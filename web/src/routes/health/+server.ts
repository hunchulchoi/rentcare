import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

// Liveness for Docker healthcheck / Sablier. No DB call, no data.
export const GET: RequestHandler = () => json({ ok: true }, { headers: { 'cache-control': 'no-store' } });
