import { json } from '@sveltejs/kit';
import { probe } from '#lib/readiness.ts';
import { config } from '#lib/server/config.ts';
import { db } from '#lib/server/db.ts';
import type { RequestHandler } from './$types';

// Readiness for the demo preflight (scripts/demo-preflight.sh): the app is up AND the database answers.
// Under /api/ so Sablier holds the request while the container wakes. No data, no error details.
export const GET: RequestHandler = async () => {
	const database = config.databaseUrl ? await probe(() => db()`select 1`, 5000) : ({ ok: false, error: 'failed' } as const);
	return json({ ok: database.ok, database }, { status: database.ok ? 200 : 503, headers: { 'cache-control': 'no-store' } });
};
