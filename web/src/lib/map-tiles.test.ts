import { describe, expect, it } from 'vitest';
import { grid, locate, parseTile } from './map-tiles.ts';

describe('locate / grid (same as car core MapTiles)', () => {
	it('matches known tiles', () => {
		const p = locate(37.5665, 126.978, 16);
		expect([p.tileX, p.tileY]).toEqual([55883, 25378]);
		expect(p.pixelX).toBeCloseTo(163.7, 1);
		expect(p.pixelY).toBeCloseTo(247.3, 1);
	});

	it('centers the point and covers the view', () => {
		const g = grid(37.5665, 126.978, 16, 600, 400);
		const t = g.find((t) => t.x === 55883 && t.y === 25378)!;
		expect(t.left + 163.7).toBeCloseTo(300, 1);
		expect(t.top + 247.3).toBeCloseTo(200, 1);
		expect(Math.min(...g.map((t) => t.left))).toBeLessThanOrEqual(0);
		expect(Math.max(...g.map((t) => t.left + 256))).toBeGreaterThanOrEqual(600);
	});
});

describe('parseTile', () => {
	it('accepts valid tile paths', () => {
		expect(parseTile('16', '55883', '25378.png')).toEqual({ z: 16, x: 55883, y: 25378 });
		expect(parseTile('12', '0', '4095')).toEqual({ z: 12, x: 0, y: 4095 });
	});

	it('rejects zoom outside 12..18, tiles outside the world, non-numbers', () => {
		for (const [z, x, y] of [
			['19', '0', '0'], ['16', '65536', '0'], ['12', '4096', '0'], ['-1', '0', '0'],
			['16', '1e3', '0'], ['16', '0x10', '0'], ['16', '../1', '0'], ['16', '1', '1.jpg'], ['11', '1', '1']
		]) {
			expect(parseTile(z, x, y), `${z}/${x}/${y}`).toBeNull();
		}
	});
});
