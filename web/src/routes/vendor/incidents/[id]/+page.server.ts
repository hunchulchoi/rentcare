import { error, fail } from '@sveltejs/kit';
import { acknowledge, getIncidentDetail } from '#lib/server/vendor.ts';
import { isUuid } from '#lib/server/validate.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals, params }) => {
	const vendor = locals.vendor!;
	if (!isUuid(params.id)) error(404, 'Not found');
	const detail = await getIncidentDetail(vendor.vendorId, params.id);
	if (!detail) error(404, 'Not found');
	return detail;
};

export const actions: Actions = {
	acknowledge: async ({ locals, params }) => {
		const vendor = locals.vendor!;
		if (!isUuid(params.id)) error(404, 'Not found');
		const ok = await acknowledge(vendor.vendorId, params.id, vendor.staffLabel ?? '업체 직원');
		if (!ok) error(404, 'Not found');
		return { acknowledged: true };
	}
};
