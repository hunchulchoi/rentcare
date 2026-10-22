import { MAX_FILE_BYTES } from '../files';

// Multipart overhead allowance on top of the per-file limit.
const MAX_MULTIPART_BYTES = MAX_FILE_BYTES + 1024 * 1024;

export type ParsedUpload =
	| { ok: true; file: File; form: FormData }
	| { ok: false; status: number; error: 'invalid_request' | 'too_large'; message: string };

/** Parse a multipart request carrying exactly one `file` field. */
export async function readSingleFile(request: Request): Promise<ParsedUpload> {
	const ct = request.headers.get('content-type') ?? '';
	if (!/^multipart\/form-data\s*;/i.test(ct)) {
		return { ok: false, status: 400, error: 'invalid_request', message: 'Content-Type must be multipart/form-data' };
	}
	const len = Number(request.headers.get('content-length') ?? 0);
	if (len > MAX_MULTIPART_BYTES) return { ok: false, status: 413, error: 'too_large', message: 'File exceeds 50 MB' };

	let form: FormData;
	try {
		form = await request.formData();
	} catch {
		// also thrown when the adapter's BODY_SIZE_LIMIT is exceeded
		return { ok: false, status: 400, error: 'invalid_request', message: 'Could not read multipart body' };
	}
	const files = form.getAll('file');
	if (files.length !== 1 || !(files[0] instanceof File)) {
		return { ok: false, status: 400, error: 'invalid_request', message: 'Exactly one "file" field is required' };
	}
	return { ok: true, file: files[0], form };
}
