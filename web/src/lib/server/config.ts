import { DATABASE_URL, DATABASE_SSL, DATABASE_SSL_CA, DEMO_VENDOR_TOKEN, PUBLIC_BASE_URL, STORAGE_DIR,
	S3_BUCKET, AWS_ENDPOINT_URL_S3, MAP_TILE_URL, RETENTION_DAYS, S3_FORCE_PATH_STYLE, AWS_REGION } from '$app/env/private';
import { s3Settings } from './storage-config.ts';
import { retentionDays } from '../retention.ts';
import path from 'node:path';

export const config = {
	databaseUrl: DATABASE_URL,
	databaseSsl: DATABASE_SSL,
	databaseSslCa: DATABASE_SSL_CA,
	demoVendorToken: DEMO_VENDOR_TOKEN,
	publicBaseUrl: PUBLIC_BASE_URL?.replace(/\/+$/, ''),
	mapTileUrl: MAP_TILE_URL ?? 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
	retentionDays: retentionDays(RETENTION_DAYS),
	storageDir: path.resolve(STORAGE_DIR ?? './storage'),
	s3: s3Settings({ S3_BUCKET, AWS_ENDPOINT_URL_S3, S3_FORCE_PATH_STYLE, AWS_REGION })
};
