import { createReadStream, createWriteStream } from 'node:fs';
import { mkdir, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import type { ReadableStream as NodeWebReadableStream } from 'node:stream/web';
import { config } from './config';
import { randomHex } from './crypto';

function resolveInside(storageName: string): string {
	const root = config.storageDir;
	const full = path.resolve(root, storageName);
	if (!full.startsWith(root + path.sep)) throw new Error('storage path escapes STORAGE_DIR');
	return full;
}

/** Write an uploaded file under STORAGE_DIR with a random name. Returns the relative storage name. */
export async function saveFile(incidentId: string, file: File): Promise<string> {
	const storageName = `${incidentId}/${randomHex(16)}`;
	const full = resolveInside(storageName);
	await mkdir(path.dirname(full), { recursive: true, mode: 0o700 });
	await pipeline(
		Readable.fromWeb(file.stream() as unknown as NodeWebReadableStream),
		createWriteStream(full, { flags: 'wx', mode: 0o600 })
	);
	return storageName;
}

export async function removeFile(storageName: string): Promise<void> {
	await rm(resolveInside(storageName), { force: true });
}

export async function openFile(storageName: string): Promise<{ stream: ReadableStream; size: number } | null> {
	const full = resolveInside(storageName);
	try {
		const s = await stat(full);
		if (!s.isFile()) return null;
		return { stream: Readable.toWeb(createReadStream(full)) as unknown as ReadableStream, size: s.size };
	} catch {
		return null;
	}
}
