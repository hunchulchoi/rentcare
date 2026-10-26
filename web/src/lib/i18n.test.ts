import { describe, expect, it } from 'vitest';
import { INCIDENT_TYPES, INCIDENT_TYPE_LABELS, MOBILE_STRINGS, pickUiLang, typeLabel, warningLabelKo } from './i18n';

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
	it('labels car warning lights in Korean, falls back to the code', () => {
		expect(warningLabelKo('TPMS')).toBe('타이어 공기압 경고');
		expect(warningLabelKo('LOW_VOLT_BATTERY')).toBe('12V 배터리 경고');
		expect(warningLabelKo('NEW_THING')).toBe('NEW_THING');
		expect(warningLabelKo('TIRE_LOW_FL')).toBe('타이어 저압: 앞 왼쪽');
		expect(warningLabelKo('TIRE_LOW_RR')).toBe('타이어 저압: 뒤 오른쪽');
		expect(warningLabelKo('TPMS_FAULT')).toBe('TPMS 장치 이상 (저압 신호 아님)');
	});
});

describe('REPORT_STRINGS (/r)', async () => {
	const { REPORT_STRINGS, reportError } = await import('./i18n');
	it('has the same keys in every language and an error text for every parse error', () => {
		expect(Object.keys(REPORT_STRINGS.ko).sort()).toEqual(Object.keys(REPORT_STRINGS.en).sort());
		for (const e of ['code_invalid', 'code_expired', 'rate_limited', 'type_required', 'consent_required', 'note_too_long', 'location_invalid', 'too_many_reports']) {
			expect(reportError('ko', e)).not.toBe(REPORT_STRINGS.ko.err_unknown);
		}
		expect(reportError('en', 'whatever')).toBe(REPORT_STRINGS.en.err_unknown);
	});
});

describe('ja / zh (RENTCARE-31, unreviewed drafts)', async () => {
	const m = await import('./i18n');
	it('has every string in every UI language', () => {
		expect(m.UI_LANGS).toEqual(['ko', 'en', 'ja', 'zh']);
		for (const lang of m.UI_LANGS) {
			for (const t of m.INCIDENT_TYPES) expect(m.INCIDENT_TYPE_LABELS[lang][t]).toBeTruthy();
			expect(Object.keys(m.MOBILE_STRINGS[lang]).sort()).toEqual(Object.keys(m.MOBILE_STRINGS.en).sort());
			expect(Object.keys(m.REPORT_STRINGS[lang]).sort()).toEqual(Object.keys(m.REPORT_STRINGS.en).sort());
			for (const v of [...Object.values(m.MOBILE_STRINGS[lang]), ...Object.values(m.REPORT_STRINGS[lang])]) expect(v.trim()).not.toBe('');
		}
	});
	it('keeps placeholders in translations', () => {
		for (const lang of m.UI_LANGS) {
			expect(m.REPORT_STRINGS[lang].consent).toContain('{vendor}');
			expect(m.REPORT_STRINGS[lang].locationAdded).toContain('{m}');
		}
	});
	it('marks ja / zh as unreviewed and never picks them from Accept-Language', () => {
		expect(m.isReviewedLang('ko')).toBe(true);
		expect(m.isReviewedLang('en')).toBe(true);
		expect(m.isReviewedLang('ja')).toBe(false);
		expect(m.isReviewedLang('zh')).toBe(false);
		expect(m.pickUiLang('ja', 'ko')).toBe('ja'); // explicit link from the car
		expect(m.pickUiLang(null, 'ja-JP,en;q=0.5')).toBe('en');
		expect(m.pickUiLang(null, 'zh-CN,zh;q=0.9')).toBe('en');
	});
	it('accepts ja / zh as report languages', () => {
		expect(m.LANGUAGES).toEqual(['ko', 'en', 'ja', 'zh']);
	});
});
