import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

export function sha256Hex(input: string): string {
	return createHash('sha256').update(input, 'utf8').digest('hex');
}

/** Constant-time string comparison (compares SHA-256 digests so lengths never leak). */
export function safeEqual(a: string, b: string): boolean {
	const da = createHash('sha256').update(a, 'utf8').digest();
	const db = createHash('sha256').update(b, 'utf8').digest();
	return timingSafeEqual(da, db) && a.length === b.length;
}

export function randomToken(bytes = 32): string {
	return randomBytes(bytes).toString('base64url');
}

export function randomHex(bytes = 16): string {
	return randomBytes(bytes).toString('hex');
}
