import { describe, expect, it } from 'vitest';
import { formatReceiptNo, isoZ, kstDayKey } from './receipt';

describe('formatReceiptNo', () => {
	it('pads the sequence to 4 digits', () => {
		expect(formatReceiptNo('20261022', 7)).toBe('RC-20261022-0007');
		expect(formatReceiptNo('20261022', 1234)).toBe('RC-20261022-1234');
	});
	it('grows past 9999 instead of truncating', () => {
		expect(formatReceiptNo('20261022', 10000)).toBe('RC-20261022-10000');
	});
	it('rejects bad input', () => {
		expect(() => formatReceiptNo('2026-10-22', 1)).toThrow();
		expect(() => formatReceiptNo('20261022', 0)).toThrow();
		expect(() => formatReceiptNo('20261022', 1.5)).toThrow();
	});
});

describe('kstDayKey', () => {
	it('uses the Korean calendar day', () => {
		expect(kstDayKey(new Date('2026-10-22T03:10:00Z'))).toBe('20261022');
		expect(kstDayKey(new Date('2026-10-22T14:59:59Z'))).toBe('20261022');
		expect(kstDayKey(new Date('2026-10-22T15:00:00Z'))).toBe('20261023'); // 00:00 KST
		expect(kstDayKey(new Date('2026-12-31T15:30:00Z'))).toBe('20270101');
	});
});

describe('isoZ', () => {
	it('drops milliseconds', () => {
		expect(isoZ(new Date('2026-10-23T03:10:00.123Z'))).toBe('2026-10-23T03:10:00Z');
	});
});
