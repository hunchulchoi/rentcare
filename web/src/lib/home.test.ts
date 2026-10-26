import { describe, expect, it } from 'vitest';
import { HOME_STRINGS, photoHref } from './home.ts';

describe('home page', () => {
	it('has the same strings in every language', () => {
		for (const l of ['ko', 'ja', 'zh'] as const) expect(Object.keys(HOME_STRINGS[l]).sort()).toEqual(Object.keys(HOME_STRINGS.en).sort());
	});

	it('keeps an explicit ?lang for the photo page, otherwise lets the phone browser decide', () => {
		expect(photoHref('en')).toBe('/m?lang=en');
		expect(photoHref('ko')).toBe('/m?lang=ko');
		expect(photoHref(null)).toBe('/m');
		expect(photoHref('fr')).toBe('/m');
		expect(photoHref('en&x=1')).toBe('/m');
	});

	it('never mentions the vendor console', () => {
		for (const s of Object.values(HOME_STRINGS)) expect(JSON.stringify(s)).not.toMatch(/vendor|업체 화면|\/vendor/i);
	});
});
