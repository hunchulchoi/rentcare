import type postgres from 'postgres';
import { ATTACH_CODE_TTL_MS, generateAttachCode } from '../attach-code';
import { MAX_FILE_BYTES, MAX_FILES_PER_INCIDENT, magicMatches, resolveMime, sanitizeDisplayName } from '../files';
import type { IncidentStatus, IncidentType } from '../i18n';
import { formatReceiptNo, isoZ, kstDayKey } from '../receipt';
import { sha256Hex } from './crypto';
import { incidentTokenMatches, issueIncidentToken } from './incident-token';
import { db } from './db';
import { removeFile, saveFile } from './storage';
import type { IncidentInput } from './validate';

type Tx = postgres.TransactionSql;

export interface CreatedIncident {
	id: string;
	receiptNo: string;
	status: IncidentStatus;
	attachCode: string;
	attachCodeExpiresAt: string;
	/** Shown once to the creating car; required for status and car attachments. */
	incidentToken: string;
}

export type CreateResult =
	| { kind: 'created' | 'replayed'; incident: CreatedIncident }
	| { kind: 'unknown_vendor' }
	| { kind: 'key_conflict' };

async function issueAttachCode(sql: Tx, incidentId: string, now: Date): Promise<{ code: string; expiresAt: Date }> {
	await sql`update attach_codes set revoked_at = ${now} where incident_id = ${incidentId} and revoked_at is null`;
	const expiresAt = new Date(now.getTime() + ATTACH_CODE_TTL_MS);
	for (let i = 0; i < 8; i++) {
		const code = generateAttachCode();
		const rows = await sql`
			insert into attach_codes (incident_id, code_hash, expires_at)
			values (${incidentId}, ${sha256Hex(code)}, ${expiresAt})
			on conflict (code_hash) do nothing
			returning id`;
		if (rows.length === 1) return { code, expiresAt };
	}
	throw new Error('could not allocate a unique attach code');
}

/**
 * Create an incident, or replay an existing one with the same Idempotency-Key
 * (which issues a fresh attach code and revokes the previous one).
 */
export async function createIncident(input: IncidentInput, idempotencyKey: string, now = new Date()): Promise<CreateResult> {
	return db().begin(async (sql) => {
		// Serialize concurrent requests with the same key.
		await sql`select pg_advisory_xact_lock(hashtextextended(${idempotencyKey}, 0))`;

		const existing = await sql<{ id: string; receipt_no: string; status: IncidentStatus; vendor_id: string }[]>`
			select id, receipt_no, status, vendor_id from incidents where idempotency_key = ${idempotencyKey}`;
		if (existing.length) {
			const e = existing[0];
			if (e.vendor_id !== input.vendorId) return { kind: 'key_conflict' } as const;
			const { code, expiresAt } = await issueAttachCode(sql, e.id, now);
			// Rotate: the replaying car gets a fresh token, the previous one stops working.
			const t = issueIncidentToken();
			await sql`update incidents set car_token_hash = ${t.hash} where id = ${e.id}`;
			return {
				kind: 'replayed',
				incident: {
					id: e.id, receiptNo: e.receipt_no, status: e.status, attachCode: code,
					attachCodeExpiresAt: isoZ(expiresAt), incidentToken: t.token
				}
			} as const;
		}

		const vendor = await sql`select 1 from vendors where id = ${input.vendorId}`;
		if (!vendor.length) return { kind: 'unknown_vendor' } as const;

		const day = kstDayKey(now);
		const [{ last_no }] = await sql<{ last_no: number }[]>`
			insert into receipt_counters (day, last_no) values (${day}, 1)
			on conflict (day) do update set last_no = receipt_counters.last_no + 1
			returning last_no`;
		const receiptNo = formatReceiptNo(day, last_no);

		const token = issueIncidentToken();
		const r = input.rental;
		const v = input.vehicle;
		const l = input.location;
		const [row] = await sql<{ id: string }[]>`
			insert into incidents (
				vendor_id, receipt_no, idempotency_key, type, language, note, occurred_at, demo,
				rental_id, vehicle_plate, vehicle_model, powertrain, return_place, return_at,
				vehicle_read, fuel_percent, soc, vehicle_read_at,
				latitude, longitude, address, location_read_at, location_source, location_edited,
				car_token_hash, warnings, created_at
			) values (
				${input.vendorId}, ${receiptNo}, ${idempotencyKey}, ${input.type}, ${input.language}, ${input.note},
				${input.occurredAt}, ${input.demo},
				${r.rentalId}, ${r.vehiclePlate}, ${r.vehicleModel}, ${r.powertrain}, ${r.returnPlace}, ${r.returnAt},
				${v !== null}, ${v?.fuelPercent ?? null}, ${v?.soc ?? null}, ${v?.readAt ?? null},
				${l?.latitude ?? null}, ${l?.longitude ?? null}, ${l?.address ?? null}, ${l?.readAt ?? null},
				${l?.source ?? null}, ${l?.edited ?? null},
				${token.hash}, ${input.warnings}, ${now}
			)
			returning id`;

		const { code, expiresAt } = await issueAttachCode(sql, row.id, now);
		return {
			kind: 'created',
			incident: {
				id: row.id, receiptNo, status: 'RECEIVED', attachCode: code,
				attachCodeExpiresAt: isoZ(expiresAt), incidentToken: token.token
			}
		} as const;
	});
}

