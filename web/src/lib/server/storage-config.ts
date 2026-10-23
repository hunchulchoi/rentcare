// Pure settings for attachment storage: S3-compatible object storage (MinIO) when S3_BUCKET is set,
// otherwise the local STORAGE_DIR. Credentials (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY) are read
// by the AWS SDK from the environment and never pass through here.

export type S3Settings = { bucket: string; endpoint: string | undefined; forcePathStyle: boolean; region: string };

type Env = Partial<Record<'S3_BUCKET' | 'AWS_ENDPOINT_URL_S3' | 'S3_FORCE_PATH_STYLE' | 'AWS_REGION', string>>;

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '[::1]']);

export function s3Settings(env: Env): S3Settings | null {
	const bucket = env.S3_BUCKET?.trim();
	if (!bucket) return null;
	const endpoint = env.AWS_ENDPOINT_URL_S3?.trim() || undefined;
	if (endpoint) {
		const u = new URL(endpoint);
		// Attachments are personal data: plain HTTP only to a storage server on this machine.
		if (u.protocol !== 'https:' && !(u.protocol === 'http:' && LOCAL_HOSTS.has(u.hostname))) {
			throw new Error('AWS_ENDPOINT_URL_S3 must use https');
		}
	}
	return {
		bucket,
		endpoint,
		forcePathStyle: /^(true|1|yes)$/i.test(env.S3_FORCE_PATH_STYLE?.trim() ?? ''),
		region: env.AWS_REGION?.trim() || 'us-east-1'
	};
}

const STORAGE_NAME = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/[0-9a-f]{32}$/;

/** Object key for a storage name made by saveFile (`<incident uuid>/<32 hex>`). Anything else is refused. */
export function objectKey(storageName: string): string {
	if (!STORAGE_NAME.test(storageName)) throw new Error('invalid storage name');
	return `attachments/${storageName}`;
}
