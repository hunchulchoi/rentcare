import { describe, expect, it } from 'vitest';
import { contrast, STATUS_COLORS } from './status-colors.ts';

describe('status badge colors', () => {
	it('match WCAG contrast examples', () => {
		expect(contrast('#ffffff', '#000000')).toBeCloseTo(21, 1);
		expect(contrast('#777777', '#ffffff')).toBeCloseTo(4.48, 2);
	});

	it('every status has a distinct color with text contrast >= 4.5:1 (Pleos)', () => {
		const bgs = new Set<string>();
		for (const [status, c] of Object.entries(STATUS_COLORS)) {
			expect(contrast(c.fg, c.bg), status).toBeGreaterThanOrEqual(4.5);
			bgs.add(c.bg);
		}
		expect(bgs.size).toBe(Object.keys(STATUS_COLORS).length);
		expect(Object.keys(STATUS_COLORS).sort()).toEqual(['ACKNOWLEDGED', 'RECEIVED']);
	});
});
