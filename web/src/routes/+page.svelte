<script lang="ts">
	import Logo from '#lib/Logo.svelte';
	import { HOME_STRINGS, photoHref } from '#lib/home.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
	const t = $derived(HOME_STRINGS[data.lang]);
	const steps = $derived([
		{ n: 1, title: t.step1Title, body: t.step1 },
		{ n: 2, title: t.step2Title, body: t.step2 },
		{ n: 3, title: t.step3Title, body: t.step3 }
	]);
</script>

<svelte:head>
	<title>{t.title}</title>
	<meta name="description" content={t.tagline} />
</svelte:head>

<div class="page" lang={data.lang}>
	<header>
		<Logo height={44} />
		<nav aria-label={t.language}>
			<a href="?lang=ko" aria-current={data.lang === 'ko' ? 'true' : undefined}>한국어</a>
			<a href="?lang=en" aria-current={data.lang === 'en' ? 'true' : undefined}>English</a>
		</nav>
	</header>

	<section class="hero">
		<p class="tagline">{t.tagline}</p>
		<a class="cta" href={photoHref(data.explicit)}>{t.photoCta}</a>
		<p class="note">{t.photoNote}</p>
	</section>

	<section>
		<h2>{t.stepsTitle}</h2>
		<ol class="steps">
			{#each steps as s (s.n)}
				<li>
					<span class="num" aria-hidden="true">{s.n}</span>
					<div>
						<h3>{s.title}</h3>
						<p>{s.body}</p>
					</div>
				</li>
			{/each}
		</ol>
	</section>

	<p class="emergency" role="note">{t.emergency}</p>
	<footer>{t.demo}</footer>
</div>

<style>
	.page {
		--brand: #00756f;
		--brand-soft: #e3f4f2;
		max-width: 880px;
		margin: 0 auto;
		padding: 20px 16px 40px;
	}
	header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		flex-wrap: wrap;
	}
	nav {
		display: flex;
		gap: 4px;
		background: var(--panel);
		border-radius: 999px;
		padding: 4px;
	}
	nav a {
		padding: 6px 14px;
		border-radius: 999px;
		text-decoration: none;
		color: var(--muted);
		font-size: 0.95rem;
	}
	nav a[aria-current='true'] {
		background: #fff;
		color: var(--fg);
		font-weight: 600;
		box-shadow: 0 1px 3px rgb(0 0 0 / 0.12);
	}
	.hero {
		margin: 36px 0 28px;
		padding: 32px 24px;
		border-radius: 28px;
		background: var(--brand-soft);
	}
	.tagline {
		font-size: clamp(1.25rem, 3.6vw, 1.75rem);
		font-weight: 700;
		line-height: 1.35;
		margin: 0 0 24px;
		word-break: keep-all;
	}
	.cta {
		display: inline-block;
		padding: 16px 28px;
		border-radius: 999px;
		background: var(--brand);
		color: #fff;
		font-weight: 700;
		font-size: 1.1rem;
		text-decoration: none;
	}
	.cta:hover {
		filter: brightness(1.08);
	}
	.cta:focus-visible {
		outline: 3px solid var(--fg);
		outline-offset: 3px;
	}
	.note {
		margin: 14px 0 0;
		color: var(--muted);
	}
	h2 {
		margin: 0 0 12px;
	}
	.steps {
		list-style: none;
		padding: 0;
		margin: 0;
		display: grid;
		gap: 12px;
		grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
	}
	.steps li {
		display: flex;
		gap: 14px;
		padding: 20px;
		border: 1px solid var(--border);
		border-radius: 22px;
		background: #fff;
	}
	.num {
		flex: none;
		width: 32px;
		height: 32px;
		border-radius: 50%;
		background: var(--brand);
		color: #fff;
		display: grid;
		place-items: center;
		font-weight: 700;
	}
	h3 {
		margin: 2px 0 6px;
		font-size: 1.05rem;
	}
	.steps p {
		margin: 0;
		color: var(--muted);
		word-break: keep-all;
	}
	.emergency {
		margin: 24px 0 0;
		padding: 14px 18px;
		border-radius: 16px;
		background: var(--warn-bg);
		border: 1px solid var(--warn-border);
		font-weight: 600;
	}
	footer {
		margin-top: 28px;
		color: var(--muted);
		font-size: 0.85rem;
	}
</style>
