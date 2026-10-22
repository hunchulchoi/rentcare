import type { Handle, HandleServerError } from '@sveltejs/kit/hooks';
import { SESSION_COOKIE, getSession } from '#lib/server/vendor.ts';

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
	response.headers.set('x-content-type-options', 'nosniff');
	response.headers.set('referrer-policy', 'no-referrer');
	response.headers.set('x-frame-options', 'DENY');
	if (pathname.startsWith('/vendor') || pathname === '/m' || pathname.startsWith('/m/') || pathname.startsWith('/api/')) {
		response.headers.set('cache-control', 'no-store');
	}
	return response;
};

// Log only unexpected errors, and only the error itself — never bodies, headers, cookies or query strings.
// Returning nothing keeps SvelteKit's safe defaults (500 / "Internal Error" for unknown errors).
export const handleError: HandleServerError = (input) => {
	if (input.kind !== 'unknown') return;
	const e = input.error;
	const msg = e instanceof Error ? `${e.name}: ${e.message}` : String(e);
	console.error(`[rentcare] ${input.event.request.method} ${input.event.url.pathname}: ${msg}`);
};
