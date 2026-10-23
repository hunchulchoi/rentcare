// Copy attachments stored under STORAGE_DIR into the S3/MinIO bucket. Local files are kept;
// objects that already exist are skipped. Safe to run again.
//   scripts/with-secrets.sh /postgres,/minio -- sh -c 'cd web && node --env-file-if-exists=.env scripts/storage-to-s3.ts'
import { HeadObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import postgres from 'postgres';
import { dbSslOptions } from '../src/lib/server/db-ssl.ts';
import { objectKey, s3Settings } from '../src/lib/server/storage-config.ts';

const url = process.env.DATABASE_URL;
const s = s3Settings(process.env);
if (!url || !s) throw new Error('DATABASE_URL and S3_BUCKET are required');
const root = path.resolve(process.env.STORAGE_DIR ?? './storage');
const sql = postgres(url, { max: 1, onnotice: () => {}, ssl: dbSslOptions(url, process.env.DATABASE_SSL, process.env.DATABASE_SSL_CA) });
const c = new S3Client({ region: s.region, endpoint: s.endpoint, forcePathStyle: s.forcePathStyle });

let copied = 0, skipped = 0, missing = 0;
try {
	for (const { storage_name } of await sql<{ storage_name: string }[]>`select storage_name from attachments`) {
		const Key = objectKey(storage_name);
		const exists = await c.send(new HeadObjectCommand({ Bucket: s.bucket, Key })).then(() => true, () => false);
		if (exists) { skipped++; continue; }
		const body = await readFile(path.join(root, storage_name)).catch(() => null);
		if (!body) { missing++; continue; }
		await c.send(new PutObjectCommand({ Bucket: s.bucket, Key, Body: body, ContentLength: body.length, ContentType: 'application/octet-stream' }));
		copied++;
	}
} finally {
	await sql.end();
}
console.log(`copied=${copied} already_in_bucket=${skipped} missing_locally=${missing}`);
