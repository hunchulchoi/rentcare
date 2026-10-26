import { describe, expect, it } from 'vitest';
import { probe } from './readiness.ts';

describe('probe', () => {
	it('is ok when the check resolves in time', async () => {
		expect(await probe(async () => {}, 100)).toEqual({ ok: true });
	});
	it('fails without leaking the error text', async () => {
		expect(await probe(async () => { throw new Error('password=secret'); }, 100)).toEqual({ ok: false, error: 'failed' });
	});
	it('fails on timeout', async () => {
		expect(await probe(() => new Promise(() => {}), 20)).toEqual({ ok: false, error: 'timeout' });
	});
});
