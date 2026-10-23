// Web Mercator (OSM slippy map) tiles, 256 px. Same math as car/core MapTiles.kt.

export const TILE = 256;
/** Zoom levels the tile proxy serves: street level only, so it is not a general map mirror. */
export const MIN_ZOOM = 12;
export const MAX_ZOOM = 18;
const MAX_LAT = 85.05112878;

export type Located = { tileX: number; tileY: number; pixelX: number; pixelY: number };
export type Placed = { z: number; x: number; y: number; left: number; top: number };

export function locate(lat: number, lon: number, zoom: number): Located {
	const n = 2 ** zoom;
	const la = Math.max(-MAX_LAT, Math.min(MAX_LAT, lat));
	const x = ((lon + 180) / 360) * n;
	const r = (la * Math.PI) / 180;
	const y = ((1 - Math.asinh(Math.tan(r)) / Math.PI) / 2) * n;
	const tileX = Math.min(n - 1, Math.max(0, Math.floor(x)));
	const tileY = Math.min(n - 1, Math.max(0, Math.floor(y)));
	return { tileX, tileY, pixelX: (x - tileX) * TILE, pixelY: (y - tileY) * TILE };
}

/** Tiles covering a width x height view centered on the point. */
export function grid(lat: number, lon: number, zoom: number, width: number, height: number): Placed[] {
	const p = locate(lat, lon, zoom);
	const n = 2 ** zoom;
	const baseLeft = width / 2 - p.pixelX;
	const baseTop = height / 2 - p.pixelY;
	const out: Placed[] = [];
	for (let dy = -Math.ceil(baseTop / TILE); dy <= Math.ceil((height - baseTop - TILE) / TILE); dy++) {
		for (let dx = -Math.ceil(baseLeft / TILE); dx <= Math.ceil((width - baseLeft - TILE) / TILE); dx++) {
			const x = p.tileX + dx;
			const y = p.tileY + dy;
			if (x >= 0 && x < n && y >= 0 && y < n) out.push({ z: zoom, x, y, left: baseLeft + dx * TILE, top: baseTop + dy * TILE });
		}
	}
	return out;
}

const INT = /^(0|[1-9][0-9]{0,6})$/;

/** Validate route params z/x/y(.png). Returns null for anything we will not proxy. */
export function parseTile(z: string, x: string, y: string): { z: number; x: number; y: number } | null {
	const yy = y.endsWith('.png') ? y.slice(0, -4) : y;
	if (!INT.test(z) || !INT.test(x) || !INT.test(yy)) return null;
	const zi = Number(z), xi = Number(x), yi = Number(yy);
	if (zi < MIN_ZOOM || zi > MAX_ZOOM) return null;
	const n = 2 ** zi;
	if (xi >= n || yi >= n) return null;
	return { z: zi, x: xi, y: yi };
}
