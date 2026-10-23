import { describe, expect, it } from 'vitest';
import { incidentMatches, locationText, type ListRow } from './incident-list.ts';

const row: ListRow = {
	id: 'a', receipt_no: 'RC-20261023-0092', type: 'NO_START', language: 'en', demo: true, status: 'RECEIVED',
	vehicle_plate: '12하3456', vehicle_model: 'IONIQ 5', created_at: new Date('2026-10-23T05:00:00Z'), attachments: 1,
	latitude: 37.41272, longitude: 127.09468, address: '수정구 시흥동', location_source: 'VEHICLE'
};

describe('incidentMatches (global search)', () => {
	it('matches receipt number, plate, model, address and Korean type label, case-insensitively', () => {
		for (const q of ['0092', 'rc-2026', '12하', 'ioniq', '시흥동', '시동', '']) expect(incidentMatches(row, q), q).toBe(true);
		expect(incidentMatches(row, '타이어')).toBe(false);
	});
});

describe('locationText', () => {
	it('prefers the address, then coordinates, else a clear "no location"', () => {
		expect(locationText(row)).toBe('수정구 시흥동');
		expect(locationText({ ...row, address: null })).toBe('37.41272, 127.09468');
		expect(locationText({ ...row, address: null, latitude: null, longitude: null })).toBe('위치 없음');
	});
});
