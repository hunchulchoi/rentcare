import { error } from '@sveltejs/kit';
import { parseTile } from '../map-tiles.ts';
import { config } from './config';
import { TileProxy } from './tile-proxy.ts';

const proxy = new TileProxy({
	fetcher: (url, init) => fetch(url, init),
	now: Date.now,
	ttlMs: 7 * 24 * 3600 * 1000,
	maxEntries: 2000, // ~20-40 MB of PNG tiles
	urlTemplate: config.mapTileUrl,
	userAgent: `RentCare/0.1 (+${config.publicBaseUrl ?? 'https://github.com/rentcare'})`
});

/** PNG tile response for validated route params; 404 for anything else. Callers check auth first. */
export async function tileResponse(params: { z: string; x: string; y: string }): Promise<Response> {
	const t = parseTile(params.z, params.x, params.y);
	if (!t) error(404, 'Not found');
	const body = await proxy.get(t);
	if (!body) error(502, 'Map tile unavailable');
	return new Response(body as BodyInit, {
		headers: { 'content-type': 'image/png', 'content-length': String(body.byteLength) }
	});
}
