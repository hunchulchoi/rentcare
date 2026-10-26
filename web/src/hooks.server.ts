import { errorId, routeForLog } from '#lib/server/log-safe.ts';
import type { Handle, HandleServerError, ServerInit } from '@sveltejs/kit/hooks';
import { scheduleRetention } from '#lib/server/retention.ts';
import { securityHeaders } from '#lib/server/security-headers.ts';
import { SESSION_COOKIE, getSession } from '#lib/server/vendor.ts';

/** Start background retention (delete reports past RETENTION_DAYS). */
export const init: ServerInit = () => {
	scheduleRetention();
};

export const handle: Handle = async ({ event, resolve }) => {
	const { pathname } = event.url;
	event.locals.vendor = null;

	if (pathname === '/vendor' || pathname.startsWith('/vendor/')) {
		const token = event.cookies.get(SESSION_COOKIE);
		event.locals.vendor = token ? await getSession(token) : null;
		if (!event.locals.vendor && pathname !== '/vendor/login') {
			return new Response(null, { status: 303, headers: { location: '/vendor/login' } });
		}
	}

	const response = await resolve(event);
	for (const [k, v] of Object.entries(securityHeaders(pathname))) response.headers.set(k, v);
	return response;
};

// Log only unexpected errors, and only the error itself — never bodies, headers, cookies or query strings.
// Returning nothing keeps SvelteKit's safe defaults (500 / "Internal Error" for unknown errors).
export const handleError: HandleServerError = (input) => {
	if (input.kind !== 'unknown') return;
	console.error(
		`[rentcare] ${input.event.request.method} ${routeForLog(input.event.url.pathname)}: ${errorId(input.error)}`
	);
};
