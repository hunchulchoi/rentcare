import { defineEnvVars } from '@sveltejs/kit/env';

// All variables are read at runtime (not inlined) and are server-only.
// Schemas tolerate missing values so `vite build` works without secrets; code that needs a
// value checks for it at request time (see src/lib/server/config.ts).
const optional = (value: string | undefined) => (value && value.trim() !== '' ? value : undefined);

export const variables = defineEnvVars({
	DATABASE_URL: { schema: optional, description: 'PostgreSQL connection string' },
	DATABASE_SSL_CA: {
		schema: optional,
		description: 'PEM CA/certificate of the DB server; enables certificate verification'
	},
	DATABASE_SSL: {
		schema: optional,
		description: 'disable | require | verify-full. Default: require for non-local hosts'
	},
	CAR_API_KEY: { schema: optional, description: 'Shared bearer key for the car app (demo)' },
	DEMO_VENDOR_TOKEN: { schema: optional, description: 'Staff login token for vendor demo-jeju' },
	PUBLIC_BASE_URL: {
		schema: optional,
		description: 'Public origin used to build attachUrl, e.g. https://rentcare.example.com'
	},
	S3_BUCKET: {
		schema: optional,
		description: 'Bucket for attachments (MinIO/S3). Set → object storage, unset → STORAGE_DIR'
	},
	AWS_ENDPOINT_URL_S3: { schema: optional, description: 'S3-compatible endpoint, e.g. https://s3.example (https only)' },
	S3_FORCE_PATH_STYLE: { schema: optional, description: 'true for MinIO path-style URLs' },
	AWS_REGION: { schema: optional, description: 'S3 region (MinIO: us-east-1)' },
	MAP_TILE_URL: {
		schema: optional,
		description: 'Raster tile source for the proxy, {z}/{x}/{y}. Default OpenStreetMap (demo use only)'
	},
	STORAGE_DIR: {
		schema: (value) => optional(value) ?? './storage',
		description: 'Private directory for uploaded files (outside static/)'
	}
});
