// Rentals registered by the car and the phone basic report (RENTCARE-13, docs/api.md v11).
import { PHONE_REPORTS_PER_RENTAL, generateRentalCode, rentalCodeExpiry, type PhoneReport } from '../rental-code.ts';
import { isoZ } from '../receipt';
import type { Language } from '../i18n';
import { sha256Hex } from './crypto';
import { db } from './db';
import { createIncident, type CreatedIncident } from './incidents';
import { issueIncidentToken } from './incident-token';
import type { RentalRegistration } from './validate';
import { DEMO_VENDOR_ID } from './db';
import { enrollmentExpiry, registrationReplay, type RegistrationAuth } from '../car-auth.ts';

export type RegisterResult =
	| { kind: 'created' | 'replayed'; rentalCode: string; rentalCodeExpiresAt: string; rentalToken: string }
	| { kind: 'unknown_vendor' }
	| { kind: 'key_conflict' }
	| { kind: 'code_invalid' }
	| { kind: 'code_expired' }
	/** Same Idempotency-Key, but not the credential that made the registration. */
	| { kind: 'replay_denied' };

/** Vendor screen: a single-use car enrollment code for this vendor, valid 30 minutes (docs/api.md v13). */
export async function issueEnrollmentCode(vendorId: string, now = new Date()): Promise<{ code: string; expiresAt: Date }> {
	const expiresAt = enrollmentExpiry(now);
	for (let i = 0; i < 8; i++) {
		const code = generateRentalCode();
		const rows = await db()`
			insert into enrollment_codes (code_hash, vendor_id, expires_at, created_at)
			values (${sha256Hex(code)}, ${vendorId}, ${expiresAt}, ${now})
			on conflict (code_hash) do nothing returning code_hash`;
		if (rows.length === 1) return { code, expiresAt };
	}
	throw new Error('could not allocate a unique enrollment code');
}

/**
 * Register a rental, or replay with the same Idempotency-Key (new code and token, old ones stop working).
 * A new registration needs an enrollment code (vendor taken from the code) or is the open demo
 * registration (demo vendor, always demo). A replay needs neither: the key is the car's own secret.
 */
export async function registerRental(
	input: RentalRegistration,
	idempotencyKey: string,
	auth: Exclude<RegistrationAuth, { kind: 'denied' }>,
	now = new Date()
): Promise<RegisterResult> {
	return db().begin(async (sql) => {
		await sql`select pg_advisory_xact_lock(hashtextextended(${'rental:' + idempotencyKey}, 0))`;
		const existing = await sql<{ id: string; vendor_id: string; demo: boolean }[]>`
			select id, vendor_id, demo from rentals where idempotency_key = ${idempotencyKey}`;
		if (existing.length) {
			const e = existing[0];
			const codeRentalRef = auth.kind === 'code'
				? ((await sql<{ rental_ref: string | null }[]>`
					select rental_ref from enrollment_codes where code_hash = ${sha256Hex(auth.code)}`)[0]?.rental_ref ?? null)
				: null;
			if (registrationReplay({ vendorId: e.vendor_id, demo: e.demo, id: e.id }, auth, codeRentalRef, DEMO_VENDOR_ID) === 'denied') {
				return { kind: 'replay_denied' } as const;
			}
		}
		let vendorId = auth.kind === 'demo' ? DEMO_VENDOR_ID : input.vendorId;
		const demo = auth.kind === 'demo' ? true : input.demo;
		let enrollment: string | null = null;
		if (!existing.length && auth.kind === 'code') {
			const [c] = await sql<{ vendor_id: string; expires_at: Date; used_at: Date | null }[]>`
				select vendor_id, expires_at, used_at from enrollment_codes where code_hash = ${sha256Hex(auth.code)} for update`;
			if (!c || c.used_at) return { kind: 'code_invalid' } as const;
			if (c.expires_at.getTime() <= now.getTime()) return { kind: 'code_expired' } as const;
			vendorId = c.vendor_id;
			enrollment = sha256Hex(auth.code);
		}
		if (existing.length && existing[0].vendor_id !== vendorId) return { kind: 'key_conflict' } as const;
		if (!existing.length) {
			const vendor = await sql`select 1 from vendors where id = ${vendorId}`;
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
				values (${vendorId}, ${idempotencyKey}, ${input.language}, ${demo}, ${r.rentalId}, ${r.vehiclePlate},
					${r.vehicleModel}, ${r.powertrain}, ${r.returnPlace}, ${r.returnAt}, ${token.hash}, ${now})
				returning id`;
			id = row.id;
			if (enrollment) await sql`update enrollment_codes set used_at = ${now}, rental_ref = ${id} where code_hash = ${enrollment}`;
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
				-- Reports from before v13 carry no rental_ref; match those by rental fields. Never reach into
				-- reports linked to another registration that happens to share the rental id and plate.
				or (i.rental_ref is null and i.vendor_id = r.vendor_id and i.rental_id = r.rental_id
					and i.vehicle_plate = r.vehicle_plate and i.created_at >= r.created_at)`;
		await sql`update attach_codes set revoked_at = now() where revoked_at is null and incident_id in (${reports})`;
		await sql`update incidents set car_token_hash = null where id in (${reports})`;
		return true;
	});
}

/** The car's rental behind a rental token (v13): not released and not past the code expiry. */
export interface TokenRental {
	id: string;
	vendorId: string;
	demo: boolean;
	rentalId: string;
	vehiclePlate: string;
	vehicleModel: string | null;
	powertrain: string | null;
	returnPlace: string | null;
	returnAt: Date | null;
}

export async function findRentalByToken(token: string, now = new Date()): Promise<TokenRental | null> {
	const rows = await db()<TokenRental[]>`
		select id, vendor_id as "vendorId", demo, rental_id as "rentalId", vehicle_plate as "vehiclePlate",
			vehicle_model as "vehicleModel", powertrain, return_place as "returnPlace", return_at as "returnAt"
		from rentals
		where car_token_hash = ${sha256Hex(token)} and released_at is null and code_expires_at > ${now}`;
	return rows[0] ?? null;
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
		{ rentalRef: r.id, maxReports: PHONE_REPORTS_PER_RENTAL, channel: 'PHONE' }
	);
	if (res.kind === 'too_many_reports') return { ok: false, error: 'too_many_reports' };
	if (res.kind === 'created' || res.kind === 'replayed') return { ok: true, incident: res.incident, replayed: res.kind === 'replayed' };
	return { ok: false, error: 'server_error' };
}
