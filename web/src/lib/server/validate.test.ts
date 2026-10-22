import { describe, expect, it } from 'vitest';
import { parseIdempotencyKey, validateIncidentInput } from './validate';

function sample(): Record<string, any> {
	return {
		vendorId: 'demo-jeju',
		type: 'NO_START',
		language: 'en',
		note: 'free text',
		occurredAt: '2026-10-22T03:10:00Z',
		demo: true,
		rental: {
			rentalId: 'R-1001',
			vehiclePlate: '12하3456',
			vehicleModel: 'IONIQ 5',
			powertrain: 'EV',
			returnPlace: '제주공항 렌터카하우스',
			returnAt: '2026-10-23T09:00:00Z'
		},
		vehicle: { fuelPercent: null, soc: 42, readAt: '2026-10-22T03:09:40Z' },
		location: {
			latitude: 33.5066,
			longitude: 126.4929,
			address: '제주특별자치도 제주시 ...',
			readAt: '2026-10-22T03:09:50Z',
			source: 'VEHICLE',
			edited: false
		}
	};
}

function errorsOf(body: unknown): string[] {
	const r = validateIncidentInput(body);
	return r.ok ? [] : r.errors;
}

describe('validateIncidentInput', () => {
	it('accepts the contract example', () => {
		const r = validateIncidentInput(sample());
		expect(r.ok).toBe(true);
		if (!r.ok) return;
		expect(r.value.type).toBe('NO_START');
		expect(r.value.occurredAt.toISOString()).toBe('2026-10-22T03:10:00.000Z');
		expect(r.value.vehicle?.soc).toBe(42);
		expect(r.value.vehicle?.fuelPercent).toBeNull();
		expect(r.value.location?.source).toBe('VEHICLE');
		expect(r.value.rental.returnAt?.toISOString()).toBe('2026-10-23T09:00:00.000Z');
	});

	it('accepts null vehicle/location and omitted optional fields', () => {
		const b = sample();
		b.vehicle = null;
		b.location = null;
		delete b.note;
		delete b.demo;
		delete b.rental.vehicleModel;
		const r = validateIncidentInput(b);
		expect(r.ok).toBe(true);
		if (!r.ok) return;
		expect(r.value.vehicle).toBeNull();
		expect(r.value.location).toBeNull();
		expect(r.value.note).toBeNull();
		expect(r.value.demo).toBe(false);
		expect(r.value.rental.vehicleModel).toBeNull();
	});

	it('keeps the note as-is but treats blank notes as no note', () => {
		const b = sample();
		b.note = '  Engine light\nthen stopped  ';
		const r = validateIncidentInput(b);
		expect(r.ok && r.value.note).toBe('  Engine light\nthen stopped  ');
		b.note = '   ';
		const r2 = validateIncidentInput(b);
		expect(r2.ok && r2.value.note).toBeNull();
	});

	it('rejects non-objects', () => {
		expect(errorsOf(null)).not.toHaveLength(0);
		expect(errorsOf([])).not.toHaveLength(0);
		expect(errorsOf('x')).not.toHaveLength(0);
	});

	it('rejects unknown fields at every level', () => {
		const b = sample();
		b.extra = 1;
		b.rental.color = 'red';
		b.location.accuracy = 5;
		const errs = errorsOf(b);
		expect(errs).toContain('$.extra: unknown field');
		expect(errs).toContain('$.rental.color: unknown field');
		expect(errs).toContain('$.location.accuracy: unknown field');
	});

	it('rejects bad enums', () => {
		const b = sample();
		b.type = 'FIRE';
		b.language = 'fr';
		b.location.source = 'GPS';
		const errs = errorsOf(b);
		expect(errs.some((e) => e.startsWith('$.type'))).toBe(true);
		expect(errs.some((e) => e.startsWith('$.language'))).toBe(true);
		expect(errs.some((e) => e.startsWith('$.location.source'))).toBe(true);
	});

	it('requires core fields', () => {
		for (const k of ['vendorId', 'type', 'language', 'occurredAt', 'rental']) {
			const b = sample();
			delete b[k];
			expect(errorsOf(b).length, k).toBeGreaterThan(0);
		}
		const b = sample();
		delete b.rental.rentalId;
		expect(errorsOf(b)).toContain('$.rental.rentalId: is required');
		const c = sample();
		delete c.location.edited;
		expect(errorsOf(c).some((e) => e.startsWith('$.location.edited'))).toBe(true);
	});

	it('limits note length to 2000 code points', () => {
		const b = sample();
		b.note = '가'.repeat(2000);
		expect(validateIncidentInput(b).ok).toBe(true);
		b.note = '😀'.repeat(2000); // 4000 UTF-16 units, 2000 code points
		expect(validateIncidentInput(b).ok).toBe(true);
		b.note = 'a'.repeat(2001);
		expect(errorsOf(b)).toContain('$.note: must be at most 2000 characters');
	});

	it('rejects control characters (incl. NUL) but allows newlines and tabs', () => {
		const b = sample();
		b.note = 'a\tb\nc\r\n';
		expect(validateIncidentInput(b).ok).toBe(true);
		b.note = 'a\u0000b';
		expect(errorsOf(b)).toContain('$.note: contains control characters');
	});

	it('requires UTC ISO timestamps and real dates', () => {
		for (const bad of ['2026-10-22 03:10:00', '2026-10-22T03:10:00+09:00', '2026-02-31T00:00:00Z', 1700000000, '']) {
			const b = sample();
			b.occurredAt = bad;
			expect(errorsOf(b).length, String(bad)).toBeGreaterThan(0);
		}
		const b = sample();
		b.occurredAt = '2026-10-22T03:10:00.123Z';
		expect(validateIncidentInput(b).ok).toBe(true);
	});

	it('checks numeric ranges and types', () => {
		const b = sample();
		b.vehicle.soc = 101;
		b.location.latitude = 91;
		b.location.longitude = '126.4';
		const errs = errorsOf(b);
		expect(errs.some((e) => e.startsWith('$.vehicle.soc'))).toBe(true);
		expect(errs.some((e) => e.startsWith('$.location.latitude'))).toBe(true);
		expect(errs.some((e) => e.startsWith('$.location.longitude'))).toBe(true);
	});

	it('rejects wrong types for booleans and strings', () => {
		const b = sample();
		b.demo = 'true';
		b.rental.vehiclePlate = 1234;
		b.vendorId = 'Demo Jeju';
		const errs = errorsOf(b);
		expect(errs).toContain('$.demo: must be a boolean');
		expect(errs).toContain('$.rental.vehiclePlate: must be a string');
		expect(errs).toContain('$.vendorId: invalid format');
	});
});

describe('parseIdempotencyKey', () => {
	it('accepts UUIDs and lower-cases them', () => {
		expect(parseIdempotencyKey('6F1C2B3A-1111-4222-8333-444455556666')).toBe('6f1c2b3a-1111-4222-8333-444455556666');
	});
	it('rejects missing or malformed keys', () => {
		expect(parseIdempotencyKey(null)).toBeNull();
		expect(parseIdempotencyKey('')).toBeNull();
		expect(parseIdempotencyKey('abc')).toBeNull();
		expect(parseIdempotencyKey('6f1c2b3a11114222833344445555666')).toBeNull();
	});
});
