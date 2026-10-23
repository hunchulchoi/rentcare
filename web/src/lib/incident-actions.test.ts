import { describe, expect, it } from 'vitest';
import { ACTION_TYPES, ACTION_TYPE_LABELS_KO, parseActionForm, statusAfterLog, timeline } from './incident-actions.ts';

const form = (o: Record<string, string>) => {
	const f = new FormData();
	for (const [k, v] of Object.entries(o)) f.set(k, v);
	return f;
};

describe('parseActionForm', () => {
	it('accepts an internal note', () => {
		expect(parseActionForm(form({ kind: 'NOTE', body: '  고객이 내일 반납 희망  ' }))).toEqual({
			ok: true, value: { kind: 'NOTE', actionType: null, body: '고객이 내일 반납 희망' }
		});
	});

	it('accepts an action with a type; body is optional for actions', () => {
		expect(parseActionForm(form({ kind: 'ACTION', actionType: 'TOW', body: '' }))).toEqual({
			ok: true, value: { kind: 'ACTION', actionType: 'TOW', body: null }
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
