// Strict validator for POST /api/incidents bodies. No dependencies; pure and unit-tested.
import {
	INCIDENT_TYPES,
	LANGUAGES,
	LOCATION_SOURCES,
	type IncidentType,
	type Language,
	type LocationSource
} from '../i18n';

export const NOTE_MAX = 2000;

export interface IncidentInput {
	vendorId: string;
	type: IncidentType;
	language: Language;
	note: string | null;
	occurredAt: Date;
	demo: boolean;
	rental: {
		rentalId: string;
		vehiclePlate: string;
		vehicleModel: string | null;
		powertrain: string | null;
		returnPlace: string | null;
		returnAt: Date | null;
	};
	vehicle: { fuelPercent: number | null; soc: number | null; readAt: Date } | null;
	location: {
		latitude: number;
		longitude: number;
		address: string | null;
		readAt: Date;
		source: LocationSource;
		edited: boolean;
	} | null;
}

export type ValidationResult<T> = { ok: true; value: T } | { ok: false; errors: string[] };

type Obj = Record<string, unknown>;

const ISO_Z = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,6})?Z$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
// eslint-disable-next-line no-control-regex
const FORBIDDEN_CHARS = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/;

export function isUuid(v: unknown): v is string {
	return typeof v === 'string' && UUID.test(v);
}

/** Idempotency-Key header: a UUID, normalized to lower case. */
export function parseIdempotencyKey(v: string | null): string | null {
	if (!v) return null;
	const s = v.trim();
	return isUuid(s) ? s.toLowerCase() : null;
}

class Checker {
	errors: string[] = [];

	fail(path: string, msg: string): undefined {
		this.errors.push(`${path}: ${msg}`);
		return undefined;
	}

	object(v: unknown, path: string, allowed: readonly string[]): Obj | undefined {
		if (typeof v !== 'object' || v === null || Array.isArray(v)) return this.fail(path, 'must be an object');
		for (const k of Object.keys(v)) {
			if (!allowed.includes(k)) this.fail(`${path}.${k}`, 'unknown field');
		}
		return v as Obj;
	}

	has(o: Obj, key: string, path: string): boolean {
		if (!(key in o) || o[key] === undefined) {
			this.fail(`${path}.${key}`, 'is required');
			return false;
		}
		return true;
	}

	string(v: unknown, path: string, max: number, { nullable = false, min = 1 } = {}): string | null | undefined {
		if (v === null || v === undefined) {
			if (nullable) return null;
			return this.fail(path, 'is required');
		}
		if (typeof v !== 'string') return this.fail(path, 'must be a string');
		if (FORBIDDEN_CHARS.test(v)) return this.fail(path, 'contains control characters');
		const len = [...v].length;
		if (len < min) return this.fail(path, `must be at least ${min} characters`);
		if (len > max) return this.fail(path, `must be at most ${max} characters`);
		return v;
	}

	oneOf<T extends string>(v: unknown, path: string, values: readonly T[]): T | undefined {
		if (typeof v !== 'string' || !(values as readonly string[]).includes(v)) {
			return this.fail(path, `must be one of ${values.join(', ')}`);
		}
		return v as T;
	}

	date(v: unknown, path: string, { nullable = false } = {}): Date | null | undefined {
		if (v === null || v === undefined) {
			if (nullable) return null;
			return this.fail(path, 'is required');
		}
		if (typeof v !== 'string' || !ISO_Z.test(v)) return this.fail(path, 'must be an ISO-8601 UTC timestamp ending in Z');
		const d = new Date(v);
		if (Number.isNaN(d.getTime())) return this.fail(path, 'is not a valid date');
		// Reject calendar overflow like 2026-02-31 (Date would silently roll it over)
		if (d.toISOString().slice(0, 10) !== v.slice(0, 10)) return this.fail(path, 'is not a valid date');
		return d;
	}

	number(v: unknown, path: string, minV: number, maxV: number, { nullable = false } = {}): number | null | undefined {
		if (v === null || v === undefined) {
			if (nullable) return null;
			return this.fail(path, 'is required');
		}
		if (typeof v !== 'number' || !Number.isFinite(v)) return this.fail(path, 'must be a number');
		if (v < minV || v > maxV) return this.fail(path, `must be between ${minV} and ${maxV}`);
		return v;
	}

	boolean(v: unknown, path: string): boolean | undefined {
		if (typeof v !== 'boolean') return this.fail(path, 'must be a boolean');
		return v;
	}
}

