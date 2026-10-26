import { describe, expect, it } from 'vitest';
import { mayPhoneSetLocation, parsePhoneLocation } from './phone-location.ts';

const form = (o: Record<string, string | undefined>) => {
	const f = new FormData();
	for (const [k, v] of Object.entries(o)) if (v !== undefined) f.set(k, v);
	return f;
};

describe('phone location fallback', () => {
	it('fills a missing location or replaces an earlier phone one, never the car GPS or a manual pick', () => {
		expect(mayPhoneSetLocation(null)).toBe(true);
		expect(mayPhoneSetLocation('MOBILE')).toBe(true);
		expect(mayPhoneSetLocation('VEHICLE')).toBe(false);
		expect(mayPhoneSetLocation('MANUAL')).toBe(false);
	});

	it('accepts sane coordinates with an accuracy up to 5 km', () => {
		expect(parsePhoneLocation(form({ lat: '33.5066', lon: '126.4929', accuracy: '35' }))).toEqual({
			ok: true, value: { latitude: 33.5066, longitude: 126.4929, accuracyM: 35 }
		});
		for (const bad of [
			{ lat: '91', lon: '0', accuracy: '10' },
			{ lat: '0', lon: '181', accuracy: '10' },
			{ lat: 'x', lon: '1', accuracy: '10' },
			{ lat: '33.5', lon: '126.5', accuracy: '9000' },
			{ lat: '33.5', lon: '126.5' }
		]) expect(parsePhoneLocation(form(bad)).ok, JSON.stringify(bad)).toBe(false);
	});
});
