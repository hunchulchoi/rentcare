import { describe, expect, it } from 'vitest';
import { INCIDENT_TYPES, INCIDENT_TYPE_LABELS, MOBILE_STRINGS, pickUiLang, typeLabel } from './i18n';

describe('i18n', () => {
	it('has a label for every incident type in every UI language', () => {
		for (const lang of ['ko', 'en'] as const) for (const t of INCIDENT_TYPES) expect(INCIDENT_TYPE_LABELS[lang][t]).toBeTruthy();
		expect(typeLabel('NO_START', 'ko')).toBe('시동 불가');
		expect(typeLabel('TIRE', 'en')).toBe('Tire problem');
	});
	it('has the same mobile string keys in ko and en', () => {
		expect(Object.keys(MOBILE_STRINGS.ko).sort()).toEqual(Object.keys(MOBILE_STRINGS.en).sort());
	});
	it('picks UI language from query, then Accept-Language, else en', () => {
		expect(pickUiLang('ko', 'en-US')).toBe('ko');
		expect(pickUiLang('fr', 'ko-KR,ko;q=0.9')).toBe('ko');
		expect(pickUiLang(null, 'ja-JP,en;q=0.5')).toBe('en');
		expect(pickUiLang(null, null)).toBe('en');
	});
});
