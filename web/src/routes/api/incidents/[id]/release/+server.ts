import { apiError } from '#lib/server/api.ts';
import { carMayAccess, releaseIncident } from '#lib/server/incidents.ts';
import { readIncidentToken } from '#lib/server/incident-token.ts';
import { isUuid } from '#lib/server/validate.ts';
import type { RequestHandler } from './$types';

// Rental end (docs/api.md v9): the car gives up its access; attach code and car token stop working.
export const POST: RequestHandler = async ({ request, params }) => {
	if (!isUuid(params.id) || !(await carMayAccess(params.id, readIncidentToken(request)))) {
		return apiError(404, 'not_found', 'Incident not found');
	}
	await releaseIncident(params.id);
	return new Response(null, { status: 204, headers: { 'cache-control': 'no-store' } });
};
