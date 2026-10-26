// Shared constants and UI strings. Safe to import from client and server code.

export const INCIDENT_TYPES = ['NO_START', 'TIRE', 'WARNING_LIGHT', 'ACCIDENT', 'OTHER'] as const;
export type IncidentType = (typeof INCIDENT_TYPES)[number];

/** Languages a report can be filed in (contract v0). `ja`/`zh` come later. */
export const LANGUAGES = ['ko', 'en'] as const;
export type Language = (typeof LANGUAGES)[number];

/** Languages the web UI itself is translated to. */
export const UI_LANGS = ['ko', 'en'] as const;
export type UiLang = (typeof UI_LANGS)[number];

export const LOCATION_SOURCES = ['VEHICLE', 'MANUAL', 'MOBILE'] as const;
export type LocationSource = (typeof LOCATION_SOURCES)[number];

export const STATUSES = ['RECEIVED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED'] as const;
export type IncidentStatus = (typeof STATUSES)[number];

export const INCIDENT_TYPE_LABELS: Record<UiLang, Record<IncidentType, string>> = {
	ko: {
		NO_START: '시동 불가',
		TIRE: '타이어 이상',
		WARNING_LIGHT: '경고등',
		ACCIDENT: '사고',
		OTHER: '기타'
	},
	en: {
		NO_START: "Car won't start",
		TIRE: 'Tire problem',
		WARNING_LIGHT: 'Warning light',
		ACCIDENT: 'Accident',
		OTHER: 'Other'
	}
};

/** Korean names of user languages, for vendor screens. */
export const LANGUAGE_NAMES_KO: Record<string, string> = {
	ko: '한국어',
	en: '영어',
	ja: '일본어',
	zh: '중국어'
};

export const STATUS_LABELS_KO: Record<IncidentStatus, string> = {
	RECEIVED: '접수됨',
	ACKNOWLEDGED: '확인함',
	IN_PROGRESS: '조치중',
	RESOLVED: '조치완료'
};

export const LOCATION_SOURCE_LABELS_KO: Record<LocationSource, string> = {
	VEHICLE: '차량 GPS',
	MANUAL: '직접 입력',
	MOBILE: '휴대폰'
};

export function isUiLang(v: unknown): v is UiLang {
	return typeof v === 'string' && (UI_LANGS as readonly string[]).includes(v);
}

export function typeLabel(type: string, lang: UiLang = 'ko'): string {
	return (INCIDENT_TYPE_LABELS[lang] as Record<string, string>)[type] ?? type;
}

/** Pick a UI language from an explicit value, then Accept-Language, defaulting to English. */
export function pickUiLang(explicit: string | null | undefined, acceptLanguage?: string | null): UiLang {
	if (isUiLang(explicit)) return explicit;
	for (const part of (acceptLanguage ?? '').split(',')) {
		const tag = part.split(';')[0].trim().toLowerCase().split('-')[0];
		if (isUiLang(tag)) return tag;
	}
	return 'en';
}

