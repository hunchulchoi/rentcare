import { isUiLang } from '#lib/i18n.ts';

/**
 * Plain phone address shown as QR on the car. Only the UI language may ride along, so the phone
 * opens in the user's language before the attach code is typed. Never a code or token.
 */
export function attachUrl(base: string, language: string): string {
	return phoneUrl(base, '/m', language);
}

/** Phone basic report page (RENTCARE-13); the rental code is typed, never in the URL. */
export function reportUrl(base: string, language: string): string {
	return phoneUrl(base, '/r', language);
}

function phoneUrl(base: string, path: string, language: string): string {
	const url = `${base.replace(/\/+$/, '')}${path}`;
	return isUiLang(language) ? `${url}?lang=${language}` : url;
}
