import { error } from '@sveltejs/kit';
import { ALLOWED_TYPES } from '#lib/files.ts';
import { openFile } from '#lib/server/storage.ts';
import { isUuid } from '#lib/server/validate.ts';
import { getVendorAttachment } from '#lib/server/vendor.ts';
import type { RequestHandler } from './$types';

// Access-checked download: the attachment's incident must belong to the session's vendor.
export const GET: RequestHandler = async ({ locals, params }) => {
	const vendor = locals.vendor!;
	if (!isUuid(params.attachmentId)) error(404, 'Not found');
	const a = await getVendorAttachment(vendor.vendorId, params.attachmentId);
	if (!a) error(404, 'Not found');
	const f = await openFile(a.storage_name);
	if (!f) error(404, 'Not found');

	const ext = ALLOWED_TYPES[a.mime_type]?.[0] ?? 'bin';
	const filename = `${a.receipt_no}-${a.idx}.${ext}`; // generated, ASCII-only: no header injection
	return new Response(f.stream, {
		headers: {
			'content-type': a.mime_type,
			'content-length': String(f.size),
			'content-disposition': `attachment; filename="${filename}"`,
			'x-content-type-options': 'nosniff',
			'cache-control': 'private, no-store'
		}
	});
};
