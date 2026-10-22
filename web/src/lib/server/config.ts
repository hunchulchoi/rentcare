import { DATABASE_URL, DATABASE_SSL, CAR_API_KEY, DEMO_VENDOR_TOKEN, PUBLIC_BASE_URL, STORAGE_DIR } from '$app/env/private';
import path from 'node:path';

export const config = {
	databaseUrl: DATABASE_URL,
	databaseSsl: DATABASE_SSL,
	carApiKey: CAR_API_KEY,
	demoVendorToken: DEMO_VENDOR_TOKEN,
	publicBaseUrl: PUBLIC_BASE_URL?.replace(/\/+$/, ''),
	storageDir: path.resolve(STORAGE_DIR ?? './storage')
};
