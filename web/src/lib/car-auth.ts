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
