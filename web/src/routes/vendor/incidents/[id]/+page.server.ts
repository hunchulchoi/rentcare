import { error, fail } from '@sveltejs/kit';
import { parseActionForm } from '#lib/incident-actions.ts';
import { acknowledge, addIncidentAction, getIncidentDetail, reopenIncident, resolveIncident } from '#lib/server/vendor.ts';
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
	},
	addAction: async ({ locals, params, request }) => {
		const vendor = locals.vendor!;
		if (!isUuid(params.id)) error(404, 'Not found');
		const parsed = parseActionForm(await request.formData());
		if (!parsed.ok) return fail(400, { actionError: parsed.error });
		const ok = await addIncidentAction(vendor.vendorId, params.id, parsed.value, vendor.staffLabel ?? '업체 직원');
		if (!ok) error(404, 'Not found');
		return { actionSaved: true };
	},
	resolve: async ({ locals, params }) => {
		const vendor = locals.vendor!;
		if (!isUuid(params.id)) error(404, 'Not found');
		if (!(await resolveIncident(vendor.vendorId, params.id, vendor.staffLabel ?? '업체 직원'))) error(404, 'Not found');
		return { resolved: true };
	},
	reopen: async ({ locals, params }) => {
		const vendor = locals.vendor!;
		if (!isUuid(params.id)) error(404, 'Not found');
		if (!(await reopenIncident(vendor.vendorId, params.id))) error(404, 'Not found');
		return { reopened: true };
	}
};
