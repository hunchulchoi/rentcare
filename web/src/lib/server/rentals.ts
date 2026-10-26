// Rentals registered by the car and the phone basic report (RENTCARE-13, docs/api.md v11).
import { PHONE_REPORTS_PER_RENTAL, generateRentalCode, rentalCodeExpiry, type PhoneReport } from '../rental-code.ts';
import { isoZ } from '../receipt';
import type { Language } from '../i18n';
import { sha256Hex } from './crypto';
import { db } from './db';
import { createIncident, type CreatedIncident } from './incidents';
import { issueIncidentToken } from './incident-token';
import type { RentalRegistration } from './validate';

export type RegisterResult =
	| { kind: 'created' | 'replayed'; rentalCode: string; rentalCodeExpiresAt: string; rentalToken: string }
	| { kind: 'unknown_vendor' }
	| { kind: 'key_conflict' };

/** Register a rental, or replay with the same Idempotency-Key (new code and token, old ones stop working). */
export async function registerRental(input: RentalRegistration, idempotencyKey: string, now = new Date()): Promise<RegisterResult> {
	return db().begin(async (sql) => {
		await sql`select pg_advisory_xact_lock(hashtextextended(${'rental:' + idempotencyKey}, 0))`;
		const existing = await sql<{ id: string; vendor_id: string }[]>`
			select id, vendor_id from rentals where idempotency_key = ${idempotencyKey}`;
		if (existing.length && existing[0].vendor_id !== input.vendorId) return { kind: 'key_conflict' } as const;
		if (!existing.length) {
			const vendor = await sql`select 1 from vendors where id = ${input.vendorId}`;
			if (!vendor.length) return { kind: 'unknown_vendor' } as const;
		}
		const r = input.rental;
		const token = issueIncidentToken();
		const expiresAt = rentalCodeExpiry(now, r.returnAt);
		let id = existing[0]?.id;
		if (!id) {
			const [row] = await sql<{ id: string }[]>`
				insert into rentals (vendor_id, idempotency_key, language, demo, rental_id, vehicle_plate, vehicle_model,
					powertrain, return_place, return_at, car_token_hash, created_at)
				values (${input.vendorId}, ${idempotencyKey}, ${input.language}, ${input.demo}, ${r.rentalId}, ${r.vehiclePlate},
					${r.vehicleModel}, ${r.powertrain}, ${r.returnPlace}, ${r.returnAt}, ${token.hash}, ${now})
				returning id`;
			id = row.id;
		}
		for (let i = 0; i < 8; i++) {
			const code = generateRentalCode();
			const rows = await sql`
				update rentals set code_hash = ${sha256Hex(code)}, code_expires_at = ${expiresAt},
					car_token_hash = ${token.hash}, released_at = null
				where id = ${id} and not exists (select 1 from rentals where code_hash = ${sha256Hex(code)})
				returning id`;
			if (rows.length === 1) {
				return {
					kind: existing.length ? 'replayed' : 'created',
					rentalCode: code,
					rentalCodeExpiresAt: isoZ(expiresAt),
					rentalToken: token.token
				} as const;
			}
		}
		throw new Error('could not allocate a unique rental code');
	});
}

/**
 * Rental end: revoke the rental code and token, and the attach codes and car tokens of every report filed
 * during this rental (the car only remembers its latest report). True if the token matched.
 */
export async function releaseRental(token: string): Promise<boolean> {
	return db().begin(async (sql) => {
		const rows = await sql<{ id: string }[]>`
			update rentals set code_hash = null, code_expires_at = null, car_token_hash = null, released_at = now()
			where car_token_hash = ${sha256Hex(token)}
			returning id`;
		if (rows.length !== 1) return false;
		const reports = sql`
			select i.id from incidents i join rentals r on r.id = ${rows[0].id}
			where i.rental_ref = r.id
				or (i.vendor_id = r.vendor_id and i.rental_id = r.rental_id and i.vehicle_plate = r.vehicle_plate
					and i.created_at >= r.created_at)`;
		await sql`update attach_codes set revoked_at = now() where revoked_at is null and incident_id in (${reports})`;
		await sql`update incidents set car_token_hash = null where id in (${reports})`;
		return true;
	});
}

export interface CodeRental {
	id: string;
	vendorId: string;
	vendorName: string;
	language: Language;
	demo: boolean;
	rentalId: string;
	vehiclePlate: string;
	vehicleModel: string | null;
	powertrain: string | null;
	returnPlace: string | null;
	returnAt: Date | null;
}

export async function findByRentalCode(
	code: string,
	now = new Date()
): Promise<{ ok: true; rental: CodeRental } | { ok: false; error: 'code_invalid' | 'code_expired' }> {
	const rows = await db()<(CodeRental & { codeExpiresAt: Date })[]>`
		select r.id, r.vendor_id as "vendorId", v.name as "vendorName", r.language, r.demo, r.rental_id as "rentalId",
			r.vehicle_plate as "vehiclePlate", r.vehicle_model as "vehicleModel", r.powertrain,
			r.return_place as "returnPlace", r.return_at as "returnAt", r.code_expires_at as "codeExpiresAt"
		from rentals r join vendors v on v.id = r.vendor_id
		where r.code_hash = ${sha256Hex(code)}`;
	if (!rows.length) return { ok: false, error: 'code_invalid' };
	const { codeExpiresAt, ...rental } = rows[0];
	if (codeExpiresAt.getTime() <= now.getTime()) return { ok: false, error: 'code_expired' };
	return { ok: true, rental };
}

export type PhoneReportResult =
	| { ok: true; incident: CreatedIncident; replayed: boolean }
	| { ok: false; error: 'code_invalid' | 'code_expired' | 'too_many_reports' | 'server_error' };

/** File a basic report from a phone for the rental behind the code. */
export async function createPhoneReport(report: PhoneReport, language: Language, now = new Date()): Promise<PhoneReportResult> {
	const f = await findByRentalCode(report.code, now);
	if (!f.ok) return f;
	const r = f.rental;
	const res = await createIncident(
		{
			vendorId: r.vendorId,
			type: report.type,
			language,
			note: report.note,
			occurredAt: now,
			demo: r.demo,
			rental: {
				rentalId: r.rentalId,
				vehiclePlate: r.vehiclePlate,
				vehicleModel: r.vehicleModel,
				powertrain: r.powertrain,
				returnPlace: r.returnPlace,
				returnAt: r.returnAt
			},
			vehicle: null,
			location: report.location
				? { latitude: report.location.latitude, longitude: report.location.longitude, address: null, readAt: now, source: 'MOBILE', edited: false }
				: null,
			warnings: []
		},
		report.key,
		now,
		{ rentalRef: r.id, maxReports: PHONE_REPORTS_PER_RENTAL }
	);
	if (res.kind === 'too_many_reports') return { ok: false, error: 'too_many_reports' };
	if (res.kind === 'created' || res.kind === 'replayed') return { ok: true, incident: res.incident, replayed: res.kind === 'replayed' };
	return { ok: false, error: 'server_error' };
}
