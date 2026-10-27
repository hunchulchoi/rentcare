import { describe, expect, it } from 'vitest';
import {
	CAR_REPORTS_PER_RENTAL,
	DEMO_REGISTRATIONS_PER_HOUR,
	ENROLLMENT_TTL_MS,
	bearerToken,
	enrollmentExpiry,
	registrationAuth,
	registrationReplay,
	reportReplay,
	vendorScope
} from './car-auth.ts';

const DEMO = 'demo-jeju';
const TOKEN = 'a'.repeat(43);

describe('car credentials (RENTCARE-36, docs/api.md v13)', () => {
	it('registers the demo vendor without a code, always as demo', () => {
		expect(registrationAuth({ vendorId: DEMO, demo: true, enrollmentCode: null }, DEMO)).toEqual({ kind: 'demo' });
	});

	it('refuses a non-demo or other-vendor registration without a code', () => {
		expect(registrationAuth({ vendorId: DEMO, demo: false, enrollmentCode: null }, DEMO)).toEqual({ kind: 'denied' });
		expect(registrationAuth({ vendorId: 'jeju-real', demo: true, enrollmentCode: null }, DEMO)).toEqual({ kind: 'denied' });
	});

	it('uses the enrollment code when one is given (vendor comes from the code)', () => {
		expect(registrationAuth({ vendorId: 'jeju-real', demo: false, enrollmentCode: 'K7Q2M9AB' }, DEMO)).toEqual({
			kind: 'code',
			code: 'K7Q2M9AB'
		});
		expect(registrationAuth({ vendorId: DEMO, demo: true, enrollmentCode: 'K7Q2M9AB' }, DEMO)).toEqual({
			kind: 'code',
			code: 'K7Q2M9AB'
		});
	});

	it('reads only a well-formed bearer token', () => {
		expect(bearerToken(`Bearer ${TOKEN}`)).toBe(TOKEN);
		expect(bearerToken(`bearer ${TOKEN}`)).toBeNull();
		expect(bearerToken('Bearer change-me-car-api-key')).toBeNull();
		expect(bearerToken(null)).toBeNull();
	});

	it('rejects a body vendor that differs from the credential', () => {
		expect(vendorScope(undefined, DEMO)).toBe('ok');
		expect(vendorScope(DEMO, DEMO)).toBe('ok');
		expect(vendorScope('jeju-real', DEMO)).toBe('forbidden');
	});

	it('enrollment codes live 30 minutes', () => {
		expect(ENROLLMENT_TTL_MS).toBe(30 * 60 * 1000);
		expect(enrollmentExpiry(new Date('2026-10-27T00:00:00Z')).toISOString()).toBe('2026-10-27T00:30:00.000Z');
	});

	it('limits open demo registrations and car reports per rental', () => {
		expect(DEMO_REGISTRATIONS_PER_HOUR).toBe(60);
		expect(CAR_REPORTS_PER_RENTAL).toBe(30);
	});

	it('replays a report only for the same rental and channel (recheck P1)', () => {
		const mine = { vendorId: DEMO, rentalRef: 'A', channel: 'CAR' as const };
		expect(reportReplay(mine, { vendorId: DEMO, rentalRef: 'A', channel: 'CAR' })).toBe('ok');
		expect(reportReplay(mine, { vendorId: DEMO, rentalRef: 'B', channel: 'CAR' })).toBe('conflict');
		expect(reportReplay(mine, { vendorId: DEMO, rentalRef: 'A', channel: 'PHONE' })).toBe('conflict');
		expect(reportReplay(mine, { vendorId: 'other', rentalRef: 'A', channel: 'CAR' })).toBe('conflict');
		expect(reportReplay({ vendorId: DEMO, rentalRef: null, channel: 'CAR' }, { vendorId: DEMO, rentalRef: 'A', channel: 'CAR' })).toBe('conflict');
	});

	it('replays a registration only with the credential that made it (recheck P2)', () => {
		const demoRental = { vendorId: DEMO, demo: true, id: 'R1' };
		const realRental = { vendorId: 'jeju-real', demo: false, id: 'R2' };
		expect(registrationReplay(demoRental, { kind: 'demo' }, null, DEMO)).toBe('ok');
		expect(registrationReplay(realRental, { kind: 'demo' }, null, DEMO)).toBe('denied');
		// The code must be the one this registration consumed, not any well-formed code.
		expect(registrationReplay(realRental, { kind: 'code', code: 'K7Q2M9AB' }, 'R2', DEMO)).toBe('ok');
		expect(registrationReplay(realRental, { kind: 'code', code: 'K7Q2M9AB' }, 'R9', DEMO)).toBe('denied');
		expect(registrationReplay(realRental, { kind: 'code', code: 'K7Q2M9AB' }, null, DEMO)).toBe('denied');
	});
});
