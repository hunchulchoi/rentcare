<script lang="ts">
	import CodeBoxes from '#lib/CodeBoxes.svelte';
	import Logo from '#lib/Logo.svelte';
	import { enhance } from '$app/forms';
	import { onMount } from 'svelte';
	import { REPORT_STRINGS, reportError, typeLabel, type UiLang } from '#lib/i18n.ts';
	import { RENTAL_CODE_LENGTH } from '#lib/rental-code.ts';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();
	let ready = $state(false);
	onMount(() => (ready = true));

	const lang: UiLang = $derived(data.lang);
	const t = $derived(REPORT_STRINGS[lang]);

	let code = $state('');
	let codeForm = $state<HTMLFormElement>();
	let busy = $state(false);
	// The verified rental is kept in memory; the code goes back in the POST body only.
	let rental = $state<{ code: string; plate: string; model: string | null; vendorName: string; demo: boolean } | null>(null);
	let type = $state('');
	let note = $state('');
	let consent = $state(false);
	let loc = $state<{ lat: number; lon: number; accuracy: number } | null>(null);
	let locState = $state<'idle' | 'saving' | 'denied'>('idle');

	const done = $derived(form?.step === 'done' ? form : null);
	const error = $derived(form && 'error' in form ? (form.error as string) : null);

	function addLocation() {
		if (!navigator.geolocation) return void (locState = 'denied');
		locState = 'saving';
		navigator.geolocation.getCurrentPosition(
			(p) => {
				loc = { lat: p.coords.latitude, lon: p.coords.longitude, accuracy: Math.min(5000, Math.round(p.coords.accuracy)) };
				locState = 'idle';
			},
			() => (locState = 'denied'),
			{ enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 }
		);
	}
</script>

<svelte:head>
	<title>RentCare · {t.title}</title>
	<meta name="robots" content="noindex" />
</svelte:head>

