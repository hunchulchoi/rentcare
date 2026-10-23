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

export type ActionInput = { kind: ActionKind; actionType: ActionType | null; body: string | null };

export type ActionRow = {
	id: string;
	kind: ActionKind;
	action_type: ActionType | null;
	body: string | null;
	staff: string | null;
	created_at: Date;
};

const isActionType = (v: unknown): v is ActionType => typeof v === 'string' && (ACTION_TYPES as readonly string[]).includes(v);

/** Notes need text; actions need a type (text optional). */
export function parseActionForm(f: FormData): { ok: true; value: ActionInput } | { ok: false; error: string } {
	const kind = f.get('kind');
	const raw = f.get('body');
	const body = typeof raw === 'string' ? raw.trim() : '';
	if (body.length > ACTION_BODY_MAX) return { ok: false, error: 'too_long' };
	if (kind === 'NOTE') {
		if (!body) return { ok: false, error: 'empty' };
		return { ok: true, value: { kind, actionType: null, body } };
	}
	if (kind === 'ACTION') {
		const t = f.get('actionType');
		if (!isActionType(t)) return { ok: false, error: 'action_type' };
		return { ok: true, value: { kind, actionType: t, body: body || null } };
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
