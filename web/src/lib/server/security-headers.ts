const under = (path: string, base: string) => path === base || path.startsWith(base + '/');

/**
 * Response headers for every page and API.
 * referrer-policy must not be no-referrer: browsers then send `Origin: null` on form POSTs and
 * SvelteKit's CSRF check rejects same-site logins ("Cross-site POST form submissions are forbidden").
 * same-origin still sends no referrer to other sites (no codes or tokens are in URLs anyway).
 */
export function securityHeaders(pathname: string): Record<string, string> {
	const h: Record<string, string> = {
		'x-content-type-options': 'nosniff',
		'referrer-policy': 'same-origin',
		'x-frame-options': 'DENY'
	};
	if (under(pathname, '/vendor') || under(pathname, '/m') || under(pathname, '/api')) h['cache-control'] = 'no-store';
	return h;
}
