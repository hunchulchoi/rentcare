import { DeleteObjectCommand, GetObjectCommand, NoSuchKey, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { createReadStream, createWriteStream } from 'node:fs';
import { mkdir, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import type { ReadableStream as NodeWebReadableStream } from 'node:stream/web';
import { config } from './config';
import { randomHex } from './crypto';
import { objectKey } from './storage-config.ts';

// Attachments live in MinIO/S3 when S3_BUCKET is set (config.s3), else under STORAGE_DIR.
// Both use the same storage name `<incident id>/<random hex>`, stored in attachments.storage_name.

let s3client: S3Client | undefined;
function s3(): S3Client {
	const s = config.s3!;
	s3client ??= new S3Client({ region: s.region, endpoint: s.endpoint, forcePathStyle: s.forcePathStyle });
	return s3client;
}

function nodeStream(file: File): Readable {
	return Readable.fromWeb(file.stream() as unknown as NodeWebReadableStream);
}

function resolveInside(storageName: string): string {
	const root = config.storageDir;
	const full = path.resolve(root, storageName);
	if (!full.startsWith(root + path.sep)) throw new Error('storage path escapes STORAGE_DIR');
	return full;
}

/** Store an uploaded file under a random name. Returns the storage name. */
export async function saveFile(incidentId: string, file: File): Promise<string> {
	const storageName = `${incidentId}/${randomHex(16)}`;
	if (config.s3) {
		await s3().send(
			new PutObjectCommand({
				Bucket: config.s3.bucket,
				Key: objectKey(storageName),
				Body: nodeStream(file),
				ContentLength: file.size,
				// Served only through /vendor/files with our own headers; never rendered from the bucket.
				ContentType: 'application/octet-stream'
			})
		);
		return storageName;
	}
	const full = resolveInside(storageName);
	await mkdir(path.dirname(full), { recursive: true, mode: 0o700 });
	await pipeline(nodeStream(file), createWriteStream(full, { flags: 'wx', mode: 0o600 }));
	return storageName;
}

export async function removeFile(storageName: string): Promise<void> {
	if (config.s3) {
		await s3().send(new DeleteObjectCommand({ Bucket: config.s3.bucket, Key: objectKey(storageName) }));
		return;
	}
	await rm(resolveInside(storageName), { force: true });
}

export async function openFile(storageName: string): Promise<{ stream: ReadableStream; size: number } | null> {
	if (config.s3) {
		try {
			const r = await s3().send(new GetObjectCommand({ Bucket: config.s3.bucket, Key: objectKey(storageName) }));
			if (!r.Body || r.ContentLength == null) return null;
			return { stream: r.Body.transformToWebStream() as ReadableStream, size: r.ContentLength };
		} catch (e) {
			if (e instanceof NoSuchKey || (e as { name?: string }).name === 'NoSuchKey') return null;
			throw e;
		}
	}
	const full = resolveInside(storageName);
	try {
		const s = await stat(full);
		if (!s.isFile()) return null;
		return { stream: Readable.toWeb(createReadStream(full)) as unknown as ReadableStream, size: s.size };
	} catch {
		return null;
	}
}
