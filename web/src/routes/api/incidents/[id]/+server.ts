import { json } from '@sveltejs/kit';
import { apiError, requireCarAuth } from '#lib/server/api.ts';
import { getIncidentStatus } from '#lib/server/incidents.ts';
import { isUuid } from '#lib/server/validate.ts';
import type { RequestHandler } from './$types';

export const GET: RequestHandler = async ({ request, params }) => {
	const denied = requireCarAuth(request);
	if (denied) return denied;
	if (!isUuid(params.id)) return apiError(404, 'not_found', 'Incident not found');
	const incident = await getIncidentStatus(params.id);
	if (!incident) return apiError(404, 'not_found', 'Incident not found');
	return json(incident, { headers: { 'cache-control': 'no-store' } });
};
