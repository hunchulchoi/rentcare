import { describe, expect, it } from 'vitest';
import { FailureLimiter } from './rate-limit';

describe('FailureLimiter', () => {
	it('blocks after max failures and resets after the window', () => {
		let now = 0;
		const l = new FailureLimiter(3, 1000, () => now);
		expect(l.isBlocked('ip')).toBe(false);
		l.recordFailure('ip');
		l.recordFailure('ip');
		expect(l.isBlocked('ip')).toBe(false);
		l.recordFailure('ip');
		expect(l.isBlocked('ip')).toBe(true);
		expect(l.isBlocked('other')).toBe(false);
		now = 1000;
		expect(l.isBlocked('ip')).toBe(false);
	});
});
