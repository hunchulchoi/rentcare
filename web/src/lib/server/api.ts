import { json } from '@sveltejs/kit';
import { bearerToken } from '../car-auth.ts';
import { findRentalByToken, type TokenRental } from './rentals';

export type ApiErrorCode =
	| 'unauthorized'
	| 'forbidden'
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

/**
 * The car's credential is its rental token (docs/api.md v13, RENTCARE-36): `Authorization: Bearer <rentalToken>`.
 * Returns the rental (vendor, demo flag, rental fields) or a 401 Response. No shared key exists any more.
 */
export async function requireRental(request: Request): Promise<{ rental: TokenRental } | { denied: Response }> {
	const token = bearerToken(request.headers.get('authorization'));
	const rental = token ? await findRentalByToken(token) : null;
	if (!rental) return { denied: apiError(401, 'unauthorized', 'Missing, expired or released rental token') };
	return { rental };
}
