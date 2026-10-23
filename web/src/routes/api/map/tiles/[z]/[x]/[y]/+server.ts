import { requireCarAuth } from '#lib/server/api.ts';
import { tileResponse } from '#lib/server/map.ts';
import type { RequestHandler } from './$types';

// Map tile for the car's location view (docs/api.md). Car key required.
export const GET: RequestHandler = async ({ request, params }) => {
	const denied = requireCarAuth(request);
	if (denied) return denied;
	return tileResponse(params);
};
