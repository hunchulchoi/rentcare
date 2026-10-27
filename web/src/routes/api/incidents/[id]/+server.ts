import { json } from '@sveltejs/kit';
import { apiError } from '#lib/server/api.ts';
import { getIncidentStatus, carMayAccess } from '#lib/server/incidents.ts';
import { readIncidentToken } from '#lib/server/incident-token.ts';
import { isUuid } from '#lib/server/validate.ts';
import type { RequestHandler } from './$types';

export const GET: RequestHandler = async ({ request, params }) => {
	if (!isUuid(params.id)) return apiError(404, 'not_found', 'Incident not found');
	// Same 404 for "no such incident" and "not your incident": do not reveal existence.
	if (!(await carMayAccess(params.id, readIncidentToken(request)))) {
		return apiError(404, 'not_found', 'Incident not found');
	}
	const incident = await getIncidentStatus(params.id);
	if (!incident) return apiError(404, 'not_found', 'Incident not found');
	return json(incident, { headers: { 'cache-control': 'no-store' } });
};
