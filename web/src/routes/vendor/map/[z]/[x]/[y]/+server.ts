import { tileResponse } from '#lib/server/map.ts';
import type { RequestHandler } from './$types';

// Map tile for the vendor incident page. The /vendor hook already requires a staff session.
export const GET: RequestHandler = async ({ params }) => tileResponse(params);
