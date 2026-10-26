// Test helper: create or remove a second demo vendor for cross-vendor access checks.
//   scripts/with-secrets.sh /postgres -- sh -c 'cd web && node scripts/test-vendor.ts create <token> | remove'
import { createHash } from 'node:crypto';
import postgres from 'postgres';
import { dbSslOptions } from '../src/lib/server/db-ssl.ts';

const url = process.env.DATABASE_URL!;
const sql = postgres(url, { max: 1, onnotice: () => {}, ssl: dbSslOptions(url, process.env.DATABASE_SSL, process.env.DATABASE_SSL_CA) });
try {
	if (process.argv[2] === 'create') {
		const hash = createHash('sha256').update(process.argv[3], 'utf8').digest('hex');
		await sql`insert into vendors (id, name, staff_token_hash) values ('e2e-other', 'E2E 다른 업체', ${hash})
			on conflict (id) do update set staff_token_hash = excluded.staff_token_hash`;
		console.log('created e2e-other');
	} else {
		await sql`delete from vendors where id = 'e2e-other'`;
		console.log('removed e2e-other');
	}
} finally {
	await sql.end();
}