/** True only for the car holding this incident's current token. */
export async function carMayAccess(id: string, token: string | null): Promise<boolean> {
	if (!token) return false;
	const rows = await db()<{ car_token_hash: string | null }[]>`select car_token_hash from incidents where id = ${id}`;
	return rows.length === 1 && incidentTokenMatches(token, rows[0].car_token_hash);
}

export async function getIncidentStatus(id: string) {
	const rows = await db()<
		{ id: string; receipt_no: string; status: IncidentStatus; acknowledged_at: Date | null; resolved_at: Date | null; expected_at: Date | null; expected_set_at: Date | null; attachments: number }[]
	>`
		select i.id, i.receipt_no, i.status, i.acknowledged_at, i.resolved_at, i.expected_at, i.expected_set_at,
			(select count(*)::int from attachments a where a.incident_id = i.id) as attachments
		from incidents i where i.id = ${id}`;
	if (!rows.length) return null;
	const r = rows[0];
	return {
		id: r.id,
		receiptNo: r.receipt_no,
		status: r.status,
		acknowledgedAt: r.acknowledged_at ? isoZ(r.acknowledged_at) : null,
		resolvedAt: r.resolved_at ? isoZ(r.resolved_at) : null,
		expectedAt: r.expected_at ? isoZ(r.expected_at) : null,
		expectedSetAt: r.expected_set_at ? isoZ(r.expected_set_at) : null,
		attachments: r.attachments
	};
}

export interface CodeIncident {
	id: string;
	receiptNo: string;
	type: IncidentType;
	language: string;
	attachments: number;
	codeExpiresAt: Date;
}

/** Look up an incident by attach code (already normalized). */
export async function findByAttachCode(
	code: string,
	now = new Date()
): Promise<{ ok: true; incident: CodeIncident } | { ok: false; error: 'code_invalid' | 'code_expired' }> {
	const rows = await db()<
		{
			expires_at: Date;
			revoked_at: Date | null;
			id: string;
			receipt_no: string;
			type: IncidentType;
			language: string;
			attachments: number;
		}[]
	>`
		select c.expires_at, c.revoked_at, i.id, i.receipt_no, i.type, i.language,
			(select count(*)::int from attachments a where a.incident_id = i.id) as attachments
		from attach_codes c join incidents i on i.id = c.incident_id
		where c.code_hash = ${sha256Hex(code)}`;
	if (!rows.length || rows[0].revoked_at) return { ok: false, error: 'code_invalid' };
	const r = rows[0];
	if (r.expires_at.getTime() <= now.getTime()) return { ok: false, error: 'code_expired' };
	return {
		ok: true,
		incident: {
			id: r.id,
			receiptNo: r.receipt_no,
			type: r.type,
			language: r.language,
			attachments: r.attachments,
			codeExpiresAt: r.expires_at
		}
	};
}

export type AttachResult =
	| { ok: true; attachmentId: string; attachments: number }
	| { ok: false; status: number; error: 'not_found' | 'too_large' | 'unsupported_type' | 'too_many_files' | 'invalid_request'; message: string };

/** Validate and store one uploaded file for an incident. */
export async function addAttachment(incidentId: string, file: File, source: 'CAR' | 'MOBILE'): Promise<AttachResult> {
	if (file.size === 0) return { ok: false, status: 400, error: 'invalid_request', message: 'File is empty' };
	if (file.size > MAX_FILE_BYTES) return { ok: false, status: 413, error: 'too_large', message: 'File exceeds 50 MB' };
	const mime = resolveMime(file.type, file.name);
	if (!mime) return { ok: false, status: 415, error: 'unsupported_type', message: 'File type or extension not allowed' };
	const head = new Uint8Array(await file.slice(0, 16).arrayBuffer());
	if (!magicMatches(mime, head)) {
		return { ok: false, status: 415, error: 'unsupported_type', message: 'File content does not match its type' };
	}

	let storageName: string | undefined;
	try {
		return await db().begin(async (sql) => {
			const inc = await sql`select id from incidents where id = ${incidentId} for update`;
			if (!inc.length) return { ok: false, status: 404, error: 'not_found', message: 'Incident not found' } as const;
			const [{ n }] = await sql<{ n: number }[]>`select count(*)::int as n from attachments where incident_id = ${incidentId}`;
			if (n >= MAX_FILES_PER_INCIDENT) {
				return { ok: false, status: 409, error: 'too_many_files', message: 'Max 10 files per incident' } as const;
			}
			storageName = await saveFile(incidentId, file);
			const [row] = await sql<{ id: string }[]>`
				insert into attachments (incident_id, storage_name, original_name, mime_type, size_bytes, source)
				values (${incidentId}, ${storageName}, ${sanitizeDisplayName(file.name)}, ${mime}, ${file.size}, ${source})
				returning id`;
			return { ok: true, attachmentId: row.id, attachments: n + 1 } as const;
		});
	} catch (e) {
		if (storageName) await removeFile(storageName).catch(() => {});
		throw e;
	}
}
