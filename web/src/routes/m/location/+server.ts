import { json } from '@sveltejs/kit';
import { normalizeAttachCode } from '#lib/attach-code.ts';
import { parsePhoneLocation } from '#lib/phone-location.ts';
import { apiError } from '#lib/server/api.ts';
import { findByAttachCode, setPhoneLocation } from '#lib/server/incidents.ts';
import { attachCodeLimiter } from '#lib/server/rate-limit.ts';
import type { RequestHandler } from './$types';

// Phone location fallback (RENTCARE-10): form fields code, lat, lon, accuracy (code in the body, never the URL).
export const POST: RequestHandler = async ({ request, getClientAddress }) => {
	const ip = getClientAddress();
	if (attachCodeLimiter.isBlocked(ip)) return apiError(429, 'rate_limited', 'Too many attempts');
	let form: FormData;
	try {
		form = await request.formData();
	} catch {
		return apiError(400, 'invalid_request', 'Form data expected');
	}
	const code = normalizeAttachCode(form.get('code'));
	if (!code) {
		attachCodeLimiter.recordFailure(ip);
		return apiError(400, 'code_invalid', 'Invalid attach code');
	}
	const found = await findByAttachCode(code);
	if (!found.ok) {
		attachCodeLimiter.recordFailure(ip);
		return apiError(400, found.error, found.error === 'code_expired' ? 'Attach code expired' : 'Invalid attach code');
	}
	const loc = parsePhoneLocation(form);
	if (!loc.ok) return apiError(400, 'invalid_request', 'Invalid location');
	const result = await setPhoneLocation(found.incident.id, loc.value);
	return json({ location: result }, { headers: { 'cache-control': 'no-store' } });
};
