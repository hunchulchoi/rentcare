import { describe, expect, it } from 'vitest';
import { attachUrl } from './attach-url.ts';

describe('attachUrl', () => {
	it('opens the phone page in the report language', () => {
		expect(attachUrl('https://rentcare.example', 'en')).toBe('https://rentcare.example/m?lang=en');
		expect(attachUrl('https://rentcare.example/', 'ko')).toBe('https://rentcare.example/m?lang=ko');
	});

	it('drops languages the phone page does not have yet', () => {
		expect(attachUrl('https://rentcare.example', 'ja')).toBe('https://rentcare.example/m');
		expect(attachUrl('https://rentcare.example', 'en&code=1')).toBe('https://rentcare.example/m');
	});
});
