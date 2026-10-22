// Attachment rules shared by the server (enforcement) and the /m page (early client-side hints).

export const MAX_FILE_BYTES = 50 * 1024 * 1024;
export const MAX_FILES_PER_INCIDENT = 10;

/** Allowed MIME type → allowed file extensions (lower case, no dot). */
export const ALLOWED_TYPES: Record<string, readonly string[]> = {
	'image/jpeg': ['jpg', 'jpeg'],
	'image/png': ['png'],
	'image/heic': ['heic'],
	'video/mp4': ['mp4'],
	'video/quicktime': ['mov']
};

export const ACCEPT_ATTR = [...Object.keys(ALLOWED_TYPES), ...Object.values(ALLOWED_TYPES).flat().map((e) => `.${e}`)].join(',');

export function extensionOf(name: string): string {
	const m = /\.([A-Za-z0-9]{1,10})$/.exec(name);
	return m ? m[1].toLowerCase() : '';
}

/**
 * Decide the canonical MIME type from the declared type and the file name.
 * Both must agree with the allowlist. Browsers sometimes send an empty type (or
 * application/octet-stream) for HEIC/MOV; in that case the extension decides.
 * Returns null if not allowed.
 */
export function resolveMime(declaredType: string, fileName: string): string | null {
	const ext = extensionOf(fileName);
	const declared = declaredType.split(';')[0].trim().toLowerCase();
	if (declared && declared !== 'application/octet-stream') {
		const exts = ALLOWED_TYPES[declared];
		return exts && exts.includes(ext) ? declared : null;
	}
	for (const [mime, exts] of Object.entries(ALLOWED_TYPES)) if (exts.includes(ext)) return mime;
	return null;
}

function ascii(bytes: Uint8Array, start: number, end: number): string {
	return String.fromCharCode(...bytes.subarray(start, end));
}

/** Check the first bytes of the file against the expected container format. */
export function magicMatches(mime: string, head: Uint8Array): boolean {
	switch (mime) {
		case 'image/jpeg':
			return head.length >= 3 && head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff;
		case 'image/png':
			return head.length >= 8 && ascii(head, 0, 8) === '\x89PNG\r\n\x1a\n';
		case 'image/heic':
		case 'video/mp4':
			// ISO base media file: [size:4]['ftyp']
			return head.length >= 8 && ascii(head, 4, 8) === 'ftyp';
		case 'video/quicktime': {
			if (head.length < 8) return false;
			const atom = ascii(head, 4, 8);
			return ['ftyp', 'moov', 'mdat', 'wide', 'free', 'skip', 'pnot'].includes(atom);
		}
		default:
			return false;
	}
}

/** Display-only file name: strip paths and control chars, cap length. Never used on disk. */
export function sanitizeDisplayName(name: string): string {
	const base = name.split(/[/\\]/).pop() ?? '';
	// eslint-disable-next-line no-control-regex
	const clean = base.replace(/[\u0000-\u001f\u007f"<>]/g, '').trim();
	return [...clean].slice(0, 120).join('') || 'file';
}
