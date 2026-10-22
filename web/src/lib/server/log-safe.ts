import { sha256Hex } from './crypto';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const TOKEN = /^[A-Za-z0-9_-]{24,}$/;
const CODE = /^[A-Z0-9]{6}$/;

/** Path with ids, tokens and codes replaced, safe to write to logs. */
export function routeForLog(pathname: string): string {
	return pathname
		.split('/')
		.map((seg) => (UUID.test(seg) ? ':id' : TOKEN.test(seg) ? ':token' : CODE.test(seg) ? ':code' : seg))
		.join('/');
}

/**
 * Error class plus a short hash of the message. Messages can carry user data
 * (DB constraint values, file names), so they are never logged as text.
 */
export function errorId(e: unknown): string {
	const name = e instanceof Error ? e.name : typeof e;
	const msg = e instanceof Error ? e.message : String(e);
	return `${name}#${sha256Hex(msg).slice(0, 8)}`;
}
