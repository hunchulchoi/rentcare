import { fail } from '@sveltejs/kit';
import { normalizeAttachCode } from '#lib/attach-code.ts';
import { MAX_FILES_PER_INCIDENT } from '#lib/files.ts';
import { isUiLang, pickUiLang } from '#lib/i18n.ts';
import { findByAttachCode } from '#lib/server/incidents.ts';
import { attachCodeLimiter } from '#lib/server/rate-limit.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = ({ url, request }) => {
	return { lang: pickUiLang(url.searchParams.get('lang'), request.headers.get('accept-language')) };
};

export const actions: Actions = {
	verify: async ({ request, getClientAddress }) => {
		const ip = getClientAddress();
		if (attachCodeLimiter.isBlocked(ip)) return fail(429, { error: 'rate_limited' as const });

		const form = await request.formData();
		const code = normalizeAttachCode(form.get('code'));
		if (!code) {
			attachCodeLimiter.recordFailure(ip);
			return fail(400, { error: 'code_invalid' as const });
		}
		const r = await findByAttachCode(code);
		if (!r.ok) {
			attachCodeLimiter.recordFailure(ip);
			return fail(400, { error: r.error });
		}
		const i = r.incident;
		return {
			verified: true as const,
			receiptNo: i.receiptNo,
			type: i.type,
			language: isUiLang(i.language) ? i.language : null,
			attachments: i.attachments,
			maxFiles: MAX_FILES_PER_INCIDENT
		};
	}
};
