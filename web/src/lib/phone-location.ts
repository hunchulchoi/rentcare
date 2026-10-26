// Phone location fallback (RENTCARE-10): when the car could not give a location, the renter's phone can.
import type { LocationSource } from './i18n.ts';

export const MAX_ACCURACY_M = 5000;

/** The car GPS and a location the renter picked on the car map always win over the phone. */
export function mayPhoneSetLocation(current: LocationSource | null): boolean {
	return current === null || current === 'MOBILE';
}

export type PhoneLocation = { latitude: number; longitude: number; accuracyM: number };

export function parsePhoneLocation(f: FormData): { ok: true; value: PhoneLocation } | { ok: false } {
	const n = (k: string) => {
		const v = f.get(k);
		return typeof v === 'string' && v.trim() !== '' && Number.isFinite(Number(v)) ? Number(v) : NaN;
	};
	const latitude = n('lat'), longitude = n('lon'), accuracyM = n('accuracy');
	if (!(latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180)) return { ok: false };
	if (!(accuracyM >= 0 && accuracyM <= MAX_ACCURACY_M)) return { ok: false };
	return { ok: true, value: { latitude, longitude, accuracyM } };
}
