<script lang="ts">
	import { enhance } from '$app/forms';
	import { fmtBytes, fmtKst } from '#lib/format.ts';
	import { LANGUAGE_NAMES_KO, LOCATION_SOURCE_LABELS_KO, typeLabel, warningLabelKo } from '#lib/i18n.ts';
	import StaticMap from '#lib/StaticMap.svelte';
	import StatusBadge from '#lib/StatusBadge.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
	const i = $derived(data.incident);
	const hasCoords = $derived(i.latitude !== null && i.longitude !== null);
	const kakaoUrl = $derived(hasCoords ? `https://map.kakao.com/link/map/${encodeURIComponent(i.receipt_no)},${i.latitude},${i.longitude}` : '');
	const googleUrl = $derived(hasCoords ? `https://www.google.com/maps/search/?api=1&query=${i.latitude},${i.longitude}` : '');
	const level = $derived(i.soc !== null ? { label: '배터리', value: i.soc } : i.fuel_percent !== null ? { label: '연료', value: i.fuel_percent } : null);
	let submitting = $state(false);
</script>

<svelte:head>
	<title>{i.receipt_no} · RentCare</title>
</svelte:head>

<a class="back" href="/vendor">← 신고 목록</a>

<header class="head card">
	<div class="title">
		<div class="tags">
			<StatusBadge status={i.status} />
			{#if i.demo}<span class="badge demo">데모 접수</span>{/if}
		</div>
		<h1>{typeLabel(i.type, 'ko')}</h1>
		<p class="receipt">{i.receipt_no}</p>
	</div>
	<dl class="meta">
		<div><dt>접수</dt><dd>{fmtKst(i.created_at)}</dd></div>
		<div><dt>발생</dt><dd>{fmtKst(i.occurred_at)}</dd></div>
		<div><dt>사용자 언어</dt><dd>{LANGUAGE_NAMES_KO[i.language] ?? i.language}</dd></div>
		<div><dt>차량</dt><dd>{i.vehicle_model ?? '-'} · {i.vehicle_plate}</dd></div>
	</dl>
</header>

<div class="layout">
	<div class="main">
		<section class="card">
			<h2>사용자 메모 <small>원문 · 자동 번역하지 않음</small></h2>
			{#if i.note}
				<blockquote class="note" lang={i.language}>{i.note}</blockquote>
			{:else}
				<p class="muted">메모 없음</p>
			{/if}

			<h3>접수 시 켜진 차량 경고등</h3>
			{#if i.warnings?.length}
				<ul class="chips">
					{#each i.warnings as w (w)}<li title={w}>{warningLabelKo(w)}</li>{/each}
				</ul>
			{:else}
				<p class="muted">없음 또는 확인 불가</p>
			{/if}
		</section>

		<section class="card">
			<h2>위치</h2>
			{#if hasCoords}
				<StaticMap lat={Number(i.latitude)} lon={Number(i.longitude)} tileBase="/vendor/map" height={300} />
				<p class="address">{i.address ?? `${i.latitude}, ${i.longitude}`}</p>
				<div class="row-links">
					<a class="pill" href={kakaoUrl} target="_blank" rel="noopener noreferrer">카카오맵에서 열기</a>
					<a class="pill" href={googleUrl} target="_blank" rel="noopener noreferrer">구글 지도에서 열기</a>
				</div>
				<dl class="kv">
					<dt>좌표</dt><dd>{i.latitude}, {i.longitude}</dd>
					<dt>출처</dt><dd>{i.location_source ? LOCATION_SOURCE_LABELS_KO[i.location_source] : '-'}</dd>
					<dt>읽은 시각</dt><dd>{fmtKst(i.location_read_at)}</dd>
					<dt>사용자 수정</dt>
					<dd>{#if i.location_edited}<span class="badge demo">사용자가 수정함</span>{:else}수정 안 함{/if}</dd>
				</dl>
			{:else}
				<p class="muted">위치를 읽지 못했습니다.</p>
			{/if}
		</section>

		<section class="card">
			<h2>첨부 <span class="count">{data.attachments.length}</span></h2>
			{#if data.attachments.length}
				<ul class="files">
					{#each data.attachments as a, n (a.id)}
						<li>
							<a href={`/vendor/files/${a.id}`} download class="file">
								{#if a.mime_type.startsWith('image/') && a.mime_type !== 'image/heic'}
									<img src={`/vendor/files/${a.id}`} alt={`첨부 ${n + 1}`} loading="lazy" />
								{:else}
									<span class="ph" aria-hidden="true">{a.mime_type.startsWith('video/') ? '▶' : '📄'}</span>
								{/if}
								<span class="fname">파일 {n + 1} · {a.source === 'CAR' ? '차량' : '휴대폰'}</span>
								<span class="muted small">{fmtBytes(a.size_bytes)} · {fmtKst(a.created_at)}</span>
							</a>
						</li>
					{/each}
				</ul>
			{:else}
				<p class="muted">첨부 파일이 없습니다.</p>
			{/if}
		</section>
	</div>

	<aside class="side">
		<section class="card ack" class:done={i.status === 'ACKNOWLEDGED'}>
			<h2>업체 확인</h2>
			{#if i.status === 'ACKNOWLEDGED'}
				<p class="ack-done">✔ {fmtKst(i.acknowledged_at)}<br /><span class="muted">{i.acknowledged_by ?? '-'}</span></p>
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
					<button type="submit" class="primary" disabled={submitting}>{submitting ? '처리 중…' : '확인했습니다'}</button>
				</form>
			{/if}
			<p class="muted small">"확인"은 신고를 보았다는 표시이며 출동·수리 확정이 아닙니다. 차량 화면에 표시됩니다.</p>
		</section>

		<section class="card">
			<h2>대여·차량</h2>
			<dl class="kv">
				<dt>대여 번호</dt><dd>{i.rental_id}</dd>
				<dt>차종</dt><dd>{i.vehicle_model ?? '-'}</dd>
				<dt>차량 번호</dt><dd>{i.vehicle_plate}</dd>
				<dt>동력원</dt><dd>{i.powertrain === 'EV' ? '전기차' : i.powertrain === 'GASOLINE' ? '휘발유' : (i.powertrain ?? '-')}</dd>
				<dt>반납 장소</dt><dd>{i.return_place ?? '-'}</dd>
				<dt>반납 예정</dt><dd>{fmtKst(i.return_at)}</dd>
			</dl>
		</section>

		<section class="card">
			<h2>차량 상태</h2>
			{#if i.vehicle_read && level}
				<div class="gauge">
					<div class="gauge-top"><span>{level.label}</span><strong>{level.value}%</strong></div>
					<div class="bar" role="img" aria-label={`${level.label} ${level.value}%`}>
						<span style:width="{Math.max(0, Math.min(100, level.value))}%"></span>
					</div>
				</div>
				<p class="muted small">읽은 시각 {fmtKst(i.vehicle_read_at)}</p>
			{:else}
				<p class="muted">차량 정보를 읽지 못했습니다.</p>
			{/if}
		</section>
	</aside>
</div>

<style>
	.back {
		display: inline-block;
		margin: 4px 0 14px;
		text-decoration: none;
		color: var(--muted);
	}
	.back:hover {
		color: var(--fg);
	}
	.card {
		background: #fff;
		border: 1px solid var(--border);
		border-radius: 20px;
		padding: 20px 22px;
	}
	.head {
		display: flex;
		flex-wrap: wrap;
		justify-content: space-between;
		gap: 16px 32px;
		margin-bottom: 16px;
		background: linear-gradient(180deg, #f2faf9, #fff);
	}
	.tags {
		display: flex;
		gap: 6px;
		align-items: center;
	}
	h1 {
		margin: 8px 0 2px;
		font-size: 1.6rem;
	}
	.receipt {
		margin: 0;
		color: var(--muted);
		font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	}
	.meta {
		display: grid;
		grid-template-columns: repeat(2, minmax(150px, auto));
		gap: 10px 28px;
		margin: 0;
		align-content: center;
	}
	.meta dt {
		color: var(--muted);
		font-size: 0.8rem;
	}
	.meta dd {
		margin: 0;
		font-weight: 600;
	}
	.layout {
		display: grid;
		grid-template-columns: minmax(0, 1fr) 340px;
		gap: 16px;
		align-items: start;
	}
	@media (max-width: 900px) {
		.layout {
			grid-template-columns: 1fr;
		}
		.side {
			order: -1;
		}
	}
	.main,
	.side {
		display: grid;
		gap: 16px;
	}
	h2 {
		margin: 0 0 12px;
		font-size: 1.05rem;
	}
	h2 small {
		font-weight: 400;
		color: var(--muted);
		font-size: 0.8rem;
		margin-left: 6px;
	}
	h3 {
		margin: 18px 0 8px;
		font-size: 0.95rem;
	}
	.count {
		display: inline-block;
		min-width: 1.6em;
		padding: 0 6px;
		border-radius: 999px;
		background: var(--panel);
		text-align: center;
		font-size: 0.85rem;
	}
	.note {
		margin: 0;
		padding: 14px 16px;
		background: var(--panel);
		border-radius: 14px;
		white-space: pre-wrap;
		overflow-wrap: anywhere;
		font-size: 1.05rem;
	}
	.chips {
		list-style: none;
		padding: 0;
		margin: 0;
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}
	.chips li {
		padding: 4px 12px;
		border-radius: 999px;
		background: #fff4e0;
		border: 1px solid #f0b860;
		font-size: 0.9rem;
	}
	.address {
		margin: 0 0 8px;
		font-weight: 600;
	}
	.row-links {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
		margin-bottom: 14px;
	}
	.pill {
		padding: 6px 14px;
		border-radius: 999px;
		border: 1px solid var(--border);
		text-decoration: none;
		font-size: 0.9rem;
	}
	.pill:hover {
		background: var(--panel);
	}
	.kv {
		display: grid;
		grid-template-columns: 6.5rem 1fr;
		gap: 6px 12px;
		margin: 0;
	}
	.kv dt {
		color: var(--muted);
	}
	.kv dd {
		margin: 0;
		overflow-wrap: anywhere;
	}
	.files {
		list-style: none;
		padding: 0;
		margin: 0;
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
		gap: 12px;
	}
	.file {
		display: grid;
		gap: 4px;
		text-decoration: none;
		color: var(--fg);
	}
	.file img,
	.ph {
		width: 100%;
		aspect-ratio: 4 / 3;
		object-fit: cover;
		border-radius: 12px;
		border: 1px solid var(--border);
		background: var(--panel);
	}
	.ph {
		display: grid;
		place-items: center;
		font-size: 1.8rem;
		color: var(--muted);
	}
	.file:hover img,
	.file:hover .ph {
		border-color: #00756f;
	}
	.fname {
		font-weight: 600;
		font-size: 0.9rem;
	}
	.ack .primary {
		width: 100%;
		margin: 0 0 10px;
		padding: 14px;
		border-radius: 999px;
		border: 0;
		background: #00756f;
		color: #fff;
		font-size: 1.05rem;
		font-weight: 700;
	}
	.ack .primary:disabled {
		opacity: 0.6;
	}
	.ack.done {
		border-color: #a6ddb9;
		background: #f1faf4;
	}
	.ack-done {
		margin: 0 0 10px;
		color: #00753a;
		font-weight: 700;
	}
	.gauge-top {
		display: flex;
		justify-content: space-between;
		margin-bottom: 6px;
	}
	.gauge-top strong {
		font-size: 1.3rem;
	}
	.bar {
		height: 10px;
		border-radius: 999px;
		background: var(--panel);
		overflow: hidden;
	}
	.bar span {
		display: block;
		height: 100%;
		background: #00756f;
		border-radius: 999px;
	}
	.muted {
		color: var(--muted);
	}
	.small {
		font-size: 0.82rem;
	}
</style>
