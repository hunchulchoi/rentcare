<script lang="ts">
	import { fmtKst } from '#lib/format.ts';
	import { LANGUAGE_NAMES_KO, STATUS_LABELS_KO, typeLabel } from '#lib/i18n.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>신고 목록 · RentCare</title>
</svelte:head>

<h1>신고 목록</h1>

{#if data.incidents.length === 0}
	<p>접수된 신고가 없습니다.</p>
{:else}
	<div class="table-wrap">
		<table>
			<thead>
				<tr>
					<th>접수 시각</th>
					<th>접수 번호</th>
					<th>유형</th>
					<th>차량</th>
					<th>사용자 언어</th>
					<th>첨부</th>
					<th>상태</th>
				</tr>
			</thead>
			<tbody>
				{#each data.incidents as i (i.id)}
					<tr>
						<td>{fmtKst(i.created_at)}</td>
						<td>
							<a href={`/vendor/incidents/${i.id}`}>{i.receipt_no}</a>
							{#if i.demo}<span class="badge demo">데모 접수</span>{/if}
						</td>
						<td>{typeLabel(i.type, 'ko')}</td>
						<td>{i.vehicle_plate}</td>
						<td>{LANGUAGE_NAMES_KO[i.language] ?? i.language}</td>
						<td>{i.attachments}</td>
						<td><span class={`badge ${i.status}`}>{STATUS_LABELS_KO[i.status]}</span></td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
{/if}

<style>
	.table-wrap {
		overflow-x: auto;
	}
	table {
		width: 100%;
		border-collapse: collapse;
		font-size: 0.95rem;
	}
	th,
	td {
		text-align: left;
		padding: 0.5rem 0.6rem;
		border-bottom: 1px solid var(--border);
		white-space: nowrap;
	}
	th {
		background: var(--panel);
		font-weight: 600;
	}
</style>
