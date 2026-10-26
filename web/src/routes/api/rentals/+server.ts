import { json } from '@sveltejs/kit';
import { reportUrl } from '#lib/server/attach-url.ts';
import { apiError, requireCarAuth } from '#lib/server/api.ts';
import { config } from '#lib/server/config.ts';
import { registerRental } from '#lib/server/rentals.ts';
import { parseIdempotencyKey, validateRentalRegistration } from '#lib/server/validate.ts';
import type { RequestHandler } from './$types';

const MAX_JSON_BYTES = 16 * 1024;

// Rental saved on the car (docs/api.md v11): returns the rental code for the phone basic report.
export const POST: RequestHandler = async ({ request, url }) => {
	const denied = requireCarAuth(request);
	if (denied) return denied;
	const key = parseIdempotencyKey(request.headers.get('idempotency-key'));
	if (!key) return apiError(400, 'invalid_request', 'Idempotency-Key header (UUID) is required');
	if (!/^application\/json\s*(;|$)/i.test(request.headers.get('content-type') ?? '')) {
		return apiError(400, 'invalid_request', 'Content-Type must be application/json');
	}
	const text = await request.text();
	if (text.length > MAX_JSON_BYTES) return apiError(413, 'too_large', 'Request body too large');
	let body: unknown;
	try {
		body = JSON.parse(text);
	} catch {
		return apiError(400, 'invalid_request', 'Body is not valid JSON');
	}
	const v = validateRentalRegistration(body);
	if (!v.ok) return apiError(400, 'invalid_request', 'Invalid rental body', { details: v.errors });

	const r = await registerRental(v.value, key);
	if (r.kind === 'unknown_vendor') return apiError(400, 'invalid_request', 'Unknown vendorId');
	if (r.kind === 'key_conflict') return apiError(409, 'invalid_request', 'Idempotency-Key was already used for a different vendor');
	const base = config.publicBaseUrl ?? url.origin;
	return json(
		{ rentalCode: r.rentalCode, rentalCodeExpiresAt: r.rentalCodeExpiresAt, reportUrl: reportUrl(base, v.value.language), rentalToken: r.rentalToken },
		{ status: r.kind === 'created' ? 201 : 200, headers: { 'cache-control': 'no-store' } }
	);
};
