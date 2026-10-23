<script lang="ts">
	import Logo from '#lib/Logo.svelte';
	import type { LayoutProps } from './$types';

	let { data, children }: LayoutProps = $props();
</script>

<svelte:head>
	<meta name="robots" content="noindex" />
</svelte:head>

<header class="vendor-bar">
	<a href="/vendor" class="brand"><Logo height={32} /> <span>업체용</span></a>
	{#if data.vendor}
		<span class="who">{data.vendor.name}{data.vendor.staff ? ` · ${data.vendor.staff}` : ''}</span>
		<form method="POST" action="/vendor?/logout">
			<button type="submit" class="secondary small">로그아웃</button>
		</form>
	{/if}
</header>

<main lang="ko" class="vendor-main">
	{@render children()}
</main>

<style>
	.vendor-main {
		max-width: 1280px;
	}
	.vendor-bar {
		display: flex;
		align-items: center;
		gap: 1rem;
		padding: 0.6rem 1rem;
		border-bottom: 1px solid var(--border);
		background: var(--panel);
		flex-wrap: wrap;
	}
	.brand {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		font-weight: 700;
		text-decoration: none;
		color: var(--fg);
	}
	.who {
		color: var(--muted);
		margin-left: auto;
	}
	.small {
		width: auto;
		margin: 0;
		padding: 0.3rem 0.7rem;
		font-size: 0.85rem;
	}
</style>
