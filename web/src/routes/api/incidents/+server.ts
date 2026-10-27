import { json } from '@sveltejs/kit';
import { attachUrl } from '#lib/server/attach-url.ts';
import { apiError, requireRental } from '#lib/server/api.ts';
import { CAR_REPORTS_PER_RENTAL, vendorScope } from '#lib/car-auth.ts';
import { config } from '#lib/server/config.ts';
import { createIncident } from '#lib/server/incidents.ts';
import { parseIdempotencyKey, validateIncidentInput } from '#lib/server/validate.ts';
import type { RequestHandler } from './$types';

const MAX_JSON_BYTES = 64 * 1024;

export const POST: RequestHandler = async ({ request, url }) => {
	// v13: the rental token decides vendor, rental and demo flag (RENTCARE-36).
	const auth = await requireRental(request);
	if ('denied' in auth) return auth.denied;
	const rental = auth.rental;

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

	if (vendorScope(v.value.vendorId, rental.vendorId) === 'forbidden') {
		return apiError(403, 'forbidden', 'vendorId does not match this rental');
	}
	const input = {
		...v.value,
		vendorId: rental.vendorId,
		demo: rental.demo,
		rental: {
			rentalId: rental.rentalId,
			vehiclePlate: rental.vehiclePlate,
			vehicleModel: rental.vehicleModel,
			powertrain: rental.powertrain,
			returnPlace: rental.returnPlace,
			returnAt: rental.returnAt
		}
	};
	const result = await createIncident(input, key, new Date(), { rentalRef: rental.id, maxReports: CAR_REPORTS_PER_RENTAL, channel: 'CAR' });
	if (result.kind === 'unknown_vendor') return apiError(400, 'invalid_request', 'Unknown vendorId');
	if (result.kind === 'too_many_reports') return apiError(429, 'rate_limited', 'Too many reports for this rental');
	if (result.kind === 'key_conflict') {
		return apiError(409, 'invalid_request', 'Idempotency-Key was already used for a different vendor');
	}

	const base = config.publicBaseUrl ?? url.origin;
	return json(
		{ ...result.incident, attachUrl: attachUrl(base, v.value.language) },
		{ status: result.kind === 'created' ? 201 : 200, headers: { 'cache-control': 'no-store' } }
	);
};
