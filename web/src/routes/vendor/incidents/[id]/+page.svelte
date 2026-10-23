<script lang="ts">
	import { enhance } from '$app/forms';
	import { fmtBytes, fmtKst } from '#lib/format.ts';
	import { LANGUAGE_NAMES_KO, LOCATION_SOURCE_LABELS_KO, STATUS_LABELS_KO, typeLabel, warningLabelKo } from '#lib/i18n.ts';
	import StaticMap from '#lib/StaticMap.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
	const i = $derived(data.incident);
	const hasCoords = $derived(i.latitude !== null && i.longitude !== null);
	const kakaoUrl = $derived(hasCoords ? `https://map.kakao.com/link/map/${encodeURIComponent(i.receipt_no)},${i.latitude},${i.longitude}` : '');
	const googleUrl = $derived(hasCoords ? `https://www.google.com/maps/search/?api=1&query=${i.latitude},${i.longitude}` : '');
	let submitting = $state(false);
</script>

<svelte:head>
	<title>{i.receipt_no} · RentCare</title>
</svelte:head>

<p><a href="/vendor">← 목록</a></p>

<h1>
	{i.receipt_no}
	{#if i.demo}<span class="badge demo">데모 접수</span>{/if}
	<span class={`badge ${i.status}`}>{STATUS_LABELS_KO[i.status]}</span>
</h1>

<section class="ack">
	{#if i.status === 'ACKNOWLEDGED'}
		<p>확인: {fmtKst(i.acknowledged_at)} · {i.acknowledged_by ?? '-'}</p>
	{:else}
		<form
			method="POST"
			action="?/acknowledge"
			use:enhance={() => {
				submitting = true;
				return async ({ update }) => {
					await update();
					submitting = false;
				};
			}}
		>
			<button type="submit" disabled={submitting}>확인</button>
		</form>
	{/if}
	<p class="hint">"확인"은 업체가 신고를 보았다는 표시이며, 출동·수리 확정이 아닙니다.</p>
</section>

<h2>신고</h2>
<dl class="kv">
	<dt>유형</dt>
	<dd>{typeLabel(i.type, 'ko')}</dd>
	<dt>사용자 언어</dt>
	<dd>{LANGUAGE_NAMES_KO[i.language] ?? i.language}</dd>
	<dt>발생 시각</dt>
	<dd>{fmtKst(i.occurred_at)}</dd>
	<dt>접수 시각</dt>
	<dd>{fmtKst(i.created_at)}</dd>
</dl>

<h3>접수 시 켜진 차량 경고등</h3>
{#if i.warnings?.length}
	<ul class="warnings">
		{#each i.warnings as w (w)}<li>{warningLabelKo(w)} <small>({w})</small></li>{/each}
	</ul>
{:else}
	<p class="muted">없음 또는 확인 불가</p>
{/if}

<h3>원문 <small>(번역하지 않은 사용자 입력)</small></h3>
{#if i.note}
	<blockquote class="note" lang={i.language}>{i.note}</blockquote>
{:else}
	<p class="hint">메모 없음</p>
{/if}

<h2>대여·차량</h2>
<dl class="kv">
	<dt>대여 번호</dt>
	<dd>{i.rental_id}</dd>
	<dt>차량 번호</dt>
	<dd>{i.vehicle_plate}</dd>
	<dt>차종</dt>
	<dd>{i.vehicle_model ?? '-'}</dd>
	<dt>동력원</dt>
	<dd>{i.powertrain ?? '-'}</dd>
	<dt>반납 장소</dt>
	<dd>{i.return_place ?? '-'}</dd>
	<dt>반납 예정</dt>
	<dd>{fmtKst(i.return_at)}</dd>
</dl>

<h3>차량 상태</h3>
{#if i.vehicle_read}
	<dl class="kv">
		<dt>연료</dt>
		<dd>{i.fuel_percent !== null ? `${i.fuel_percent}%` : '-'}</dd>
		<dt>배터리(SOC)</dt>
		<dd>{i.soc !== null ? `${i.soc}%` : '-'}</dd>
		<dt>읽은 시각</dt>
		<dd>{fmtKst(i.vehicle_read_at)}</dd>
	</dl>
{:else}
	<p class="hint">차량 정보를 읽지 못했습니다.</p>
{/if}

<h2>위치</h2>
{#if hasCoords}
	<StaticMap lat={Number(i.latitude)} lon={Number(i.longitude)} tileBase="/vendor/map" />
	<dl class="kv">
		<dt>주소</dt>
		<dd>{i.address ?? '-'}</dd>
		<dt>좌표</dt>
		<dd>
			{i.latitude}, {i.longitude}
			· <a href={kakaoUrl} target="_blank" rel="noopener noreferrer">카카오맵</a>
			· <a href={googleUrl} target="_blank" rel="noopener noreferrer">구글 지도</a>
		</dd>
		<dt>출처</dt>
		<dd>{i.location_source ? LOCATION_SOURCE_LABELS_KO[i.location_source] : '-'}</dd>
		<dt>읽은 시각</dt>
		<dd>{fmtKst(i.location_read_at)}</dd>
		<dt>사용자 수정</dt>
		<dd>
			{#if i.location_edited}<span class="badge demo">사용자가 수정함</span>{:else}수정 안 함{/if}
		</dd>
	</dl>
{:else}
	<p class="hint">위치를 읽지 못했습니다.</p>
{/if}

<h2>첨부 ({data.attachments.length})</h2>
{#if data.attachments.length}
	<ul class="attachments">
		{#each data.attachments as a, n (a.id)}
			<li>
				<a href={`/vendor/files/${a.id}`} download>파일 {n + 1}</a>
				<span class="hint">
					{a.original_name ?? ''} · {a.mime_type} · {fmtBytes(a.size_bytes)} · {a.source === 'CAR' ? '차량' : '휴대폰'} · {fmtKst(a.created_at)}
				</span>
			</li>
		{/each}
	</ul>
{:else}
	<p class="hint">첨부 파일이 없습니다.</p>
{/if}

<style>
	h1 .badge {
		vertical-align: middle;
		margin-left: 0.25rem;
	}
	.ack {
		max-width: 320px;
	}
	.hint {
		color: var(--muted);
		font-size: 0.85rem;
	}
	.kv {
		display: grid;
		grid-template-columns: 8rem 1fr;
		gap: 0.3rem 1rem;
		margin: 0;
	}
	.kv dt {
		color: var(--muted);
	}
	.kv dd {
		margin: 0;
		overflow-wrap: anywhere;
	}
	.note {
		margin: 0;
		padding: 0.75rem 1rem;
		background: var(--panel);
		border-left: 4px solid var(--accent);
		white-space: pre-wrap;
		overflow-wrap: anywhere;
	}
	.attachments {
		padding-left: 1.2rem;
	}
	.attachments li {
		margin-bottom: 0.4rem;
	}
	h3 small {
		font-weight: normal;
		color: var(--muted);
	}
</style>
