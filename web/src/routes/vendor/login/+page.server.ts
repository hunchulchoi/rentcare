import { fail, redirect } from '@sveltejs/kit';
import { dev } from '$app/env';
import { loginLimiter } from '#lib/server/rate-limit.ts';
import { login, setSessionCookie } from '#lib/server/vendor.ts';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = ({ locals }) => {
	if (locals.vendor) redirect(303, '/vendor');
};

export const actions: Actions = {
	default: async ({ request, cookies, getClientAddress }) => {
		const ip = getClientAddress();
		if (loginLimiter.isBlocked(ip)) return fail(429, { message: '시도 횟수가 너무 많습니다. 잠시 후 다시 시도하세요.' });

		const form = await request.formData();
		const token = String(form.get('token') ?? '').trim();
		const staff = String(form.get('staff') ?? '').trim().slice(0, 40) || null;
		if (!token || token.length > 256) {
			loginLimiter.recordFailure(ip);
			return fail(400, { message: '토큰을 입력하세요.' });
		}

		const session = await login(token, staff);
		if (!session) {
			loginLimiter.recordFailure(ip);
			return fail(401, { message: '토큰이 올바르지 않습니다.' });
		}
		setSessionCookie(cookies, session.sessionToken, session.expiresAt, !dev);
		redirect(303, '/vendor');
	}
};
