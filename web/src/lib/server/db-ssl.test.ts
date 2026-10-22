import { describe, expect, it } from 'vitest';
import { dbSsl, dbSslOptions } from './db-ssl';

describe('dbSsl', () => {
	it('requires TLS for remote hosts by default', () => {
		expect(dbSsl('postgres://u:p@stage.devfor.link:5432/rentcare', undefined)).toBe('require');
	});
	it('allows plain TCP for local hosts', () => {
		expect(dbSsl('postgres://u:p@127.0.0.1:5433/postgres', undefined)).toBe(false);
		expect(dbSsl('postgres://u:p@localhost/db', undefined)).toBe(false);
	});
	it('remote plaintext only with an explicit opt-out', () => {
		expect(dbSsl('postgres://u:p@stage.devfor.link/rentcare', 'disable')).toBe(false);
		expect(dbSsl('postgres://u:p@stage.devfor.link/rentcare', 'verify-full')).toBe('verify-full');
	});
	it('respects sslmode in the URL', () => {
		expect(dbSsl('postgres://u:p@db.example/x?sslmode=verify-full', undefined)).toBe('verify-full');
	});
	it('verifies the server with a pinned CA when one is given', () => {
		const pem = '-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----';
		expect(dbSslOptions('postgres://u:p@stage.devfor.link/r', undefined, pem)).toEqual({ ca: pem, rejectUnauthorized: true });
		expect(dbSslOptions('postgres://u:p@stage.devfor.link/r', undefined, undefined)).toBe('require');
		expect(dbSslOptions('postgres://u:p@127.0.0.1/r', undefined, pem)).toBe(false);
	});
});
