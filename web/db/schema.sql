-- RentCare schema (PostgreSQL 16). Idempotent: safe to run on an existing database.
-- Applied automatically by docker-entrypoint-initdb.d on first start, or by `pnpm db:migrate`.

create table if not exists vendors (
	id text primary key,
	name text not null,
	-- hex SHA-256 of the staff login token; null = login disabled
	staff_token_hash text,
	created_at timestamptz not null default now()
);

create table if not exists vendor_sessions (
	-- hex SHA-256 of the random session token kept in the httpOnly cookie
	token_hash text primary key,
	vendor_id text not null references vendors (id) on delete cascade,
	staff_label text,
	created_at timestamptz not null default now(),
	expires_at timestamptz not null
);
create index if not exists vendor_sessions_expires_idx on vendor_sessions (expires_at);

-- One row per KST calendar day; last_no is the last issued receipt sequence of that day.
create table if not exists receipt_counters (
	day text primary key, -- 'YYYYMMDD' (Asia/Seoul)
	last_no integer not null
);

create table if not exists incidents (
	id uuid primary key default gen_random_uuid(),
	vendor_id text not null references vendors (id),
	receipt_no text not null unique,
	idempotency_key text not null unique,
	type text not null check (type in ('NO_START', 'TIRE', 'WARNING_LIGHT', 'ACCIDENT', 'OTHER')),
	language text not null check (language in ('ko', 'en', 'ja', 'zh')),
	note text check (note is null or char_length(note) <= 2000),
	occurred_at timestamptz not null,
	demo boolean not null default false,

	rental_id text not null,
	vehicle_plate text not null,
	vehicle_model text,
	powertrain text,
	return_place text,
	return_at timestamptz,

	-- vehicle snapshot (all null when the car could not read it)
	vehicle_read boolean not null default false,
	fuel_percent double precision,
	soc double precision,
	vehicle_read_at timestamptz,

	-- location (all null when the car could not read it)
	latitude double precision,
	longitude double precision,
	address text,
	location_read_at timestamptz,
	location_source text check (location_source in ('VEHICLE', 'MANUAL', 'MOBILE')),
	location_edited boolean,

	status text not null default 'RECEIVED' check (status in ('RECEIVED', 'ACKNOWLEDGED')),
	acknowledged_at timestamptz,
	acknowledged_by text,
	created_at timestamptz not null default now()
);
create index if not exists incidents_vendor_created_idx on incidents (vendor_id, created_at desc);

create table if not exists attach_codes (
	id bigserial primary key,
	incident_id uuid not null references incidents (id) on delete cascade,
	code_hash text not null unique, -- hex SHA-256 of the normalized 6-char code
	expires_at timestamptz not null,
	revoked_at timestamptz,
	created_at timestamptz not null default now()
);
create index if not exists attach_codes_incident_idx on attach_codes (incident_id);

create table if not exists attachments (
	id uuid primary key default gen_random_uuid(),
	incident_id uuid not null references incidents (id) on delete cascade,
	storage_name text not null unique, -- random, relative to STORAGE_DIR; never the user's filename
	original_name text, -- sanitized, display only
	mime_type text not null,
	size_bytes bigint not null,
	source text not null check (source in ('CAR', 'MOBILE')),
	created_at timestamptz not null default now()
);
create index if not exists attachments_incident_idx on attachments (incident_id, created_at);

-- Demo vendor. The staff token hash is filled from DEMO_VENDOR_TOKEN by `pnpm db:migrate`
-- (and synced by the server on the first login attempt).
insert into vendors (id, name) values ('demo-jeju', '제주 데모렌터카') on conflict (id) do nothing;

-- v2: per-incident car token (only the creating car can read status / add files). Hash only.
alter table incidents add column if not exists car_token_hash text;

-- v3: car warning lights on when the report was made (Pleos TelltaleType names)
alter table incidents add column if not exists warnings text[] not null default '{}';

-- v5: vendor-side log per incident — internal notes and actions taken. Never sent to the car.
create table if not exists incident_actions (
	id uuid primary key default gen_random_uuid(),
	incident_id uuid not null references incidents(id) on delete cascade,
	kind text not null check (kind in ('NOTE', 'ACTION')),
	action_type text check (action_type in ('CALL', 'DISPATCH', 'TOW', 'REPLACEMENT', 'REPAIR', 'OTHER')),
	body text check (body is null or char_length(body) <= 2000),
	staff text,
	created_at timestamptz not null default now(),
	check ((kind = 'NOTE' and body is not null and action_type is null) or (kind = 'ACTION' and action_type is not null))
);
create index if not exists incident_actions_incident_idx on incident_actions (incident_id, created_at);

-- v6: handling status — IN_PROGRESS (first staff action) and RESOLVED (staff marked done).
alter table incidents drop constraint if exists incidents_status_check;
alter table incidents add constraint incidents_status_check
	check (status in ('RECEIVED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED'));
alter table incidents add column if not exists resolved_at timestamptz;
alter table incidents add column if not exists resolved_by text;

-- v7: vendor's expected time (e.g. tow arrival). Latest one on the incident, each one on its log entry.
alter table incidents add column if not exists expected_at timestamptz;
alter table incidents add column if not exists expected_set_at timestamptz;
alter table incident_actions add column if not exists expected_at timestamptz;

-- v8: rentals registered by the car (RENTCARE-13). The rental code lets the renter file a basic report
-- from a phone when the car screen cannot be used. Code and car token are stored as SHA-256 hashes only.
create table if not exists rentals (
	id uuid primary key default gen_random_uuid(),
	vendor_id text not null references vendors (id),
	idempotency_key text not null unique,
	language text not null check (language in ('ko', 'en', 'ja', 'zh')),
	demo boolean not null default false,
	rental_id text not null,
	vehicle_plate text not null,
	vehicle_model text,
	powertrain text,
	return_place text,
	return_at timestamptz,
	code_hash text unique,
	code_expires_at timestamptz,
	car_token_hash text,
	released_at timestamptz,
	created_at timestamptz not null default now()
);
create index if not exists rentals_created_idx on rentals (created_at);
alter table incidents add column if not exists channel text not null default 'CAR';
alter table incidents drop constraint if exists incidents_channel_check;
alter table incidents add constraint incidents_channel_check check (channel in ('CAR', 'PHONE'));
alter table incidents add column if not exists rental_ref uuid references rentals (id) on delete set null;
create index if not exists incidents_rental_ref_idx on incidents (rental_ref);

-- v9 (2026-10-27, RENTCARE-36): car enrollment codes issued on the vendor screen. The car registers the
-- rental with one and gets its own rental token; no shared key ships in the app (docs/api.md v13).
create table if not exists enrollment_codes (
	code_hash text primary key, -- hex SHA-256 of the 8-char code
	vendor_id text not null references vendors (id) on delete cascade,
	expires_at timestamptz not null,
	used_at timestamptz,
	rental_ref uuid references rentals (id) on delete set null,
	created_at timestamptz not null default now()
);
create index if not exists enrollment_codes_expires_idx on enrollment_codes (expires_at);
