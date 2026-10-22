import { describe, expect, it } from 'vitest';
import { magicMatches, resolveMime, sanitizeDisplayName } from './files';

const bytes = (...b: number[]) => Uint8Array.from(b);
const withAtom = (atom: string) => Uint8Array.from([0, 0, 0, 0x18, ...[...atom].map((c) => c.charCodeAt(0)), 0, 0, 0, 0]);

describe('resolveMime', () => {
	it('requires declared type and extension to agree', () => {
		expect(resolveMime('image/jpeg', 'a.JPG')).toBe('image/jpeg');
		expect(resolveMime('image/jpeg', 'a.jpeg')).toBe('image/jpeg');
		expect(resolveMime('video/quicktime', 'clip.mov')).toBe('video/quicktime');
		expect(resolveMime('image/jpeg', 'a.png')).toBeNull();
		expect(resolveMime('image/png', 'a.png.exe')).toBeNull();
		expect(resolveMime('text/html', 'a.jpg')).toBeNull();
		expect(resolveMime('image/gif', 'a.gif')).toBeNull();
	});
	it('falls back to extension when the browser sends no type', () => {
		expect(resolveMime('', 'IMG_0001.HEIC')).toBe('image/heic');
		expect(resolveMime('application/octet-stream', 'v.mov')).toBe('video/quicktime');
		expect(resolveMime('', 'noext')).toBeNull();
		expect(resolveMime('', 'a.svg')).toBeNull();
	});
});

describe('magicMatches', () => {
	it('checks container signatures', () => {
		expect(magicMatches('image/jpeg', bytes(0xff, 0xd8, 0xff, 0xe0))).toBe(true);
		expect(magicMatches('image/jpeg', bytes(0x89, 0x50, 0x4e, 0x47))).toBe(false);
		expect(magicMatches('image/png', bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))).toBe(true);
		expect(magicMatches('video/mp4', withAtom('ftyp'))).toBe(true);
		expect(magicMatches('image/heic', withAtom('ftyp'))).toBe(true);
		expect(magicMatches('video/mp4', withAtom('moov'))).toBe(false);
		expect(magicMatches('video/quicktime', withAtom('moov'))).toBe(true);
		expect(magicMatches('video/quicktime', bytes(0x3c, 0x68, 0x74, 0x6d, 0x6c, 0, 0, 0))).toBe(false);
		expect(magicMatches('image/jpeg', bytes())).toBe(false);
	});
});

describe('sanitizeDisplayName', () => {
	it('strips paths and control characters', () => {
		expect(sanitizeDisplayName('../../etc/passwd')).toBe('passwd');
		expect(sanitizeDisplayName('C:\\Users\\x\\photo.jpg')).toBe('photo.jpg');
		expect(sanitizeDisplayName('a\u0000b"<c>.png')).toBe('abc.png');
		expect(sanitizeDisplayName('')).toBe('file');
	});
});
