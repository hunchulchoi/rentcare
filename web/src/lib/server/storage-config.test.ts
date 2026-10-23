import { describe, expect, it } from 'vitest';
import { objectKey, s3Settings } from './storage-config.ts';

describe('s3Settings', () => {
	it('is null without a bucket, so local storage is used', () => {
		expect(s3Settings({})).toBeNull();
		expect(s3Settings({ S3_BUCKET: '  ' })).toBeNull();
	});

	it('reads MinIO settings', () => {
		expect(
			s3Settings({
				S3_BUCKET: 'rentcare',
				AWS_ENDPOINT_URL_S3: 'https://s3.example',
				S3_FORCE_PATH_STYLE: 'true',
				AWS_REGION: 'us-east-1'
			})
		).toEqual({ bucket: 'rentcare', endpoint: 'https://s3.example', forcePathStyle: true, region: 'us-east-1' });
	});

	it('defaults region and path style, refuses a plain-http remote endpoint', () => {
		expect(s3Settings({ S3_BUCKET: 'b' })).toEqual({
			bucket: 'b', endpoint: undefined, forcePathStyle: false, region: 'us-east-1'
		});
		expect(() => s3Settings({ S3_BUCKET: 'b', AWS_ENDPOINT_URL_S3: 'http://s3.example' })).toThrow();
		expect(s3Settings({ S3_BUCKET: 'b', AWS_ENDPOINT_URL_S3: 'http://127.0.0.1:9000' })?.endpoint).toBe(
			'http://127.0.0.1:9000'
		);
	});
});

describe('objectKey', () => {
	const id = '6f1c2a3b-4d5e-4f60-8a1b-2c3d4e5f6a7b';
	it('keeps attachments under one prefix', () => {
		expect(objectKey(`${id}/0123456789abcdef0123456789abcdef`)).toBe(
			`attachments/${id}/0123456789abcdef0123456789abcdef`
		);
	});

	it('rejects anything that is not a storage name we generated', () => {
		for (const bad of ['', '../x', `/${id}/0123456789abcdef0123456789abcdef`, `${id}/../../etc`, `${id}/ABC`]) {
			expect(() => objectKey(bad)).toThrow();
		}
	});
});
