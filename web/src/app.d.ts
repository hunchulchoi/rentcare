// See https://svelte.dev/docs/kit/types#app.d.ts
import type { VendorSession } from '#lib/server/vendor.ts';

declare global {
	namespace App {
		interface Locals {
			vendor: VendorSession | null;
		}
	}
}

export {};
