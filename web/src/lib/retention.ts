// How long a report is kept (docs/privacy.md). Shared by server purge and the notices on every screen.

export const DEFAULT_RETENTION_DAYS = 90;
const DAY_MS = 24 * 3600 * 1000;

/** RETENTION_DAYS env: whole days 1..3650, otherwise the default. */
export function retentionDays(raw: string | undefined): number {
	if (!raw || !/^\d+$/.test(raw.trim())) return DEFAULT_RETENTION_DAYS;
	const n = Number(raw.trim());
	return n >= 1 && n <= 3650 ? n : DEFAULT_RETENTION_DAYS;
}

export function deleteAfter(receivedAt: Date, days: number): Date {
	return new Date(new Date(receivedAt).getTime() + days * DAY_MS);
}

/** Reports received before this instant are past their retention. */
export function retentionCutoff(now: Date, days: number): Date {
	return new Date(now.getTime() - days * DAY_MS);
}
