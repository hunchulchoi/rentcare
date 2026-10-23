import { describe, expect, it } from 'vitest';
import { ATTACH_CODE_ALPHABET, generateAttachCode, normalizeAttachCode } from './attach-code';

describe('generateAttachCode', () => {
	it('produces 6 chars from the alphabet', () => {
		for (let i = 0; i < 500; i++) {
			const c = generateAttachCode();
			expect(c).toMatch(/^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$/);
		}
	});

	it('never uses look-alike characters', () => {
		expect(ATTACH_CODE_ALPHABET).not.toMatch(/[ILO01]/);
		expect(ATTACH_CODE_ALPHABET).toHaveLength(31);
	});

	it('rejects biased bytes (>= 248) instead of wrapping', () => {
		// first call: all 255 (rejected); then sequential bytes 0..11
		let call = 0;
		const rnd = (n: number) => {
			call++;
			return call === 1 ? new Uint8Array(n).fill(255) : Uint8Array.from({ length: n }, (_, i) => i);
		};
		expect(generateAttachCode(rnd)).toBe('ABCDEF');
		expect(call).toBe(2);
	});

	it('maps byte values modulo the alphabet length', () => {
		const rnd = (n: number) => Uint8Array.from({ length: n }, () => 31 + 30); // 61 % 31 = 30 -> '9'
		expect(generateAttachCode(rnd)).toBe('999999');
	});
});

describe('normalizeAttachCode', () => {
	it('upper-cases and strips spaces and hyphens', () => {
		expect(normalizeAttachCode('k7q2m9')).toBe('K7Q2M9');
		expect(normalizeAttachCode(' K7Q-2M9 ')).toBe('K7Q2M9');
		expect(normalizeAttachCode('k7q 2m9')).toBe('K7Q2M9');
	});

	it('rejects wrong length, excluded chars and non-strings', () => {
		expect(normalizeAttachCode('K7Q2M')).toBeNull();
		expect(normalizeAttachCode('K7Q2M9X')).toBeNull();
		expect(normalizeAttachCode('K7Q2M0')).toBeNull(); // 0 not in alphabet
		expect(normalizeAttachCode('IIIIII')).toBeNull();
		expect(normalizeAttachCode('K7Q2M!')).toBeNull();
		expect(normalizeAttachCode(null)).toBeNull();
		expect(normalizeAttachCode(123456)).toBeNull();
		expect(normalizeAttachCode('x'.repeat(100))).toBeNull();
	});
});

describe('cleanCodeInput (6-box input)', () => {
	it('upper-cases, drops separators and characters the code never uses, keeps at most 6', async () => {
		const { cleanCodeInput } = await import('./attach-code');
		expect(cleanCodeInput('k7q2m9')).toBe('K7Q2M9');
		expect(cleanCodeInput('K7Q-2M9')).toBe('K7Q2M9');
		expect(cleanCodeInput(' k 7 q ')).toBe('K7Q');
		expect(cleanCodeInput('K7Q2M9XYZ')).toBe('K7Q2M9');
		expect(cleanCodeInput('O0I1L')).toBe('');
		expect(cleanCodeInput('한글K7')).toBe('K7');
	});
});
