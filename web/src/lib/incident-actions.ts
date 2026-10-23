// Vendor-side log on an incident: internal notes and actions taken. Never sent to the car or the renter
// (the car only sees the status: an action moves it to IN_PROGRESS).
import type { IncidentStatus } from './i18n.ts';

export const ACTION_TYPES = ['CALL', 'DISPATCH', 'TOW', 'REPLACEMENT', 'REPAIR', 'OTHER'] as const;
export type ActionType = (typeof ACTION_TYPES)[number];
export type ActionKind = 'NOTE' | 'ACTION';

export const ACTION_TYPE_LABELS_KO: Record<ActionType, string> = {
	CALL: '고객 통화',
	DISPATCH: '현장 출동 요청',
	TOW: '견인 요청',
	REPLACEMENT: '대체 차량 배정',
	REPAIR: '정비 접수',
	OTHER: '기타 조치'
};

export const ACTION_BODY_MAX = 2000;

export type ActionInput = { kind: ActionKind; actionType: ActionType | null; body: string | null; expectedAt: Date | null };

const MAX_AHEAD_MS = 7 * 24 * 3600 * 1000;
const KST_LOCAL = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/;

/**
 * Vendor's expected time for an action: `expectedAt` (datetime-local, read as KST) or `expectedIn` (minutes).
 * Must be in the future and within 7 days. undefined = not given, null = invalid.
 */
function parseExpected(f: FormData, now: Date): Date | null | undefined {
	const at = f.get('expectedAt');
	const inMin = f.get('expectedIn');
	let d: Date | undefined;
	if (typeof inMin === 'string' && inMin.trim()) {
		if (!/^\d{1,5}$/.test(inMin.trim())) return null;
		d = new Date(now.getTime() + Number(inMin.trim()) * 60_000);
	} else if (typeof at === 'string' && at.trim()) {
		const m = KST_LOCAL.exec(at.trim());
		if (!m) return null;
		d = new Date(Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4] - 9, +m[5]));
	} else {
		return undefined;
	}
	const ahead = d.getTime() - now.getTime();
	return ahead > 0 && ahead <= MAX_AHEAD_MS ? d : null;
}

export type ActionRow = {
	id: string;
	kind: ActionKind;
	action_type: ActionType | null;
	body: string | null;
	staff: string | null;
	expected_at?: Date | null;
	created_at: Date;
};

const isActionType = (v: unknown): v is ActionType => typeof v === 'string' && (ACTION_TYPES as readonly string[]).includes(v);

/** Notes need text; actions need a type (text optional). */
export function parseActionForm(f: FormData, now = new Date()): { ok: true; value: ActionInput } | { ok: false; error: string } {
	const kind = f.get('kind');
	const raw = f.get('body');
	const body = typeof raw === 'string' ? raw.trim() : '';
	if (body.length > ACTION_BODY_MAX) return { ok: false, error: 'too_long' };
	const expected = parseExpected(f, now);
	if (kind === 'NOTE') {
		if (!body) return { ok: false, error: 'empty' };
		if (expected !== undefined) return { ok: false, error: 'expected_on_note' };
		return { ok: true, value: { kind, actionType: null, body, expectedAt: null } };
	}
	if (kind === 'ACTION') {
		const t = f.get('actionType');
		if (!isActionType(t)) return { ok: false, error: 'action_type' };
		if (expected === null) return { ok: false, error: 'expected' };
		return { ok: true, value: { kind, actionType: t, body: body || null, expectedAt: expected ?? null } };
	}
	return { ok: false, error: 'kind' };
}

/** Status after a log entry: the first action starts handling; notes never change it; done stays done. */
export function statusAfterLog(current: IncidentStatus, kind: ActionKind): IncidentStatus {
	if (kind === 'NOTE') return current;
	return current === 'RECEIVED' || current === 'ACKNOWLEDGED' ? 'IN_PROGRESS' : current;
}

export type TimelineEntry =
	| { kind: 'REPORTED'; at: Date }
	| { kind: 'ACKNOWLEDGED'; at: Date; staff: string | null }
	| { kind: 'RESOLVED'; at: Date; staff: string | null }
	| ({ kind: ActionKind; at: Date } & Omit<ActionRow, 'kind' | 'created_at'>);

/** One chronological history for the detail page. */
export function timeline(
	incident: { created_at: Date; acknowledged_at: Date | null; acknowledged_by: string | null; resolved_at?: Date | null; resolved_by?: string | null },
	rows: ActionRow[]
): TimelineEntry[] {
	const out: TimelineEntry[] = [{ kind: 'REPORTED', at: new Date(incident.created_at) }];
	if (incident.acknowledged_at) out.push({ kind: 'ACKNOWLEDGED', at: new Date(incident.acknowledged_at), staff: incident.acknowledged_by });
	if (incident.resolved_at) out.push({ kind: 'RESOLVED', at: new Date(incident.resolved_at), staff: incident.resolved_by ?? null });
	for (const r of rows) {
		const { created_at, kind, ...rest } = r;
		out.push({ kind, at: new Date(created_at), ...rest });
	}
	return out.sort((a, b) => a.at.getTime() - b.at.getTime());
}
