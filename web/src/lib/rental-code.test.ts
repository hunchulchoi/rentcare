import { describe, expect, it } from 'vitest';
import {
	RENTAL_CODE_LENGTH,
	generateRentalCode,
	normalizeRentalCode,
	parsePhoneReport,
	rentalCodeExpiry,
	PHONE_REPORTS_PER_RENTAL
} from './rental-code.ts';

const H = 3600_000;

describe('rental code', () => {
	it('is 8 chars from the attach-code alphabet', () => {
		expect(RENTAL_CODE_LENGTH).toBe(8);
		for (let i = 0; i < 200; i++) expect(generateRentalCode()).toMatch(/^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$/);
	});

	it('normalizes typing and rejects 6-char attach codes', () => {
		expect(normalizeRentalCode(' k7q2-m9ab ')).toBe('K7Q2M9AB');
		expect(normalizeRentalCode('K7Q2M9')).toBeNull();
		expect(normalizeRentalCode('K7Q2M9A0')).toBeNull();
		expect(normalizeRentalCode(null)).toBeNull();
	});
});

describe('rentalCodeExpiry', () => {
	const now = new Date('2026-10-26T00:00:00Z');
	it('is the return time + 24 h', () => {
		expect(rentalCodeExpiry(now, new Date(now.getTime() + 48 * H)).getTime()).toBe(now.getTime() + 72 * H);
	});
	it('is at least 24 h from now (no or past return time)', () => {
		expect(rentalCodeExpiry(now, null).getTime()).toBe(now.getTime() + 24 * H);
		expect(rentalCodeExpiry(now, new Date(now.getTime() - 5 * H)).getTime()).toBe(now.getTime() + 24 * H);
	});
	it('is capped at 31 days', () => {
		expect(rentalCodeExpiry(now, new Date(now.getTime() + 90 * 24 * H)).getTime()).toBe(now.getTime() + 31 * 24 * H);
	});
});

function form(o: Record<string, string>) {
	const f = new FormData();
	for (const [k, v] of Object.entries(o)) f.set(k, v);
	return f;
}

describe('parsePhoneReport', () => {
	const base = { code: 'k7q2m9ab', type: 'NO_START', consent: 'yes', key: '7c3a1f5e-2b4d-4e8f-9a1b-0c2d3e4f5a6b' };

	it('accepts a minimal report without note or location', () => {
		const r = parsePhoneReport(form(base));
		expect(r).toEqual({
			ok: true,
			value: { code: 'K7Q2M9AB', type: 'NO_START', note: null, location: null, key: base.key }
		});
	});

	it('keeps the note and a phone location', () => {
		const r = parsePhoneReport(form({ ...base, note: ' 연기 남 ', lat: '37.39', lon: '127.11', accuracy: '30' }));
		expect(r.ok && r.value.note).toBe('연기 남');
		expect(r.ok && r.value.location).toEqual({ latitude: 37.39, longitude: 127.11, accuracyM: 30 });
	});

	it('requires consent, a known type and a valid code', () => {
		expect(parsePhoneReport(form({ ...base, consent: '' }))).toEqual({ ok: false, error: 'consent_required' });
		expect(parsePhoneReport(form({ ...base, type: 'FIRE' }))).toEqual({ ok: false, error: 'type_required' });
		expect(parsePhoneReport(form({ ...base, code: 'ABC' }))).toEqual({ ok: false, error: 'code_invalid' });
	});

	it('rejects a too long note or a broken location, and a missing key', () => {
		expect(parsePhoneReport(form({ ...base, note: 'x'.repeat(2001) }))).toEqual({ ok: false, error: 'note_too_long' });
		expect(parsePhoneReport(form({ ...base, lat: '200', lon: '0', accuracy: '5' }))).toEqual({ ok: false, error: 'location_invalid' });
		expect(parsePhoneReport(form({ ...base, key: 'nope' }))).toEqual({ ok: false, error: 'invalid_request' });
	});

	it('limits phone reports per rental', () => {
		expect(PHONE_REPORTS_PER_RENTAL).toBe(5);
	});
});
