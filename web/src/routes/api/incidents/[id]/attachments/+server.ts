import { json } from '@sveltejs/kit';
import { apiError, requireCarAuth } from '#lib/server/api.ts';
import { addAttachment, carMayAccess } from '#lib/server/incidents.ts';
import { readIncidentToken } from '#lib/server/incident-token.ts';
import { readSingleFile } from '#lib/server/upload.ts';
import { isUuid } from '#lib/server/validate.ts';
import type { RequestHandler } from './$types';

export const POST: RequestHandler = async ({ request, params }) => {
	const denied = requireCarAuth(request);
	if (denied) return denied;
	if (!isUuid(params.id)) return apiError(404, 'not_found', 'Incident not found');
	// Same 404 for "no such incident" and "not your incident": do not reveal existence.
	if (!(await carMayAccess(params.id, readIncidentToken(request)))) {
		return apiError(404, 'not_found', 'Incident not found');
	}

	const parsed = await readSingleFile(request);
	if (!parsed.ok) return apiError(parsed.status, parsed.error, parsed.message);

	const r = await addAttachment(params.id, parsed.file, 'CAR');
	if (!r.ok) return apiError(r.status, r.error, r.message);
	return json({ attachmentId: r.attachmentId, attachments: r.attachments }, { status: 201 });
};
