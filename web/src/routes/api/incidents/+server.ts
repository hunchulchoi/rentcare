import { json } from '@sveltejs/kit';
import { attachUrl } from '#lib/server/attach-url.ts';
import { apiError, requireCarAuth } from '#lib/server/api.ts';
import { config } from '#lib/server/config.ts';
import { createIncident } from '#lib/server/incidents.ts';
import { parseIdempotencyKey, validateIncidentInput } from '#lib/server/validate.ts';
import type { RequestHandler } from './$types';

const MAX_JSON_BYTES = 64 * 1024;

export const POST: RequestHandler = async ({ request, url }) => {
	const denied = requireCarAuth(request);
	if (denied) return denied;

	const key = parseIdempotencyKey(request.headers.get('idempotency-key'));
	if (!key) return apiError(400, 'invalid_request', 'Idempotency-Key header (UUID) is required');

	const ct = request.headers.get('content-type') ?? '';
	if (!/^application\/json\s*(;|$)/i.test(ct)) {
		return apiError(400, 'invalid_request', 'Content-Type must be application/json');
	}
	if (Number(request.headers.get('content-length') ?? 0) > MAX_JSON_BYTES) {
		return apiError(413, 'too_large', 'Request body too large');
	}

	const text = await request.text();
	if (text.length > MAX_JSON_BYTES) return apiError(413, 'too_large', 'Request body too large');
	let body: unknown;
	try {
		body = JSON.parse(text);
	} catch {
		return apiError(400, 'invalid_request', 'Body is not valid JSON');
	}

	const v = validateIncidentInput(body);
	if (!v.ok) return apiError(400, 'invalid_request', 'Invalid incident body', { details: v.errors });

	const result = await createIncident(v.value, key);
	if (result.kind === 'unknown_vendor') return apiError(400, 'invalid_request', 'Unknown vendorId');
	if (result.kind === 'too_many_reports') return apiError(500, 'server_error', 'Unexpected result');
	if (result.kind === 'key_conflict') {
		return apiError(409, 'invalid_request', 'Idempotency-Key was already used for a different vendor');
	}

	const base = config.publicBaseUrl ?? url.origin;
	return json(
		{ ...result.incident, attachUrl: attachUrl(base, v.value.language) },
		{ status: result.kind === 'created' ? 201 : 200, headers: { 'cache-control': 'no-store' } }
	);
};
