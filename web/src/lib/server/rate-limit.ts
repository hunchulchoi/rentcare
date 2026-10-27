// Tiny in-memory fixed-window limiter for failed attempts (per process; fine for a single node).
import { DEMO_REGISTRATIONS_PER_HOUR } from '../car-auth.ts';

export class FailureLimiter {
	private hits = new Map<string, { count: number; resetAt: number }>();

	constructor(
		private readonly max: number,
		private readonly windowMs: number,
		private readonly now: () => number = Date.now
	) {}

	/** True if this key has used up its failures for the current window. */
	isBlocked(key: string): boolean {
		const e = this.hits.get(key);
		if (!e) return false;
		if (e.resetAt <= this.now()) {
			this.hits.delete(key);
			return false;
		}
		return e.count >= this.max;
	}

	recordFailure(key: string): void {
		const t = this.now();
		const e = this.hits.get(key);
		if (!e || e.resetAt <= t) this.hits.set(key, { count: 1, resetAt: t + this.windowMs });
		else e.count++;
		if (this.hits.size > 10_000) this.sweep();
	}

	private sweep(): void {
		const t = this.now();
		for (const [k, e] of this.hits) if (e.resetAt <= t) this.hits.delete(k);
	}
}

/** Failed attach-code checks on /m (verify + upload). */
export const attachCodeLimiter = new FailureLimiter(10, 10 * 60 * 1000);
/** Failed vendor logins. */
export const loginLimiter = new FailureLimiter(10, 15 * 60 * 1000);
/** Failed rental-code checks on /r (verify + report). */
export const rentalCodeLimiter = new FailureLimiter(10, 10 * 60 * 1000);
/** Failed car enrollment codes on POST /api/rentals (v13). */
export const enrollmentCodeLimiter = new FailureLimiter(10, 10 * 60 * 1000);
/** Open demo registrations per IP (counted on every call, not only failures). */
export const demoRegistrationLimiter = new FailureLimiter(DEMO_REGISTRATIONS_PER_HOUR, 60 * 60 * 1000);
