// Receipt numbers: RC-YYYYMMDD-NNNN, numbered per calendar day in Korea (Asia/Seoul, UTC+9, no DST).

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

/** 'YYYYMMDD' of the given instant in KST. */
export function kstDayKey(date: Date): string {
	return new Date(date.getTime() + KST_OFFSET_MS).toISOString().slice(0, 10).replaceAll('-', '');
}

/** Format a receipt number. Sequence is zero-padded to 4 digits (wider if it ever exceeds 9999). */
export function formatReceiptNo(dayKey: string, seq: number): string {
	if (!/^\d{8}$/.test(dayKey)) throw new Error('invalid day key');
	if (!Number.isInteger(seq) || seq < 1) throw new Error('invalid sequence');
	return `RC-${dayKey}-${String(seq).padStart(4, '0')}`;
}

/** ISO-8601 UTC without milliseconds, e.g. 2026-10-22T03:10:00Z (matches the API contract). */
export function isoZ(date: Date): string {
	return date.toISOString().replace(/\.\d{3}Z$/, 'Z');
}
