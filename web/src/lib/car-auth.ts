// Car credentials (RENTCARE-36, docs/api.md v13). No shared key ships in the app: the car registers the
// rental with a vendor-issued enrollment code (or as the open demo vendor) and gets its own rental token.

export const ENROLLMENT_TTL_MS = 30 * 60 * 1000;
/** Open demo registrations per client IP per hour (demo vendor only, always demo: true). */
export const DEMO_REGISTRATIONS_PER_HOUR = 60;
/** Car reports per registered rental (a leaked rental token cannot flood the vendor). */
export const CAR_REPORTS_PER_RENTAL = 30;

const TOKEN_RE = /^[A-Za-z0-9_-]{43}$/; // 32 random bytes, base64url (incident-token.ts)

export type RegistrationAuth = { kind: 'demo' } | { kind: 'code'; code: string } | { kind: 'denied' };

/** How a rental registration is authorized. The code, when given, decides the vendor. */
export function registrationAuth(
	input: { vendorId: string; demo: boolean; enrollmentCode: string | null },
	demoVendorId: string
): RegistrationAuth {
	if (input.enrollmentCode) return { kind: 'code', code: input.enrollmentCode };
	if (input.vendorId === demoVendorId && input.demo) return { kind: 'demo' };
	return { kind: 'denied' };
}

export function bearerToken(header: string | null): string | null {
	const m = /^Bearer (\S+)$/.exec(header ?? '');
	return m && TOKEN_RE.test(m[1]) ? m[1] : null;
}

/** A vendorId in the body must match the credential's vendor (absent is fine). */
export function vendorScope(bodyVendorId: string | undefined, credentialVendorId: string): 'ok' | 'forbidden' {
	return bodyVendorId === undefined || bodyVendorId === credentialVendorId ? 'ok' : 'forbidden';
}

export function enrollmentExpiry(now: Date): Date {
	return new Date(now.getTime() + ENROLLMENT_TTL_MS);
}

export type ReportScope = { vendorId: string; rentalRef: string | null; channel: 'CAR' | 'PHONE' };

/**
 * A replay (same Idempotency-Key) rotates the report's attach code and car token, so it must come from the
 * same rental and channel that filed it; another rental of the same vendor gets a conflict, nothing rotates.
 */
export function reportReplay(existing: ReportScope, request: ReportScope): 'ok' | 'conflict' {
	return existing.vendorId === request.vendorId &&
		existing.rentalRef !== null &&
		existing.rentalRef === request.rentalRef &&
		existing.channel === request.channel
		? 'ok'
		: 'conflict';
}

/**
 * Replaying a registration (lost response, app restart) returns a fresh rental token, so it needs the same
 * credential that made it: the demo registration for a demo-vendor rental, or the enrollment code that this
 * registration consumed (`codeRentalRef` = the rental that code was used for). The key alone is not enough.
 */
export function registrationReplay(
	existing: { vendorId: string; demo: boolean; id: string },
	auth: Exclude<RegistrationAuth, { kind: 'denied' }>,
	codeRentalRef: string | null,
	demoVendorId: string
): 'ok' | 'denied' {
	if (auth.kind === 'demo') return existing.vendorId === demoVendorId && existing.demo ? 'ok' : 'denied';
	return codeRentalRef !== null && codeRentalRef === existing.id ? 'ok' : 'denied';
}
