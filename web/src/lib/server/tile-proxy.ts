// Map tile proxy: the car and vendor pages load tiles from our server only. We fetch from the tile
// provider with an identifying User-Agent and keep tiles in memory (OSM tile usage policy: cache, no bulk).

export type TileKey = { z: number; x: number; y: number };

type Options = {
	fetcher: (url: string, init: RequestInit) => Promise<Response>;
	now: () => number;
	ttlMs: number;
	maxEntries: number;
	urlTemplate: string;
	userAgent: string;
};

const MAX_TILE_BYTES = 1_000_000;

export class TileProxy {
	private cache = new Map<string, { body: Uint8Array; expires: number }>();

	constructor(private readonly o: Options) {}

	async get(t: TileKey): Promise<Uint8Array | null> {
		const key = `${t.z}/${t.x}/${t.y}`;
		const hit = this.cache.get(key);
		if (hit && hit.expires > this.o.now()) {
			this.cache.delete(key); // refresh LRU position
			this.cache.set(key, hit);
			return hit.body;
		}
		this.cache.delete(key);

		const url = this.o.urlTemplate.replace('{z}', String(t.z)).replace('{x}', String(t.x)).replace('{y}', String(t.y));
		let res: Response;
		try {
			res = await this.o.fetcher(url, { headers: { 'user-agent': this.o.userAgent }, signal: AbortSignal.timeout(8000) });
		} catch {
			return null;
		}
		if (!res.ok || !(res.headers.get('content-type') ?? '').startsWith('image/png')) return null;
		const body = new Uint8Array(await res.arrayBuffer());
		if (body.byteLength > MAX_TILE_BYTES) return null;

		this.cache.set(key, { body, expires: this.o.now() + this.o.ttlMs });
		while (this.cache.size > this.o.maxEntries) this.cache.delete(this.cache.keys().next().value!);
		return body;
	}
}
