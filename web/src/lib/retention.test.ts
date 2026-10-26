import { describe, expect, it } from 'vitest';
import { DEFAULT_RETENTION_DAYS, deleteAfter, retentionCutoff, retentionDays } from './retention.ts';

describe('retention', () => {
	it('defaults to 90 days and accepts only a sane override', () => {
		expect(DEFAULT_RETENTION_DAYS).toBe(90);
		expect(retentionDays(undefined)).toBe(90);
		expect(retentionDays('30')).toBe(30);
		for (const bad of ['0', '-1', 'abc', '3651', '1.5']) expect(retentionDays(bad), bad).toBe(90);
	});

	it('deletes a report N days after it was received', () => {
		expect(deleteAfter(new Date('2026-10-23T05:00:00Z'), 90).toISOString()).toBe('2027-01-21T05:00:00.000Z');
	});

	it('cutoff: reports received before it are expired', () => {
		const now = new Date('2027-01-21T05:00:01Z');
		const cut = retentionCutoff(now, 90);
		expect(new Date('2026-10-23T05:00:00Z') < cut).toBe(true);
		expect(new Date('2026-10-23T05:00:02Z') < cut).toBe(false);
	});
});