const TOP_KEYS = ['vendorId', 'type', 'language', 'note', 'occurredAt', 'demo', 'rental', 'vehicle', 'location'];
const RENTAL_KEYS = ['rentalId', 'vehiclePlate', 'vehicleModel', 'powertrain', 'returnPlace', 'returnAt'];
const VEHICLE_KEYS = ['fuelPercent', 'soc', 'readAt'];
const LOCATION_KEYS = ['latitude', 'longitude', 'address', 'readAt', 'source', 'edited'];

export function validateIncidentInput(body: unknown): ValidationResult<IncidentInput> {
	const c = new Checker();
	const o = c.object(body, '$', TOP_KEYS);
	if (!o) return { ok: false, errors: c.errors };

	const vendorId = c.string(o.vendorId, '$.vendorId', 64);
	if (typeof vendorId === 'string' && !/^[a-z0-9][a-z0-9-]*$/.test(vendorId)) c.fail('$.vendorId', 'invalid format');
	const type = c.oneOf(o.type, '$.type', INCIDENT_TYPES);
	const language = c.oneOf(o.language, '$.language', LANGUAGES);
	let note = c.string(o.note, '$.note', NOTE_MAX, { nullable: true, min: 0 });
	if (typeof note === 'string' && note.trim() === '') note = null; // empty note == no note; otherwise kept as-is
	const occurredAt = c.date(o.occurredAt, '$.occurredAt');
	let demo = false;
	if (o.demo !== undefined) demo = c.boolean(o.demo, '$.demo') ?? false;

	let rental: IncidentInput['rental'] | undefined;
	if (c.has(o, 'rental', '$')) {
		const r = c.object(o.rental, '$.rental', RENTAL_KEYS);
		if (r) {
			const rentalId = c.string(r.rentalId, '$.rental.rentalId', 64);
			const vehiclePlate = c.string(r.vehiclePlate, '$.rental.vehiclePlate', 20);
			const vehicleModel = c.string(r.vehicleModel, '$.rental.vehicleModel', 64, { nullable: true });
			const powertrain = c.string(r.powertrain, '$.rental.powertrain', 32, { nullable: true });
			const returnPlace = c.string(r.returnPlace, '$.rental.returnPlace', 200, { nullable: true });
			const returnAt = c.date(r.returnAt, '$.rental.returnAt', { nullable: true });
			if (rentalId && vehiclePlate) {
				rental = {
					rentalId,
					vehiclePlate,
					vehicleModel: vehicleModel ?? null,
					powertrain: powertrain ?? null,
					returnPlace: returnPlace ?? null,
					returnAt: returnAt ?? null
				};
			}
		}
	}

	let vehicle: IncidentInput['vehicle'] = null;
	if (o.vehicle !== undefined && o.vehicle !== null) {
		const v = c.object(o.vehicle, '$.vehicle', VEHICLE_KEYS);
		if (v) {
			const fuelPercent = c.number(v.fuelPercent, '$.vehicle.fuelPercent', 0, 100, { nullable: true });
			const soc = c.number(v.soc, '$.vehicle.soc', 0, 100, { nullable: true });
			const readAt = c.date(v.readAt, '$.vehicle.readAt');
			if (readAt) vehicle = { fuelPercent: fuelPercent ?? null, soc: soc ?? null, readAt };
		}
	}

	let location: IncidentInput['location'] = null;
	if (o.location !== undefined && o.location !== null) {
		const l = c.object(o.location, '$.location', LOCATION_KEYS);
		if (l) {
			const latitude = c.number(l.latitude, '$.location.latitude', -90, 90);
			const longitude = c.number(l.longitude, '$.location.longitude', -180, 180);
			const address = c.string(l.address, '$.location.address', 500, { nullable: true });
			const readAt = c.date(l.readAt, '$.location.readAt');
			const source = c.oneOf(l.source, '$.location.source', LOCATION_SOURCES);
			const edited = c.boolean(l.edited, '$.location.edited');
			if (typeof latitude === 'number' && typeof longitude === 'number' && readAt && source && edited !== undefined) {
				location = { latitude, longitude, address: address ?? null, readAt, source, edited };
			}
		}
	}

	if (c.errors.length > 0 || !vendorId || !type || !language || !occurredAt || !rental) {
		return { ok: false, errors: c.errors.length ? c.errors : ['$: invalid'] };
	}

	return {
		ok: true,
		value: { vendorId, type, language, note: note ?? null, occurredAt, demo, rental, vehicle, location }
	};
}
