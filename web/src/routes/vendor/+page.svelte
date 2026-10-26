<script lang="ts">
	import {
		createTable,
		getCoreRowModel,
		getFilteredRowModel,
		getPaginationRowModel,
		getSortedRowModel,
		type ColumnDef,
		type PaginationState,
		type SortingState,
		type Updater
	} from '@tanstack/table-core';
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';
	import StatusBadge from '#lib/StatusBadge.svelte';
	import { fmtKst } from '#lib/format.ts';
	import { LANGUAGE_NAMES_KO, LOCATION_SOURCE_LABELS_KO, STATUSES, STATUS_LABELS_KO, typeLabel, type IncidentStatus } from '#lib/i18n.ts';
	import { incidentMatches, locationText, type ListRow } from '#lib/incident-list.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
	// Set once the page is interactive (used by scripts/browser-login-check.mjs).
	let ready = $state(false);
	onMount(() => (ready = true));

	// Columns: ids drive sorting; cells are rendered below by id.
	const columns: ColumnDef<ListRow>[] = [
		{ id: 'created_at', header: '접수 시각', accessorFn: (r) => new Date(r.created_at).getTime() },
		{ id: 'receipt_no', header: '접수 번호', accessorKey: 'receipt_no' },
		{ id: 'type', header: '유형', accessorFn: (r) => typeLabel(r.type, 'ko') },
		{ id: 'vehicle', header: '차량', accessorFn: (r) => `${r.vehicle_model ?? ''} ${r.vehicle_plate}` },
		{ id: 'location', header: '위치', accessorFn: (r) => locationText(r), enableSorting: false },
		{ id: 'language', header: '사용자 언어', accessorFn: (r) => LANGUAGE_NAMES_KO[r.language] ?? r.language },
		{ id: 'attachments', header: '첨부', accessorKey: 'attachments' },
		{ id: 'status', header: '상태', accessorKey: 'status' }
	];

	let sorting = $state<SortingState>([{ id: 'created_at', desc: true }]);
	let pagination = $state<PaginationState>({ pageIndex: 0, pageSize: 20 });
	let search = $state('');
	let status = $state<'ALL' | IncidentStatus>('ALL');

	const apply = <T,>(u: Updater<T>, prev: T): T => (typeof u === 'function' ? (u as (p: T) => T)(prev) : u);

	const rows = $derived(data.incidents.filter((r) => status === 'ALL' || r.status === status));
	const FILTERS = ['ALL', ...STATUSES] as const;
	const counts = $derived(
		Object.fromEntries(FILTERS.map((f) => [f, f === 'ALL' ? data.incidents.length : data.incidents.filter((r) => r.status === f).length]))
	);

	// Rebuilt when data or state changes (≤ 200 rows): simple and fully reactive with Svelte 5 runes.
	const table = $derived.by(() => {
		const t = createTable<ListRow>({
			data: rows,
			columns,
			state: {},
			onStateChange: () => {},
			renderFallbackValue: null,
			onSortingChange: (u) => (sorting = apply(u, sorting)),
			onPaginationChange: (u) => (pagination = apply(u, pagination)),
			globalFilterFn: (row, _id, value) => incidentMatches(row.original, String(value ?? '')),
			getCoreRowModel: getCoreRowModel(),
			getSortedRowModel: getSortedRowModel(),
			getFilteredRowModel: getFilteredRowModel(),
			getPaginationRowModel: getPaginationRowModel()
		});
		t.setOptions((prev) => ({ ...prev, state: { ...t.initialState, sorting, pagination, globalFilter: search } }));
		return t;
	});

	const filteredCount = $derived(table.getFilteredRowModel().rows.length);

	function setSearch(v: string) {
		search = v;
		pagination = { ...pagination, pageIndex: 0 };
	}
	function setStatus(s: typeof status) {
		status = s;
		pagination = { ...pagination, pageIndex: 0 };
	}
</script>

<svelte:head>
	<title>신고 목록 · RentCare</title>
</svelte:head>

<h1>신고 목록</h1>

