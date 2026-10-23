// Smoke test for attachment object storage: put, head, get, delete one throwaway object.
//   scripts/with-secrets.sh /minio -- sh -c 'cd web && node scripts/s3-check.ts'
import { DeleteObjectCommand, GetObjectCommand, HeadObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { randomBytes } from 'node:crypto';
import { s3Settings } from '../src/lib/server/storage-config.ts';

const s = s3Settings(process.env);
if (!s) throw new Error('S3_BUCKET is not set');
const c = new S3Client({ region: s.region, endpoint: s.endpoint, forcePathStyle: s.forcePathStyle });
const Key = `healthcheck/${randomBytes(8).toString('hex')}`;
const body = randomBytes(1024);
await c.send(new PutObjectCommand({ Bucket: s.bucket, Key, Body: body, ContentLength: body.length }));
const head = await c.send(new HeadObjectCommand({ Bucket: s.bucket, Key }));
const got = Buffer.from(await (await c.send(new GetObjectCommand({ Bucket: s.bucket, Key }))).Body!.transformToByteArray());
await c.send(new DeleteObjectCommand({ Bucket: s.bucket, Key }));
console.log(`bucket=${s.bucket} put/head/get/delete ok, size=${head.ContentLength}, same=${got.equals(body)}, sse=${head.ServerSideEncryption ?? 'none'}`);
