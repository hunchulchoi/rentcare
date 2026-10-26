// Count attachment objects with and without server-side encryption. --fix re-writes plain objects in place
// (CopyObject onto itself), which makes the bucket's default encryption apply. Prints counts only.
//   scripts/with-secrets.sh /minio -- sh -c 'cd web && node scripts/s3-sse-audit.ts [--fix]'
import { CopyObjectCommand, HeadObjectCommand, ListObjectsV2Command, S3Client } from '@aws-sdk/client-s3';
import { s3Settings } from '../src/lib/server/storage-config.ts';

const s = s3Settings(process.env)!;
const c = new S3Client({ region: s.region, endpoint: s.endpoint, forcePathStyle: s.forcePathStyle });
const fix = process.argv.includes('--fix');
let token: string | undefined;
let enc = 0, plain = 0, fixed = 0;
do {
	const page = await c.send(new ListObjectsV2Command({ Bucket: s.bucket, Prefix: 'attachments/', ContinuationToken: token }));
	for (const o of page.Contents ?? []) {
		const h = await c.send(new HeadObjectCommand({ Bucket: s.bucket, Key: o.Key! }));
		if (h.ServerSideEncryption) { enc++; continue; }
		plain++;
		if (fix) {
			await c.send(new CopyObjectCommand({
				Bucket: s.bucket, Key: o.Key!, CopySource: `${s.bucket}/${o.Key}`,
				MetadataDirective: 'REPLACE', ContentType: h.ContentType ?? 'application/octet-stream',
				ServerSideEncryption: 'AES256'
			}));
			fixed++;
		}
	}
	token = page.IsTruncated ? page.NextContinuationToken : undefined;
} while (token);
console.log(`encrypted=${enc} plain=${plain}${fix ? ` re-encrypted=${fixed}` : ''}`);