{#if data.incidents.length === 0}
	<p>접수된 신고가 없습니다.</p>
{:else}
	<div class="toolbar">
		<input
			type="search"
			placeholder="접수번호·차량번호·차종·주소·유형 검색"
			value={search}
			oninput={(e) => setSearch(e.currentTarget.value)}
			aria-label="검색"
		/>
		<div class="seg" role="group" aria-label="상태">
			{#each FILTERS as key (key)}
				<button type="button" class:on={status === key} aria-pressed={status === key} onclick={() => setStatus(key)}>
					{key === 'ALL' ? '전체' : STATUS_LABELS_KO[key]} <span class="count">{counts[key]}</span>
				</button>
			{/each}
		</div>
		<span class="result" data-total={data.total}>{filteredCount}건</span>
		{#if data.total > data.incidents.length}<span class="result">전체 {data.total}건 중 최근 {data.limit}건 표시</span>{/if}
	</div>

	<div class="table-wrap" data-ready={ready ? '1' : undefined}>
		<table>
			<thead>
				{#each table.getHeaderGroups() as hg (hg.id)}
					<tr>
						{#each hg.headers as h (h.id)}
							<th class={h.column.id}>
								{#if h.column.getCanSort()}
									<button type="button" class="sort" onclick={h.column.getToggleSortingHandler()}>
										{h.column.columnDef.header}
										<span class="arrow" aria-hidden="true">
											{h.column.getIsSorted() === 'asc' ? '▲' : h.column.getIsSorted() === 'desc' ? '▼' : '↕'}
										</span>
									</button>
								{:else}
									{h.column.columnDef.header}
								{/if}
							</th>
						{/each}
					</tr>
				{/each}
			</thead>
			<tbody>
				{#each table.getRowModel().rows as row (row.id)}
					{@const i = row.original}
					<!-- Whole row opens the detail; the receipt link stays the keyboard/screen-reader target. -->
					<tr class="row" onclick={(e) => { if (!(e.target as HTMLElement).closest('a')) goto(`/vendor/incidents/${i.id}`); }}>
						<td class="nowrap">{fmtKst(i.created_at)}</td>
						<td class="nowrap">
							<a href={`/vendor/incidents/${i.id}`}>{i.receipt_no}</a>
							{#if i.demo}<span class="badge demo">데모</span>{/if}
						</td>
						<td class="nowrap">{typeLabel(i.type, 'ko')}</td>
						<td>
							<div class="model">{i.vehicle_model ?? '-'}</div>
							<div class="sub plate">{i.vehicle_plate}</div>
						</td>
						<td><div class="loc">
							<div class="loc-text">
								<div>{locationText(i)}</div>
								{#if i.location_source}<div class="sub">{LOCATION_SOURCE_LABELS_KO[i.location_source]}</div>{/if}
							</div>
						</div></td>
						<td class="nowrap">{LANGUAGE_NAMES_KO[i.language] ?? i.language}</td>
						<td class="num">{i.attachments}</td>
						<td class="nowrap"><StatusBadge status={i.status} /></td>
					</tr>
				{:else}
					<tr><td colspan={columns.length} class="empty">조건에 맞는 신고가 없습니다.</td></tr>
				{/each}
			</tbody>
		</table>
	</div>

	<div class="pager">
		<button type="button" class="secondary" disabled={!table.getCanPreviousPage()} onclick={() => table.previousPage()}>이전</button>
		<span>{table.getState().pagination.pageIndex + 1} / {Math.max(1, table.getPageCount())}</span>
		<button type="button" class="secondary" disabled={!table.getCanNextPage()} onclick={() => table.nextPage()}>다음</button>
	</div>
{/if}

<style>
	.toolbar {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 10px;
		margin-bottom: 12px;
	}
	.toolbar input {
		flex: 1 1 260px;
		max-width: 420px;
		border-radius: 999px;
		padding-left: 1rem;
	}
	.seg {
		display: flex;
		gap: 4px;
		background: var(--panel);
		border-radius: 999px;
		padding: 4px;
	}
	.seg button {
		width: auto;
		margin: 0;
		padding: 0.35rem 0.9rem;
		border-radius: 999px;
		border: 0;
		background: transparent;
		color: var(--muted);
		font-size: 0.9rem;
	}
	.seg button.on {
		background: #fff;
		color: var(--fg);
		font-weight: 600;
		box-shadow: 0 1px 3px rgb(0 0 0 / 0.12);
	}
	.count {
		color: var(--muted);
		font-weight: 400;
	}
	.result {
		color: var(--muted);
		font-size: 0.9rem;
	}
	.table-wrap {
		overflow-x: auto;
		border: 1px solid var(--border);
		border-radius: 16px;
	}
	table {
		width: 100%;
		border-collapse: collapse;
		font-size: 0.95rem;
	}
	th,
	td {
		text-align: left;
		padding: 0.55rem 0.7rem;
		border-bottom: 1px solid var(--border);
		vertical-align: middle;
	}
	tbody tr:last-child td {
		border-bottom: 0;
	}
	tbody tr.row:nth-child(even) td {
		background: #f8fafb;
	}
	tbody tr.row {
		cursor: pointer;
	}
	tbody tr.row:hover td {
		background: #e9f6f4;
	}
	th {
		background: var(--panel);
		font-weight: 600;
		white-space: nowrap;
	}
	.sort {
		all: unset;
		cursor: pointer;
		display: inline-flex;
		gap: 4px;
		align-items: center;
	}
	.sort:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: 2px;
	}
	.arrow {
		color: var(--muted);
		font-size: 0.75rem;
	}
	.nowrap {
		white-space: nowrap;
	}
	.num {
		text-align: right;
	}
	.sub {
		color: var(--muted);
		font-size: 0.85rem;
	}
	.model,
	.plate {
		white-space: nowrap;
	}
	.loc {
		display: flex;
		align-items: center;
		gap: 10px;
		min-width: 200px;
	}
	.loc-text {
		min-width: 0;
	}
	.empty {
		text-align: center;
		color: var(--muted);
		padding: 2rem;
	}
	.pager {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 12px;
		margin: 14px 0 4px;
	}
	.pager button {
		width: auto;
		margin: 0;
		padding: 0.35rem 1rem;
	}
</style>
