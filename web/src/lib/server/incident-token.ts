import { randomToken, safeEqual, sha256Hex } from './crypto';

/**
 * Per-incident capability for the car that created it. The shared CAR_API_KEY only allows
 * creating incidents; reading status or adding files also needs this token, so one leaked
 * APK key cannot read or modify other cars' reports. Only the SHA-256 hash is stored.
 */
export const INCIDENT_TOKEN_HEADER = 'x-incident-token';
const TOKEN_RE = /^[A-Za-z0-9_-]{43}$/; // 32 random bytes, base64url

export function issueIncidentToken(): { token: string; hash: string } {
	const token = randomToken(32);
	return { token, hash: sha256Hex(token) };
}

export const RENTAL_TOKEN_HEADER = 'x-rental-token';

export function readIncidentToken(request: Request, header = INCIDENT_TOKEN_HEADER): string | null {
	const v = request.headers.get(header)?.trim() ?? '';
	return TOKEN_RE.test(v) ? v : null;
}

export function incidentTokenMatches(token: string, storedHash: string | null): boolean {
	if (!storedHash) return false;
	return safeEqual(sha256Hex(token), storedHash);
}
