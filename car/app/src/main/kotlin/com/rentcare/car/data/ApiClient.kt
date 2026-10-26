package com.rentcare.car.data

import com.rentcare.core.ApiError
import com.rentcare.core.AttachCreated
import com.rentcare.core.IncidentCreated
import com.rentcare.core.IncidentRequest
import com.rentcare.core.IncidentStatusDto
import com.rentcare.core.RentcareJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** Server answered with an error. Retrying the same request will not help unless 5xx. */
    data class Http(val code: Int, val error: ApiError?) : ApiResult<Nothing>

    /** No answer (offline, timeout). The request may or may not have reached the server. */
    data class Network(val message: String) : ApiResult<Nothing>
}

class ApiClient(private val baseUrl: String, private val apiKey: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Same [idempotencyKey] on retry → server returns the same incident, no duplicate. */
    suspend fun createIncident(req: IncidentRequest, idempotencyKey: String): ApiResult<IncidentCreated> {
        val body = RentcareJson.encodeToString(IncidentRequest.serializer(), req).toRequestBody(jsonType)
        val request = Request.Builder()
            .url("$baseUrl/api/incidents")
            .header("Authorization", "Bearer $apiKey")
            .header("Idempotency-Key", idempotencyKey)
            .post(body)
            .build()
        return execute(request, IncidentCreated.serializer())
    }

    suspend fun status(id: String, incidentToken: String): ApiResult<IncidentStatusDto> {
        val request = Request.Builder()
            .url("$baseUrl/api/incidents/$id")
            .header("Authorization", "Bearer $apiKey")
            .header("X-Incident-Token", incidentToken)
            .get()
            .build()
        return execute(request, IncidentStatusDto.serializer())
    }

    /**
     * Car direct attach (docs/api.md): streams the file (no full copy in memory) and reports progress.
     * Multipart POST needs Origin = our base URL (SvelteKit CSRF check).
     */
    suspend fun uploadAttachment(
        id: String, incidentToken: String, fileName: String, mime: String, size: Long,
        open: () -> java.io.InputStream, onProgress: (sent: Long, total: Long) -> Unit,
    ): ApiResult<AttachCreated> {
        val part = object : okhttp3.RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: okio.BufferedSink) {
                open().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        sent += n
                        onProgress(sent, size)
                    }
                }
            }
        }
        val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", fileName, part).build()
        val request = Request.Builder()
            .url("$baseUrl/api/incidents/$id/attachments")
            .header("Authorization", "Bearer $apiKey")
            .header("X-Incident-Token", incidentToken)
            .header("Origin", baseUrl)
            .post(body)
            .build()
        return execute(request, AttachCreated.serializer())
    }

    /** Map tile PNG from our tile proxy (docs/api.md). Null when unavailable; the map just stays blank. */
    suspend fun mapTile(z: Int, x: Int, y: Int): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/map/tiles/$z/$x/$y.png")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        try {
            http.newCall(request).execute().use { res ->
                val type = res.header("content-type").orEmpty()
                if (res.isSuccessful && type.startsWith("image/png")) res.body?.bytes() else null
            }
        } catch (e: IOException) {
            null
        }
    }

    private suspend fun <T> execute(request: Request, serializer: KSerializer<T>): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { res ->
                    val text = res.body?.string().orEmpty()
                    if (res.isSuccessful) {
                        ApiResult.Ok(RentcareJson.decodeFromString(serializer, text))
                    } else {
                        val err = runCatching { RentcareJson.decodeFromString(ApiError.serializer(), text) }.getOrNull()
                        ApiResult.Http(res.code, err)
                    }
                }
            } catch (e: IOException) {
                ApiResult.Network(e.message ?: e.javaClass.simpleName)
            } catch (e: kotlinx.serialization.SerializationException) {
                ApiResult.Http(-1, ApiError("bad_response", e.message))
            }
        }
}
