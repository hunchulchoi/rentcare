// Attach codes: 6 chars from an alphabet without look-alikes (no I, L, O, 0, 1).

export const ATTACH_CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
export const ATTACH_CODE_LENGTH = 6;
export const ATTACH_CODE_TTL_MS = 24 * 60 * 60 * 1000;

/**
 * Generate a code with unbiased rejection sampling.
 * `randomBytes` is injectable for tests; defaults to Web Crypto.
 */
export function generateAttachCode(
	randomBytes: (n: number) => Uint8Array = (n) => crypto.getRandomValues(new Uint8Array(n)),
	length = ATTACH_CODE_LENGTH
): string {
	const alphabetLength = ATTACH_CODE_ALPHABET.length; // 31
	const limit = 256 - (256 % alphabetLength); // reject bytes >= 248 to avoid modulo bias
	let out = '';
	while (out.length < length) {
		for (const b of randomBytes(length * 2)) {
			if (b >= limit) continue;
			out += ATTACH_CODE_ALPHABET[b % alphabetLength];
			if (out.length === length) break;
		}
	}
	return out;
}

/** For the 6-box input while typing: upper-case, keep only code characters, at most 6. */
export function cleanCodeInput(raw: string, length = ATTACH_CODE_LENGTH): string {
	let out = '';
	for (const ch of raw.toUpperCase()) {
		if (ATTACH_CODE_ALPHABET.includes(ch)) out += ch;
		if (out.length === length) break;
	}
	return out;
}

/**
 * Normalize user input: trim, drop spaces/hyphens, upper-case.
 * Returns null if the result is not a well-formed code.
 */
export function normalizeAttachCode(input: unknown, length = ATTACH_CODE_LENGTH): string | null {
	if (typeof input !== 'string' || input.length > 32) return null;
	const s = input.replace(/[\s-]/g, '').toUpperCase();
	if (s.length !== length) return null;
	for (const ch of s) if (!ATTACH_CODE_ALPHABET.includes(ch)) return null;
	return s;
}
