// Test helper: move one DEMO incident's receive time into the past (retention check). Refuses non-demo rows.
//   scripts/with-secrets.sh /postgres,/minio -- sh -c 'cd web && node scripts/backdate-incident.ts <receiptNo> <days> [--check-object]'
import { HeadObjectCommand, S3Client } from '@aws-sdk/client-s3';
import postgres from 'postgres';
import { dbSslOptions } from '../src/lib/server/db-ssl.ts';
import { objectKey, s3Settings } from '../src/lib/server/storage-config.ts';

const [receipt, daysArg] = process.argv.slice(2);
const url = process.env.DATABASE_URL!;
const sql = postgres(url, { max: 1, onnotice: () => {}, ssl: dbSslOptions(url, process.env.DATABASE_SSL, process.env.DATABASE_SSL_CA) });
try {
	if (process.argv.includes('--check-object')) {
		// prints: rows=<n> objects=<present count>
		const rows = await sql<{ storage_name: string }[]>`select a.storage_name from attachments a join incidents i on i.id = a.incident_id where i.receipt_no = ${receipt}`;
		const keys = (process.argv[process.argv.indexOf('--check-object') + 1] ?? '').split(',').filter(Boolean);
		const s = s3Settings(process.env)!;
		const c = new S3Client({ region: s.region, endpoint: s.endpoint, forcePathStyle: s.forcePathStyle });
		let present = 0;
		for (const k of keys) present += await c.send(new HeadObjectCommand({ Bucket: s.bucket, Key: objectKey(k) })).then(() => 1, () => 0);
		const inc = await sql`select 1 from incidents where receipt_no = ${receipt}`;
		console.log(`incident=${inc.length} attachments=${rows.length} objects=${present}/${keys.length}`);
	} else {
		const r = await sql`update incidents set created_at = now() - (${Number(daysArg)} || ' days')::interval
			where receipt_no = ${receipt} and demo = true returning (select string_agg(storage_name, ',') from attachments where incident_id = incidents.id) as keys`;
		console.log(r.length ? `backdated keys=${r[0].keys ?? ''}` : 'not found or not demo');
	}
} finally {
	await sql.end();
}
