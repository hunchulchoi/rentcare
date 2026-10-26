// Readiness probe for GET /api/health (RENTCARE-21 demo preflight). Never returns error details.
export type ProbeResult = { ok: true } | { ok: false; error: 'failed' | 'timeout' };

export async function probe(check: () => Promise<unknown>, timeoutMs: number): Promise<ProbeResult> {
	let timer: ReturnType<typeof setTimeout> | undefined;
	const timeout = new Promise<ProbeResult>((r) => (timer = setTimeout(() => r({ ok: false, error: 'timeout' }), timeoutMs)));
	const run = check().then(
		(): ProbeResult => ({ ok: true }),
		(): ProbeResult => ({ ok: false, error: 'failed' })
	);
	try {
		return await Promise.race([run, timeout]);
	} finally {
		clearTimeout(timer);
	}
}