/** Mobile attach page strings. Keys must exist in every language. */
const mobile = {
	ko: {
		title: '사진·영상 추가',
		intro: '차량 화면에 표시된 6자리 첨부 코드를 입력하세요.',
		retention: '보낸 사진·영상은 이 접수 처리에만 쓰이며 접수일로부터 90일 후 삭제됩니다.',
		locationTitle: '현재 위치 보내기',
		locationNote: '차량이 위치를 보내지 못했을 때만 씁니다. 차량 위치가 있으면 바뀌지 않습니다.',
		locationButton: '이 휴대폰의 현재 위치 보내기',
		locationSaving: '위치 확인 중…',
		locationSaved: '위치를 보냈습니다. 업체 화면에 "휴대폰" 위치로 표시됩니다.',
		locationKept: '차량 위치가 이미 있어 그대로 두었습니다.',
		locationDenied: '위치를 가져오지 못했습니다. 브라우저의 위치 권한을 확인하세요.',
		locationFailed: '위치를 보내지 못했습니다. 다시 시도하세요.',
		codeLabel: '첨부 코드',
		codePlaceholder: '예: K7Q2M9',
		verify: '확인',
		verifying: '확인 중…',
		receivedNotice:
			'기본 신고는 이미 접수되었습니다. 사진·영상 업로드는 선택 사항이며, 업로드에 실패해도 접수에는 영향이 없습니다.',
		receiptNo: '접수 번호',
		type: '신고 유형',
		chooseFiles: '사진·영상 선택',
		limits: '사진(JPEG, PNG, HEIC), 영상(MP4, MOV) · 파일당 최대 50MB · 신고당 최대 10개',
		upload: '업로드',
		uploading: '업로드 중…',
		retry: '다시 시도',
		alreadyAttached: '이미 첨부된 파일',
		statusWaiting: '대기',
		statusUploading: '업로드 중',
		statusDone: '완료',
		statusFailed: '실패',
		allDone: '업로드가 끝났습니다. 이 창을 닫아도 됩니다.',
		noscript: '파일 업로드에는 JavaScript가 필요합니다.',
		otherCode: '다른 코드 입력',
		err_code_invalid: '코드가 올바르지 않습니다. 차량 화면의 코드를 다시 확인하세요.',
		err_code_expired: '코드가 만료되었습니다. 차량 화면에서 새 코드를 받으세요.',
		err_rate_limited: '시도 횟수가 너무 많습니다. 잠시 후 다시 시도하세요.',
		err_too_large: '파일이 너무 큽니다(최대 50MB).',
		err_unsupported_type: '지원하지 않는 파일 형식입니다.',
		err_too_many_files: '이 신고에는 더 이상 파일을 추가할 수 없습니다(최대 10개).',
		err_network: '네트워크 오류입니다. 다시 시도하세요.',
		err_unknown: '업로드하지 못했습니다. 다시 시도하세요.'
	},
	en: {
		title: 'Add photos & videos',
		intro: 'Enter the 6-character attach code shown on the car screen.',
		retention: 'Photos and videos are used only for this report and deleted 90 days after it.',
		locationTitle: 'Send your current location',
		locationNote: 'Only used when the car could not send its location. A car location is never replaced.',
		locationButton: 'Send this phone\'s location',
		locationSaving: 'Getting location…',
		locationSaved: 'Location sent. The rental company sees it as a phone location.',
		locationKept: 'The car location is already there, so it was kept.',
		locationDenied: 'Could not get the location. Check the browser\'s location permission.',
		locationFailed: 'Could not send the location. Please try again.',
		codeLabel: 'Attach code',
		codePlaceholder: 'e.g. K7Q2M9',
		verify: 'Continue',
		verifying: 'Checking…',
		receivedNotice:
			'Your basic report has already been received. Adding photos or videos is optional — if an upload fails, your report is still received.',
		receiptNo: 'Receipt number',
		type: 'Report type',
		chooseFiles: 'Choose photos / videos',
		limits: 'Photos (JPEG, PNG, HEIC), videos (MP4, MOV) · up to 50 MB each · up to 10 files per report',
		upload: 'Upload',
		uploading: 'Uploading…',
		retry: 'Retry',
		alreadyAttached: 'Already attached',
		statusWaiting: 'Waiting',
		statusUploading: 'Uploading',
		statusDone: 'Done',
		statusFailed: 'Failed',
		allDone: 'Upload finished. You can close this page.',
		noscript: 'JavaScript is required to upload files.',
		otherCode: 'Enter a different code',
		err_code_invalid: 'That code is not valid. Please check the code on the car screen.',
		err_code_expired: 'This code has expired. Please get a new code on the car screen.',
		err_rate_limited: 'Too many attempts. Please wait a few minutes and try again.',
		err_too_large: 'The file is too large (max 50 MB).',
		err_unsupported_type: 'This file type is not supported.',
		err_too_many_files: 'No more files can be added to this report (max 10).',
		err_network: 'Network error. Please try again.',
		err_unknown: 'Could not upload. Please try again.'
	}
} satisfies Record<UiLang, Record<string, string>>;

export type MobileStrings = (typeof mobile)['en'];
export const MOBILE_STRINGS: Record<UiLang, MobileStrings> = mobile;

export function mobileError(lang: UiLang, code: string | undefined): string {
	const key = `err_${code ?? 'unknown'}` as keyof MobileStrings;
	return MOBILE_STRINGS[lang][key] ?? MOBILE_STRINGS[lang].err_unknown;
}

/** Korean names of car warning lights (Pleos TelltaleType) for vendor screens. */
const WARNING_LABELS_KO: Record<string, string> = {
	TPMS: '타이어 공기압 경고',
	// Derived by the car from per-wheel data (TireCheck), not dashboard lamps.
	TPMS_FAULT: 'TPMS 장치 이상 (저압 신호 아님)',
	TIRE_LOW_FL: '타이어 저압: 앞 왼쪽',
	TIRE_LOW_FR: '타이어 저압: 앞 오른쪽',
	TIRE_LOW_RL: '타이어 저압: 뒤 왼쪽',
	TIRE_LOW_RR: '타이어 저압: 뒤 오른쪽',
	LOW_VOLT_BATTERY: '12V 배터리 경고',
	ENGINE: '엔진 경고',
	ENGINE_OIL_PRESSURE: '엔진오일 압력 경고',
	ENGINE_TEMPERATURE: '엔진 과열 경고',
	MASTER_WARNING: '마스터 경고',
	ABS: 'ABS 경고',
	ESC: '차체자세제어(ESC) 경고',
	MDPS: '전동 조향(MDPS) 경고',
	AIRBAG: '에어백 경고',
	EV_SYSTEM: '전기차 시스템 경고',
	HEV_SYSTEM: '하이브리드 시스템 경고',
	POWER_DOWN: '출력 제한',
	LOW_EV_BATTERY: '구동 배터리 부족',
	LOW_FUEL: '연료 부족',
	DOOR_OPEN: '문 열림',
	TRUNK_OPEN: '트렁크 열림',
	IMMOBILIZER: '이모빌라이저 경고'
};

export function warningLabelKo(code: string): string {
	return WARNING_LABELS_KO[code] ?? code;
}
