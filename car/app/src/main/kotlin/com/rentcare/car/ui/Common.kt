package com.rentcare.car.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.rentcare.car.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

fun fmtTime(ms: Long?): String = ms?.let { timeFmt.format(Instant.ofEpochMilli(it)) } ?: "-"
fun fmtDateTime(ms: Long?): String = ms?.let { dateTimeFmt.format(Instant.ofEpochMilli(it)) } ?: "-"
fun parseDateTime(s: String): Long? = runCatching {
    java.time.LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

fun won(n: Int) = "%,d".format(n)

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(key, Modifier.width(220.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
    )
}

@Composable
fun NoRental() {
    Text(stringResource(R.string.no_rental))
}

/** QR for a plain URL (no tokens inside). */
@Composable
fun QrCode(text: String, size: Dp = 220.dp) {
    val bmp = remember(text) {
        val px = 512
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px)
        Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until px) for (y in 0 until px) {
                setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
    Image(bmp.asImageBitmap(), contentDescription = text, modifier = Modifier.size(size))
}
