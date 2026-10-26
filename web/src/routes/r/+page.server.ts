import { fail } from '@sveltejs/kit';
import { INCIDENT_TYPES, pickUiLang } from '#lib/i18n.ts';
import { normalizeRentalCode, parsePhoneReport } from '#lib/rental-code.ts';
import { rentalCodeLimiter } from '#lib/server/rate-limit.ts';
import { createPhoneReport, findByRentalCode } from '#lib/server/rentals.ts';
import type { Actions, PageServerLoad } from './$types';

// Phone basic report (RENTCARE-13, docs/api.md v11): the rental code is typed, never in the URL.
export const load: PageServerLoad = ({ url, request }) => ({
	lang: pickUiLang(url.searchParams.get('lang'), request.headers.get('accept-language')),
	types: INCIDENT_TYPES,
	// Idempotency key for this page view: a double tap or a resend files one report.
	key: crypto.randomUUID()
});

function summary(r: { vehiclePlate: string; vehicleModel: string | null; vendorName: string; demo: boolean }) {
	return { plate: r.vehiclePlate, model: r.vehicleModel, vendorName: r.vendorName, demo: r.demo };
}

export const actions: Actions = {
	verify: async ({ request, getClientAddress }) => {
		const ip = getClientAddress();
		if (rentalCodeLimiter.isBlocked(ip)) return fail(429, { step: 'verify' as const, error: 'rate_limited' });
		const code = normalizeRentalCode((await request.formData()).get('code'));
		const r = code ? await findByRentalCode(code) : ({ ok: false, error: 'code_invalid' } as const);
		if (!r.ok) {
			rentalCodeLimiter.recordFailure(ip);
			return fail(400, { step: 'verify' as const, error: r.error });
		}
		return { step: 'verified' as const, code, rental: summary(r.rental) };
	},

	report: async ({ request, url, getClientAddress }) => {
		const ip = getClientAddress();
		if (rentalCodeLimiter.isBlocked(ip)) return fail(429, { step: 'report' as const, error: 'rate_limited' });
		const f = await request.formData();
		const l = f.get('lang');
		const lang = pickUiLang(typeof l === 'string' ? l : url.searchParams.get('lang'), request.headers.get('accept-language'));
		const p = parsePhoneReport(f);
		if (!p.ok) return fail(400, { step: 'report' as const, error: p.error });
		const res = await createPhoneReport(p.value, lang);
		if (!res.ok) {
			if (res.error === 'code_invalid' || res.error === 'code_expired') rentalCodeLimiter.recordFailure(ip);
			return fail(res.error === 'server_error' ? 500 : 400, { step: 'report' as const, error: res.error });
		}
		const i = res.incident;
		return { step: 'done' as const, receiptNo: i.receiptNo, attachCode: i.attachCode, attachCodeExpiresAt: i.attachCodeExpiresAt };
	}
};
