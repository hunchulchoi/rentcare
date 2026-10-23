import { describe, expect, it, vi } from 'vitest';
import { TileProxy } from './tile-proxy.ts';

const png = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 1, 2, 3]);
const ok = () => new Response(png, { status: 200, headers: { 'content-type': 'image/png' } });

describe('TileProxy', () => {
	it('fetches once, then serves from cache until expiry', async () => {
		let now = 0;
		const fetcher = vi.fn(async (_url: string, _init: RequestInit) => ok());
		const p = new TileProxy({ fetcher, now: () => now, ttlMs: 1000, maxEntries: 10, urlTemplate: 'https://t/{z}/{x}/{y}.png', userAgent: 'ua' });
		expect(await p.get({ z: 16, x: 1, y: 2 })).toEqual(png);
		expect(await p.get({ z: 16, x: 1, y: 2 })).toEqual(png);
		expect(fetcher).toHaveBeenCalledTimes(1);
		expect(fetcher.mock.calls[0][0]).toBe('https://t/16/1/2.png');
		expect(fetcher.mock.calls[0][1].headers).toMatchObject({ 'user-agent': 'ua' });
		now = 2000;
		await p.get({ z: 16, x: 1, y: 2 });
		expect(fetcher).toHaveBeenCalledTimes(2);
	});

	it('evicts the oldest entry beyond maxEntries', async () => {
		const fetcher = vi.fn(async (_url: string, _init: RequestInit) => ok());
		const p = new TileProxy({ fetcher, now: () => 0, ttlMs: 1e9, maxEntries: 2, urlTemplate: 'https://t/{z}/{x}/{y}.png', userAgent: 'ua' });
		await p.get({ z: 16, x: 1, y: 1 });
		await p.get({ z: 16, x: 2, y: 2 });
		await p.get({ z: 16, x: 3, y: 3 });
		await p.get({ z: 16, x: 1, y: 1 });
		expect(fetcher).toHaveBeenCalledTimes(4);
	});

	it('returns null for upstream errors, non-PNG or oversized bodies, and does not cache them', async () => {
		const bad = [
			new Response('x', { status: 503 }),
			new Response('<html>', { status: 200, headers: { 'content-type': 'text/html' } }),
			new Response(new Uint8Array(2_000_000), { status: 200, headers: { 'content-type': 'image/png' } })
		];
		const fetcher = vi.fn(async (_url: string, _init: RequestInit) => bad.shift() ?? ok());
		const p = new TileProxy({ fetcher, now: () => 0, ttlMs: 1e9, maxEntries: 10, urlTemplate: 'https://t/{z}/{x}/{y}.png', userAgent: 'ua' });
		expect(await p.get({ z: 16, x: 1, y: 1 })).toBeNull();
		expect(await p.get({ z: 16, x: 1, y: 1 })).toBeNull();
		expect(await p.get({ z: 16, x: 1, y: 1 })).toBeNull();
		expect(await p.get({ z: 16, x: 1, y: 1 })).toEqual(png);
	});
});
