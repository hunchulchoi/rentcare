import { redirect } from '@sveltejs/kit';
import { LIST_LIMIT, SESSION_COOKIE, listIncidents, logout } from '#lib/server/vendor.ts';
import { issueEnrollmentCode } from '#lib/server/rentals.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ locals }) => {
	const vendor = locals.vendor!; // guaranteed by hooks.server.ts
	const { rows, total } = await listIncidents(vendor.vendorId);
	return { incidents: rows, total, limit: LIST_LIMIT };
};

export const actions: Actions = {
	/** v13: single-use code staff type into the car's rental screen (30 min). Shown once, stored hashed. */
	enroll: async ({ locals }) => {
		const { code, expiresAt } = await issueEnrollmentCode(locals.vendor!.vendorId);
		return { enrollment: { code, expiresAt: expiresAt.toISOString() } };
	},
	logout: async ({ cookies }) => {
		await logout(cookies.get(SESSION_COOKIE));
		cookies.delete(SESSION_COOKIE, { path: '/vendor' });
		redirect(303, '/vendor/login');
	}
};
