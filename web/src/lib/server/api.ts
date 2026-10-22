import { json } from '@sveltejs/kit';
import { config } from './config';
import { safeEqual } from './crypto';

export type ApiErrorCode =
	| 'unauthorized'
	| 'invalid_request'
	| 'not_found'
	| 'too_large'
	| 'unsupported_type'
	| 'too_many_files'
	| 'code_invalid'
	| 'code_expired'
	| 'rate_limited'
	| 'server_error';

export function apiError(status: number, error: ApiErrorCode, message: string, extra?: Record<string, unknown>): Response {
	return json({ error, message, ...extra }, { status, headers: { 'cache-control': 'no-store' } });
}

let warnedMissingKey = false;

/** Returns an error Response unless the request carries `Authorization: Bearer <CAR_API_KEY>`. */
export function requireCarAuth(request: Request): Response | null {
	const expected = config.carApiKey;
	if (!expected) {
		if (!warnedMissingKey) {
			console.error('[rentcare] CAR_API_KEY is not set; rejecting all car API calls');
			warnedMissingKey = true;
		}
		return apiError(401, 'unauthorized', 'Missing or invalid API key');
	}
	const header = request.headers.get('authorization') ?? '';
	const m = /^Bearer (.+)$/.exec(header);
	if (!m || !safeEqual(m[1], expected)) return apiError(401, 'unauthorized', 'Missing or invalid API key');
	return null;
}
