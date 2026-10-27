import { requireRental } from '#lib/server/api.ts';
import { tileResponse } from '#lib/server/map.ts';
import type { RequestHandler } from './$types';

// Map tile for the car's location view (docs/api.md v13). The car's rental token is required.
export const GET: RequestHandler = async ({ request, params }) => {
	const auth = await requireRental(request);
	if ('denied' in auth) return auth.denied;
	return tileResponse(params);
};
