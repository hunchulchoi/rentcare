import { apiError, requireCarAuth } from '#lib/server/api.ts';
import { RENTAL_TOKEN_HEADER, readIncidentToken } from '#lib/server/incident-token.ts';
import { releaseRental } from '#lib/server/rentals.ts';
import type { RequestHandler } from './$types';

// Rental end (docs/api.md v11): the rental code stops working.
export const POST: RequestHandler = async ({ request }) => {
	const denied = requireCarAuth(request);
	if (denied) return denied;
	const token = readIncidentToken(request, RENTAL_TOKEN_HEADER);
	if (!token || !(await releaseRental(token))) return apiError(404, 'not_found', 'Rental not found');
	return new Response(null, { status: 204, headers: { 'cache-control': 'no-store' } });
};