<main class="mobile" {lang} data-ready={ready ? '1' : undefined}>
	<Logo height={36} />
	<h1>{t.title}</h1>

	{#if done}
		<div class="notice" role="status" data-receipt={done.receiptNo}>
			<p><strong>{t.done}</strong></p>
			<dl>
				<dt>{t.receiptNo}</dt>
				<dd><strong>{done.receiptNo}</strong></dd>
			</dl>
		</div>
		<p>{t.attachIntro}</p>
		<p class="attach-code" aria-label={t.attachCode} data-attach-code={done.attachCode}>{done.attachCode}</p>
		<a class="button" href={`/m?lang=${lang}`}>{t.attachLink}</a>
		<p class="hint">{t.statusNote}</p>
	{:else if !rental}
		<p>{t.intro}</p>
		<form
			bind:this={codeForm}
			method="POST"
			action={`?lang=${lang}&/verify`}
			use:enhance={() => {
				busy = true;
				return async ({ result, update }) => {
					if (result.type === 'success' && result.data?.step === 'verified') {
						const d = result.data as { code: string; rental: { plate: string; model: string | null; vendorName: string; demo: boolean } };
						rental = { code: d.code, ...d.rental };
					}
					await update({ reset: false });
					busy = false;
				};
			}}
		>
			<p class="code-label">{t.codeLabel}</p>
			<CodeBoxes bind:value={code} length={RENTAL_CODE_LENGTH} label={t.codeLabel} disabled={busy} invalid={!!error} oncomplete={() => codeForm?.requestSubmit()} />
			{#if error}<p class="error" role="alert">{reportError(lang, error)}</p>{/if}
			<button type="submit" disabled={busy || code.length !== RENTAL_CODE_LENGTH}>{busy ? t.verifying : t.verify}</button>
		</form>
	{:else}
		<div class="car">
			<dl>
				<dt>{t.vehicle}</dt>
				<dd>{rental.model ?? ''} <strong>{rental.plate}</strong>{#if rental.demo} <span class="badge">DEMO</span>{/if}</dd>
				<dt>{t.vendor}</dt>
				<dd>{rental.vendorName}</dd>
			</dl>
		</div>
		<form
			method="POST"
			action={`?lang=${lang}&/report`}
			use:enhance={() => {
				busy = true;
				return async ({ update }) => {
					await update({ reset: false });
					busy = false;
				};
			}}
		>
			<input type="hidden" name="code" value={rental.code} />
			<input type="hidden" name="key" value={data.key} />
			<input type="hidden" name="lang" value={lang} />
			<fieldset class="types">
				<legend>{t.type}</legend>
				{#each data.types as k (k)}
					<label class="type" class:on={type === k}>
						<input type="radio" name="type" value={k} bind:group={type} required />
						{typeLabel(k, lang)}
					</label>
				{/each}
			</fieldset>

			<label for="note" class="field">{t.note}</label>
			<textarea id="note" name="note" rows="3" maxlength="2000" placeholder={t.notePlaceholder} bind:value={note}></textarea>

			<section class="loc">
				<p class="field">{t.locationTitle}</p>
				{#if loc}
					<input type="hidden" name="lat" value={loc.lat} />
					<input type="hidden" name="lon" value={loc.lon} />
					<input type="hidden" name="accuracy" value={loc.accuracy} />
					<p class="ok" role="status">{t.locationAdded.replace('{m}', String(loc.accuracy))}</p>
					<button type="button" class="link" onclick={() => (loc = null)}>{t.locationRemove}</button>
				{:else}
					<button type="button" class="secondary" onclick={addLocation} disabled={locState === 'saving'}>
						{locState === 'saving' ? t.locationSaving : t.locationButton}
					</button>
					{#if locState === 'denied'}<p class="hint" role="alert">{t.locationDenied}</p>{/if}
					<noscript><p class="hint">{t.noscript}</p></noscript>
				{/if}
			</section>

			<label class="consent">
				<input type="checkbox" name="consent" value="yes" bind:checked={consent} required />
				<span>{t.consent.replace('{vendor}', rental.vendorName)}</span>
			</label>
			{#if error}<p class="error" role="alert">{reportError(lang, error)}</p>{/if}
			<button type="submit" disabled={busy || !type || !consent}>{busy ? t.submitting : t.submit}</button>
		</form>
	{/if}
	{#if rental || done}<p><a href={`/r?lang=${lang}`} data-sveltekit-reload>{t.otherCode}</a></p>{/if}
</main>

<style>
	.mobile {
		max-width: 520px;
	}
	.code-label,
	.field {
		font-weight: 600;
		margin: 0.9rem 0 0.3rem;
		display: block;
	}
	.car,
	.notice {
		background: var(--panel);
		border-radius: 14px;
		padding: 0.75rem 1rem;
		margin-bottom: 0.5rem;
	}
	.notice {
		background: var(--ok-bg);
		border: 1px solid var(--ok-border);
	}
	dl {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: 0.25rem 1rem;
		margin: 0;
	}
	dd {
		margin: 0;
	}
	.types {
		border: 0;
		padding: 0;
		margin: 0.9rem 0 0;
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 8px;
	}
	.types legend {
		font-weight: 600;
		margin-bottom: 0.3rem;
		padding: 0;
	}
	.type {
		display: flex;
		align-items: center;
		gap: 8px;
		border: 2px solid var(--border);
		border-radius: 14px;
		padding: 0.7rem 0.8rem;
		cursor: pointer;
	}
	.type input {
		width: 1.1rem;
		height: 1.1rem;
		margin: 0;
		flex: none;
		accent-color: #00756f;
	}
	.type.on {
		border-color: #00756f;
		background: #e9f6f4;
		font-weight: 600;
	}
	textarea {
		width: 100%;
		box-sizing: border-box;
		border-radius: 14px;
	}
	.consent {
		display: flex;
		gap: 10px;
		align-items: flex-start;
		margin: 1rem 0 0.5rem;
		font-size: 0.92rem;
	}
	.consent input {
		width: 1.2rem;
		height: 1.2rem;
		flex: none;
		margin-top: 0.15rem;
	}
	.attach-code {
		font: 700 2rem/1.2 ui-monospace, SFMono-Regular, Menlo, monospace;
		letter-spacing: 0.25em;
		margin: 0.25rem 0 0.75rem;
	}
	.hint {
		color: var(--muted);
		font-size: 0.85rem;
	}
	.ok {
		color: var(--ok);
		font-weight: 600;
	}
	.badge {
		font-size: 0.75rem;
		background: #fff3c4;
		border-radius: 999px;
		padding: 0.1rem 0.5rem;
	}
	.button {
		display: block;
		text-align: center;
	}
</style>
