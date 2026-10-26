// Rental code (RENTCARE-13): issued when the car saves the rental, so the renter can still file a basic
// report from a phone if the car screen cannot be used. 8 chars (the 6-char attach code is per report).
import { generateAttachCode, normalizeAttachCode } from './attach-code.ts';
import { INCIDENT_TYPES, type IncidentType } from './i18n.ts';
import { parsePhoneLocation, type PhoneLocation } from './phone-location.ts';

export const RENTAL_CODE_LENGTH = 8;
export const PHONE_REPORTS_PER_RENTAL = 5;
const NOTE_MAX = 2000;
const H = 3600_000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function generateRentalCode(randomBytes?: (n: number) => Uint8Array): string {
	return generateAttachCode(randomBytes, RENTAL_CODE_LENGTH);
}

export function normalizeRentalCode(input: unknown): string | null {
	return normalizeAttachCode(input, RENTAL_CODE_LENGTH);
}

/** Valid until the return time + 24 h, at least 24 h from now, at most 31 days. */
export function rentalCodeExpiry(now: Date, returnAt: Date | null): Date {
	const t = now.getTime();
	const want = returnAt ? returnAt.getTime() + 24 * H : 0;
	return new Date(Math.min(Math.max(want, t + 24 * H), t + 31 * 24 * H));
}

export type PhoneReport = {
	code: string;
	type: IncidentType;
	note: string | null;
	location: PhoneLocation | null;
	/** Idempotency key from the form, so a double tap files one report. */
	key: string;
};

export type PhoneReportError =
	| 'code_invalid'
	| 'type_required'
	| 'consent_required'
	| 'note_too_long'
	| 'location_invalid'
	| 'invalid_request';

export function parsePhoneReport(f: FormData): { ok: true; value: PhoneReport } | { ok: false; error: PhoneReportError } {
	const str = (k: string) => {
		const v = f.get(k);
		return typeof v === 'string' ? v : '';
	};
	const code = normalizeRentalCode(str('code'));
	if (!code) return { ok: false, error: 'code_invalid' };
	const key = str('key').trim().toLowerCase();
	if (!UUID.test(key)) return { ok: false, error: 'invalid_request' };
	const type = str('type');
	if (!(INCIDENT_TYPES as readonly string[]).includes(type)) return { ok: false, error: 'type_required' };
	if (str('consent') !== 'yes') return { ok: false, error: 'consent_required' };
	const raw = str('note').trim();
	// eslint-disable-next-line no-control-regex
	if ([...raw].length > NOTE_MAX || /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/.test(raw)) return { ok: false, error: 'note_too_long' };
	let location: PhoneLocation | null = null;
	if (str('lat') || str('lon')) {
		const l = parsePhoneLocation(f);
		if (!l.ok) return { ok: false, error: 'location_invalid' };
		location = l.value;
	}
	return { ok: true, value: { code, type: type as IncidentType, note: raw || null, location, key } };
}
