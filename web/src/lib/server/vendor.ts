import type { Cookies } from '@sveltejs/kit';
import type { IncidentStatus, IncidentType, LocationSource } from '../i18n';
import { randomToken, sha256Hex } from './crypto';
import { db, syncDemoVendor } from './db';

export const SESSION_COOKIE = 'rc_vendor_session';
const SESSION_TTL_MS = 12 * 60 * 60 * 1000;

export interface VendorSession {
	vendorId: string;
	vendorName: string;
	staffLabel: string | null;
}

export async function login(token: string, staffLabel: string | null): Promise<{ sessionToken: string; expiresAt: Date } | null> {
	if (!token) return null;
	await syncDemoVendor();
	const sql = db();
	const vendors = await sql<{ id: string }[]>`
		select id from vendors where staff_token_hash is not null and staff_token_hash = ${sha256Hex(token)}`;
	if (vendors.length !== 1) return null;
	const sessionToken = randomToken(32);
	const expiresAt = new Date(Date.now() + SESSION_TTL_MS);
	await sql`delete from vendor_sessions where expires_at < now()`;
	await sql`
		insert into vendor_sessions (token_hash, vendor_id, staff_label, expires_at)
		values (${sha256Hex(sessionToken)}, ${vendors[0].id}, ${staffLabel}, ${expiresAt})`;
	return { sessionToken, expiresAt };
}

export async function getSession(sessionToken: string | undefined): Promise<VendorSession | null> {
	if (!sessionToken || sessionToken.length > 128) return null;
	const rows = await db()<{ vendor_id: string; name: string; staff_label: string | null }[]>`
		select s.vendor_id, v.name, s.staff_label
		from vendor_sessions s join vendors v on v.id = s.vendor_id
		where s.token_hash = ${sha256Hex(sessionToken)} and s.expires_at > now()`;
	if (!rows.length) return null;
	return { vendorId: rows[0].vendor_id, vendorName: rows[0].name, staffLabel: rows[0].staff_label };
}

export async function logout(sessionToken: string | undefined): Promise<void> {
	if (!sessionToken) return;
	await db()`delete from vendor_sessions where token_hash = ${sha256Hex(sessionToken)}`;
}

export function setSessionCookie(cookies: Cookies, token: string, expiresAt: Date, secure: boolean): void {
	cookies.set(SESSION_COOKIE, token, {
		path: '/vendor',
		httpOnly: true,
		sameSite: 'lax',
		secure,
		expires: expiresAt
	});
}

export interface IncidentListRow {
	id: string;
	receipt_no: string;
	type: IncidentType;
	language: string;
	demo: boolean;
	status: IncidentStatus;
	vehicle_plate: string;
	created_at: Date;
	attachments: number;
}

export async function listIncidents(vendorId: string): Promise<IncidentListRow[]> {
	return db()<IncidentListRow[]>`
		select i.id, i.receipt_no, i.type, i.language, i.demo, i.status, i.vehicle_plate, i.created_at,
			(select count(*)::int from attachments a where a.incident_id = i.id) as attachments
		from incidents i
		where i.vendor_id = ${vendorId}
		order by i.created_at desc
		limit 200`;
}

export interface IncidentDetailRow {
	id: string;
	receipt_no: string;
	type: IncidentType;
	language: string;
	note: string | null;
	occurred_at: Date;
	demo: boolean;
	rental_id: string;
	vehicle_plate: string;
	vehicle_model: string | null;
	powertrain: string | null;
	return_place: string | null;
	return_at: Date | null;
	vehicle_read: boolean;
	fuel_percent: number | null;
	soc: number | null;
	vehicle_read_at: Date | null;
	latitude: number | null;
	longitude: number | null;
	address: string | null;
	location_read_at: Date | null;
	location_source: LocationSource | null;
	location_edited: boolean | null;
	warnings: string[];
	status: IncidentStatus;
	acknowledged_at: Date | null;
	acknowledged_by: string | null;
	created_at: Date;
}

export interface AttachmentRow {
	id: string;
	original_name: string | null;
	mime_type: string;
	size_bytes: number;
	source: 'CAR' | 'MOBILE';
	created_at: Date;
}

/** Returns null when the incident does not exist or belongs to another vendor. */
export async function getIncidentDetail(vendorId: string, id: string) {
	const sql = db();
	const rows = await sql<IncidentDetailRow[]>`
		select id, receipt_no, type, language, note, occurred_at, demo,
			rental_id, vehicle_plate, vehicle_model, powertrain, return_place, return_at,
			vehicle_read, fuel_percent, soc, vehicle_read_at,
			latitude, longitude, address, location_read_at, location_source, location_edited, warnings,
			status, acknowledged_at, acknowledged_by, created_at
		from incidents where id = ${id} and vendor_id = ${vendorId}`;
	if (!rows.length) return null;
	const attachments = await sql<AttachmentRow[]>`
		select id, original_name, mime_type, size_bytes::float8 as size_bytes, source, created_at
		from attachments where incident_id = ${id} order by created_at`;
	return { incident: rows[0], attachments };
}

export async function acknowledge(vendorId: string, id: string, by: string): Promise<boolean> {
	const sql = db();
	const rows = await sql`
		update incidents set status = 'ACKNOWLEDGED', acknowledged_at = now(), acknowledged_by = ${by}
		where id = ${id} and vendor_id = ${vendorId} and status = 'RECEIVED'
		returning id`;
	if (rows.length) return true;
	const exists = await sql`select 1 from incidents where id = ${id} and vendor_id = ${vendorId}`;
	return exists.length > 0;
}

/** Attachment owned by this vendor (via its incident), or null. */
export async function getVendorAttachment(vendorId: string, attachmentId: string) {
	const rows = await db()<{ storage_name: string; mime_type: string; receipt_no: string; idx: number }[]>`
		select a.storage_name, a.mime_type, i.receipt_no,
			(select count(*)::int from attachments b where b.incident_id = a.incident_id and b.created_at <= a.created_at) as idx
		from attachments a join incidents i on i.id = a.incident_id
		where a.id = ${attachmentId} and i.vendor_id = ${vendorId}`;
	return rows[0] ?? null;
}
