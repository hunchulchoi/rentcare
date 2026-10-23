import { describe, expect, it } from 'vitest';
import { ACTION_TYPES, ACTION_TYPE_LABELS_KO, parseActionForm, statusAfterLog, timeline } from './incident-actions.ts';

const form = (o: Record<string, string | undefined>) => {
	const f = new FormData();
	for (const [k, v] of Object.entries(o)) if (v !== undefined) f.set(k, v);
	return f;
};

describe('parseActionForm', () => {
	it('accepts an internal note', () => {
		expect(parseActionForm(form({ kind: 'NOTE', body: '  고객이 내일 반납 희망  ' }))).toEqual({
			ok: true, value: { kind: 'NOTE', actionType: null, body: '고객이 내일 반납 희망', expectedAt: null }
		});
	});

	it('accepts an action with a type; body is optional for actions', () => {
		expect(parseActionForm(form({ kind: 'ACTION', actionType: 'TOW', body: '' }))).toEqual({
			ok: true, value: { kind: 'ACTION', actionType: 'TOW', body: null, expectedAt: null }
		});
	});

	it('rejects empty notes, unknown kinds/types, and overlong text', () => {
		expect(parseActionForm(form({ kind: 'NOTE', body: '   ' })).ok).toBe(false);
		expect(parseActionForm(form({ kind: 'ACTION', actionType: 'LAUNCH', body: 'x' })).ok).toBe(false);
		expect(parseActionForm(form({ kind: 'ACTION', body: 'x' })).ok).toBe(false);
		expect(parseActionForm(form({ kind: 'DELETE', body: 'x' })).ok).toBe(false);
		expect(parseActionForm(form({ kind: 'NOTE', body: 'a'.repeat(2001) })).ok).toBe(false);
	});

	it('has a Korean label for every action type', () => {
		for (const t of ACTION_TYPES) expect(ACTION_TYPE_LABELS_KO[t]).toBeTruthy();
	});
});

describe('expected time (vendor estimate)', () => {
	const now = new Date('2026-10-23T05:00:00Z'); // 14:00 KST
	it('takes datetime-local in KST, or minutes from now', () => {
		const a = parseActionForm(form({ kind: 'ACTION', actionType: 'TOW', expectedAt: '2026-10-23T15:30' }), now);
		expect(a.ok && a.value.expectedAt?.toISOString()).toBe('2026-10-23T06:30:00.000Z');
		const b = parseActionForm(form({ kind: 'ACTION', actionType: 'TOW', expectedIn: '45' }), now);
		expect(b.ok && b.value.expectedAt?.toISOString()).toBe('2026-10-23T05:45:00.000Z');
		const c = parseActionForm(form({ kind: 'ACTION', actionType: 'CALL' }), now);
		expect(c.ok && c.value.expectedAt).toBe(null);
	});
	it('rejects past, more than 7 days ahead, garbage, and on notes', () => {
		for (const f of [
			{ kind: 'ACTION', actionType: 'TOW', expectedAt: '2026-10-23T13:00' },
			{ kind: 'ACTION', actionType: 'TOW', expectedAt: '2026-11-05T10:00' },
			{ kind: 'ACTION', actionType: 'TOW', expectedAt: 'soon' },
			{ kind: 'ACTION', actionType: 'TOW', expectedIn: '-5' },
			{ kind: 'NOTE', body: 'x', expectedIn: '30' }
		]) expect(parseActionForm(form(f), now).ok, JSON.stringify(f)).toBe(false);
	});
});

describe('statusAfterLog', () => {
	it('an action starts handling; a note never changes the status; done stays done', () => {
		expect(statusAfterLog('RECEIVED', 'ACTION')).toBe('IN_PROGRESS');
		expect(statusAfterLog('ACKNOWLEDGED', 'ACTION')).toBe('IN_PROGRESS');
		expect(statusAfterLog('IN_PROGRESS', 'ACTION')).toBe('IN_PROGRESS');
		expect(statusAfterLog('RESOLVED', 'ACTION')).toBe('RESOLVED');
		for (const s of ['RECEIVED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED'] as const) expect(statusAfterLog(s, 'NOTE')).toBe(s);
	});
});

describe('timeline', () => {
	it('merges report, staff check and log entries, oldest first', () => {
		const t = timeline(
			{ created_at: new Date('2026-10-23T01:00:00Z'), acknowledged_at: new Date('2026-10-23T01:05:00Z'), acknowledged_by: '김',
				resolved_at: new Date('2026-10-23T02:00:00Z'), resolved_by: '박' },
			[{ id: 'x', kind: 'ACTION', action_type: 'CALL', body: '통화함', staff: '이', created_at: new Date('2026-10-23T01:03:00Z') }]
		);
		expect(t.map((e) => e.kind)).toEqual(['REPORTED', 'ACTION', 'ACKNOWLEDGED', 'RESOLVED']);
	});
});
