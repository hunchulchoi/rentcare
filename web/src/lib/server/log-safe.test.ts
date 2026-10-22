import { describe, expect, it } from 'vitest';
import { errorId, routeForLog } from './log-safe';

describe('safe logging', () => {
	it('replaces ids in paths with placeholders', () => {
		expect(routeForLog('/api/incidents/6f1c0000-0000-4000-8000-000000000000/attachments')).toBe('/api/incidents/:id/attachments');
		expect(routeForLog('/vendor/files/bdff2c4b-7b7b-4503-9029-b7760385ae80')).toBe('/vendor/files/:id');
		expect(routeForLog('/m')).toBe('/m');
	});
	it('drops anything that looks like a token or code segment', () => {
		expect(routeForLog('/x/abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ')).toBe('/x/:token');
		expect(routeForLog('/x/K7Q2M9')).toBe('/x/:code');
	});
	it('logs an error class and a stable id, never the message', () => {
		const e = new Error('duplicate key value: plate=12하3456');
		const line = errorId(e);
		expect(line).toMatch(/^Error#[0-9a-f]{8}$/);
		expect(line).not.toContain('12하3456');
		expect(errorId(e)).toBe(line);
	});
});
