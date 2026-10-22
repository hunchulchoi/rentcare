import { describe, expect, it } from 'vitest';
import { incidentTokenMatches, issueIncidentToken, readIncidentToken } from './incident-token';

describe('incident token', () => {
	it('issues a random url-safe token and stores only its hash', () => {
		const a = issueIncidentToken();
		const b = issueIncidentToken();
		expect(a.token).toMatch(/^[A-Za-z0-9_-]{43}$/);
		expect(a.token).not.toBe(b.token);
		expect(a.hash).toMatch(/^[0-9a-f]{64}$/);
		expect(a.hash).not.toContain(a.token);
	});

	it('matches only the token it was issued for', () => {
		const { token, hash } = issueIncidentToken();
		expect(incidentTokenMatches(token, hash)).toBe(true);
		expect(incidentTokenMatches(issueIncidentToken().token, hash)).toBe(false);
		expect(incidentTokenMatches(token, null)).toBe(false);
	});

	it('reads the token from the X-Incident-Token header only', () => {
		const { token } = issueIncidentToken();
		expect(readIncidentToken(new Request('http://x/api', { headers: { 'x-incident-token': token } }))).toBe(token);
		expect(readIncidentToken(new Request(`http://x/api?token=${token}`))).toBeNull();
		expect(readIncidentToken(new Request('http://x/api', { headers: { 'x-incident-token': 'short' } }))).toBeNull();
	});
});
