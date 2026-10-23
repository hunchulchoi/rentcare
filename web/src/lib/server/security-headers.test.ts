import { describe, expect, it } from 'vitest';
import { securityHeaders } from './security-headers.ts';

describe('securityHeaders', () => {
	it('keeps the Origin header on same-site form POSTs (no-referrer makes browsers send Origin: null → CSRF 403)', () => {
		const h = securityHeaders('/vendor/login');
		expect(h['referrer-policy']).toBe('same-origin');
	});

	it('blocks framing and sniffing everywhere', () => {
		expect(securityHeaders('/')).toMatchObject({ 'x-frame-options': 'DENY', 'x-content-type-options': 'nosniff' });
	});

	it('disables caching only for private areas', () => {
		for (const p of ['/vendor', '/vendor/incidents/x', '/m', '/m/upload', '/api/incidents']) {
			expect(securityHeaders(p)['cache-control'], p).toBe('no-store');
		}
		for (const p of ['/', '/health-check', '/mobile', '/vendors']) {
			expect(securityHeaders(p)['cache-control'], p).toBeUndefined();
		}
	});
});
