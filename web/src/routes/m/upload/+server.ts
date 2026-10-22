import { json } from '@sveltejs/kit';
import { normalizeAttachCode } from '#lib/attach-code.ts';
import { apiError } from '#lib/server/api.ts';
import { addAttachment, findByAttachCode } from '#lib/server/incidents.ts';
import { attachCodeLimiter } from '#lib/server/rate-limit.ts';
import { readSingleFile } from '#lib/server/upload.ts';
import type { RequestHandler } from './$types';

// Mobile upload: multipart with fields `code` (attach code, in the body — never the URL) and `file`.
export const POST: RequestHandler = async ({ request, getClientAddress }) => {
	const ip = getClientAddress();
	if (attachCodeLimiter.isBlocked(ip)) return apiError(429, 'rate_limited', 'Too many attempts');

	const parsed = await readSingleFile(request);
	if (!parsed.ok) return apiError(parsed.status, parsed.error, parsed.message);

	const code = normalizeAttachCode(parsed.form.get('code'));
	if (!code) {
		attachCodeLimiter.recordFailure(ip);
		return apiError(400, 'code_invalid', 'Invalid attach code');
	}
	const found = await findByAttachCode(code);
	if (!found.ok) {
		attachCodeLimiter.recordFailure(ip);
		return apiError(400, found.error, found.error === 'code_expired' ? 'Attach code expired' : 'Invalid attach code');
	}

	const r = await addAttachment(found.incident.id, parsed.file, 'MOBILE');
	if (!r.ok) return apiError(r.status, r.error, r.message);
	return json({ attachmentId: r.attachmentId, attachments: r.attachments }, { status: 201 });
};
