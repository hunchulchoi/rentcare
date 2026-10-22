// Apply db/schema.sql to DATABASE_URL (idempotent) and sync the demo vendor's staff token hash.
// Usage: pnpm db:migrate   (runs with Node's built-in TypeScript support and --env-file-if-exists=.env)
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import postgres from 'postgres';
import { dbSsl } from '../src/lib/server/db-ssl.ts';

const url = process.env.DATABASE_URL;
if (!url) {
	console.error('DATABASE_URL is not set');
	process.exit(1);
}

const sql = postgres(url, { max: 1, onnotice: () => {}, ssl: dbSsl(url, process.env.DATABASE_SSL) });
try {
	const schema = await readFile(new URL('../db/schema.sql', import.meta.url), 'utf8');
	await sql.unsafe(schema);
	console.log('schema applied');

	const token = process.env.DEMO_VENDOR_TOKEN?.trim();
	if (token) {
		const hash = createHash('sha256').update(token, 'utf8').digest('hex');
		await sql`
			insert into vendors (id, name, staff_token_hash) values ('demo-jeju', '제주 데모렌터카', ${hash})
			on conflict (id) do update set staff_token_hash = excluded.staff_token_hash`;
		console.log('demo vendor token synced');
	} else {
		console.log('DEMO_VENDOR_TOKEN not set; demo vendor login stays disabled');
	}
} finally {
	await sql.end();
}
