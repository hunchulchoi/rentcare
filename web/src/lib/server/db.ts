import postgres from 'postgres';
import { config } from './config';
import { sha256Hex } from './crypto';
import { dbSslOptions } from './db-ssl';

let client: postgres.Sql | undefined;

export function db(): postgres.Sql {
	if (!client) {
		if (!config.databaseUrl) throw new Error('DATABASE_URL is not set');
		client = postgres(config.databaseUrl, {
			max: 10,
			onnotice: () => {},
			ssl: dbSslOptions(config.databaseUrl, config.databaseSsl, config.databaseSslCa)
		});
	}
	return client;
}

/** Close the pool so the process can exit on shutdown (otherwise idle connections keep it alive). */
export async function closeDb(): Promise<void> {
	const c = client;
	client = undefined;
	await c?.end({ timeout: 5 });
}

export const DEMO_VENDOR_ID = 'demo-jeju';
export const DEMO_VENDOR_NAME = '제주 데모렌터카';

let demoSynced: Promise<void> | undefined;

/** Make sure demo-jeju exists and its staff token hash matches DEMO_VENDOR_TOKEN (once per process). */
export function syncDemoVendor(): Promise<void> {
	if (!config.demoVendorToken) return Promise.resolve();
	demoSynced ??= (async () => {
		const hash = sha256Hex(config.demoVendorToken!);
		await db()`
			insert into vendors (id, name, staff_token_hash)
			values (${DEMO_VENDOR_ID}, ${DEMO_VENDOR_NAME}, ${hash})
			on conflict (id) do update set staff_token_hash = excluded.staff_token_hash`;
	})().catch((e) => {
		demoSynced = undefined;
		throw e;
	});
	return demoSynced;
}
