import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = ({ locals }) => {
	return { vendor: locals.vendor ? { name: locals.vendor.vendorName, staff: locals.vendor.staffLabel } : null };
};
