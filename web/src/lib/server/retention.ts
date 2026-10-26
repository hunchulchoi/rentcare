import { retentionCutoff } from '../retention.ts';
import { config } from './config';
import { db } from './db';
import { errorId } from './log-safe.ts';
import { removeFile } from './storage';

export type PurgeResult = { incidents: number; files: number; failedFiles: number; rentals: number };

/**
 * Delete reports past their retention (docs/privacy.md): files first, then the incident row
 * (attachments, attach codes and the staff log go with it by cascade). If a file cannot be deleted
 * the incident is kept and retried on the next run, so no file is left without its record.
 */
export async function purgeExpired(now = new Date(), days = config.retentionDays, dryRun = false): Promise<PurgeResult> {
	const cutoff = retentionCutoff(now, days);
	const rows = await db()<{ id: string; files: string[] }[]>`
		select i.id, coalesce(array_agg(a.storage_name) filter (where a.storage_name is not null), '{}') as files
		from incidents i left join attachments a on a.incident_id = i.id
		where i.created_at < ${cutoff}
		group by i.id
		limit 500`;
	const result: PurgeResult = { incidents: 0, files: 0, failedFiles: 0, rentals: 0 };
	if (dryRun) {
		const [{ n }] = await db()<{ n: number }[]>`select count(*)::int as n from rentals where created_at < ${cutoff}`;
		return { incidents: rows.length, files: rows.reduce((n, r) => n + r.files.length, 0), failedFiles: 0, rentals: n };
	}
	for (const r of rows) {
		let failed = 0;
		for (const f of r.files) {
			try {
				await removeFile(f);
				result.files++;
			} catch {
				failed++;
			}
		}
		result.failedFiles += failed;
		if (failed === 0) {
			await db()`delete from incidents where id = ${r.id}`;
			result.incidents++;
		}
	}
	// Rentals registered by the car (RENTCARE-13). Their reports keep their own copy of the rental fields.
	result.rentals = (await db()`delete from rentals where created_at < ${cutoff} returning id`).length;
	return result;
}

let timer: ReturnType<typeof setInterval> | undefined;

export function stopRetention(): void {
	if (timer) clearInterval(timer);
	timer = undefined;
}

/** Run at start and every 6 hours (a stopped container under Sablier simply runs it at its next start). */
export function scheduleRetention(): void {
	if (timer || !config.databaseUrl) return;
	const run = () =>
		purgeExpired()
			.then((r) => {
				if (r.incidents || r.failedFiles || r.rentals) {
					console.log(`[rentcare] retention: deleted ${r.incidents} reports, ${r.files} files, ${r.failedFiles} file errors, ${r.rentals} rentals`);
				}
			})
			.catch((e) => console.error(`[rentcare] retention failed: ${errorId(e)}`));
	void run();
	timer = setInterval(run, 6 * 3600 * 1000);
	timer.unref?.();
}
