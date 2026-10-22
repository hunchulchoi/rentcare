export type DbSsl = false | 'require' | 'verify-full' | 'prefer';

const LOCAL = new Set(['localhost', '127.0.0.1', '::1', '[::1]']);

/**
 * TLS for the DB connection (Pleos security guide: no plaintext for external traffic).
 * Remote hosts require TLS unless DATABASE_SSL=disable is set on purpose.
 */
export function dbSsl(databaseUrl: string, setting: string | undefined): DbSsl {
	const url = new URL(databaseUrl);
	const mode = (setting ?? url.searchParams.get('sslmode') ?? '').toLowerCase();
	if (mode === 'disable') return false;
	if (mode === 'verify-full' || mode === 'prefer' || mode === 'require') return mode;
	return LOCAL.has(url.hostname) ? false : 'require';
}
