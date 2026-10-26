// Vendor incident list: row shape and the pure rules used by the TanStack table (search, location).
import { typeLabel, type IncidentStatus, type IncidentType, type LocationSource } from './i18n.ts';

export type ListRow = {
	id: string;
	receipt_no: string;
	type: IncidentType;
	language: string;
	demo: boolean;
	channel?: 'CAR' | 'PHONE';
	status: IncidentStatus;
	vehicle_plate: string;
	vehicle_model: string | null;
	created_at: Date;
	attachments: number;
	latitude: number | null;
	longitude: number | null;
	address: string | null;
	location_source: LocationSource | null;
};

export function locationText(r: Pick<ListRow, 'address' | 'latitude' | 'longitude'>): string {
	if (r.address) return r.address;
	if (r.latitude !== null && r.longitude !== null) return `${r.latitude}, ${r.longitude}`;
	return '위치 없음';
}

/** Global search over what staff read on the row. Empty query matches everything. */
export function incidentMatches(r: ListRow, query: string): boolean {
	const q = query.trim().toLowerCase();
	if (!q) return true;
	return [r.receipt_no, r.vehicle_plate, r.vehicle_model ?? '', locationText(r), typeLabel(r.type, 'ko')]
		.some((v) => v.toLowerCase().includes(q));
}
