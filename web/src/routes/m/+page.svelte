<script lang="ts">
	import CodeBoxes from '#lib/CodeBoxes.svelte';
	import Logo from '#lib/Logo.svelte';
	import { enhance } from '$app/forms';
	import { ACCEPT_ATTR, MAX_FILE_BYTES, resolveMime } from '#lib/files.ts';
	import { MOBILE_STRINGS, mobileError, typeLabel, type UiLang } from '#lib/i18n.ts';
	import { fmtBytes } from '#lib/format.ts';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();

	type ItemStatus = 'waiting' | 'uploading' | 'done' | 'failed';
	interface Item {
		id: number;
		file: File;
		status: ItemStatus;
		progress: number;
		error?: string;
	}

	let code = $state('');
	let formEl = $state<HTMLFormElement>();
	let verifiedCode = $state(''); // kept in memory only; sent in POST bodies
	let verifying = $state(false);
	let uploading = $state(false);
	let items = $state<Item[]>([]);
	let attached = $state(0);
	let nextId = 0;

	const verified = $derived(form && 'verified' in form && form.verified ? form : null);
	const lang: UiLang = $derived(verified?.language ?? data.lang);
	const t = $derived(MOBILE_STRINGS[lang]);
	const maxFiles = $derived(verified?.maxFiles ?? 10);
	const pending = $derived(items.filter((i) => i.status === 'waiting' || i.status === 'failed'));
	const allDone = $derived(items.length > 0 && items.every((i) => i.status === 'done'));

	$effect(() => {
		if (verified) attached = verified.attachments;
	});

	function pick(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		for (const file of input.files ?? []) {
			const item: Item = { id: nextId++, file, status: 'waiting', progress: 0 };
			if (!resolveMime(file.type, file.name)) {
				item.status = 'failed';
				item.error = mobileError(lang, 'unsupported_type');
			} else if (file.size > MAX_FILE_BYTES) {
				item.status = 'failed';
				item.error = mobileError(lang, 'too_large');
			}
			items.push(item);
		}
		input.value = '';
	}

	function send(item: Item): Promise<void> {
		return new Promise((resolve) => {
			const body = new FormData();
			body.append('code', verifiedCode);
			body.append('file', item.file);
			const xhr = new XMLHttpRequest();
			xhr.open('POST', '/m/upload');
			xhr.responseType = 'json';
			xhr.upload.onprogress = (ev) => {
				if (ev.lengthComputable) item.progress = Math.round((ev.loaded / ev.total) * 100);
			};
			xhr.onload = () => {
				const res = xhr.response as { attachments?: number; error?: string } | null;
				if (xhr.status === 201 && res) {
					item.status = 'done';
					item.error = undefined;
					attached = res.attachments ?? attached + 1;
				} else {
					item.status = 'failed';
					item.error = mobileError(lang, res?.error);
				}
				resolve();
			};
			xhr.onerror = () => {
				item.status = 'failed';
				item.error = mobileError(lang, 'network');
				resolve();
			};
			item.status = 'uploading';
			item.progress = 0;
			xhr.send(body);
		});
	}

	async function uploadAll() {
		uploading = true;
		for (const item of items) {
			if (item.status !== 'waiting' && item.status !== 'failed') continue;
			if (!resolveMime(item.file.type, item.file.name) || item.file.size > MAX_FILE_BYTES) continue;
			await send(item);
		}
		uploading = false;
	}

	function remove(id: number) {
		items = items.filter((i) => i.id !== id);
	}

	function statusText(s: ItemStatus) {
		return { waiting: t.statusWaiting, uploading: t.statusUploading, done: t.statusDone, failed: t.statusFailed }[s];
	}
</script>

<svelte:head>
	<title>RentCare · {t.title}</title>
	<meta name="robots" content="noindex" />
</svelte:head>

<main class="mobile" lang={lang}>
	<Logo height={36} />
	<h1>{t.title}</h1>

	{#if !verified}
		<p>{t.intro}</p>
		<form
			bind:this={formEl}
			method="POST"
			action="?/verify"
			use:enhance={() => {
				verifying = true;
				const submitted = code;
				return async ({ result, update }) => {
					if (result.type === 'success') verifiedCode = submitted;
					await update({ reset: false });
					verifying = false;
				};
			}}
		>
			<p class="code-label" id="code-label">{t.codeLabel}</p>
			<CodeBoxes
				bind:value={code}
				label={t.codeLabel}
				disabled={verifying}
				invalid={!!(form && 'error' in form && form.error)}
				oncomplete={() => formEl?.requestSubmit()}
			/>
			{#if form && 'error' in form && form.error}
				<p class="error" role="alert">{mobileError(lang, form.error)}</p>
			{/if}
			<button type="submit" disabled={verifying || code.length !== 6}>{verifying ? t.verifying : t.verify}</button>
		</form>
	{:else}
		<div class="notice" role="status">
			<p>{t.receivedNotice}</p>
			<dl>
				<dt>{t.receiptNo}</dt>
				<dd><strong>{verified.receiptNo}</strong></dd>
				<dt>{t.type}</dt>
				<dd>{typeLabel(verified.type, lang)}</dd>
				<dt>{t.alreadyAttached}</dt>
				<dd>{attached} / {maxFiles}</dd>
			</dl>
		</div>

		<noscript><p class="error">{t.noscript}</p></noscript>

		<label class="file-pick">
			<span class="button secondary">{t.chooseFiles}</span>
			<input type="file" multiple accept={ACCEPT_ATTR} onchange={pick} disabled={uploading} />
		</label>
		<p class="hint">{t.limits}</p>

		{#if items.length}
			<ul class="files">
				{#each items as item (item.id)}
					<li class={item.status}>
						<div class="row">
							<span class="name">{item.file.name}</span>
							<span class="size">{fmtBytes(item.file.size)}</span>
						</div>
						<div class="row">
							<span class="status">
								{statusText(item.status)}{item.status === 'uploading' ? ` ${item.progress}%` : ''}
							</span>
							{#if item.status !== 'uploading' && item.status !== 'done' && !uploading}
								<button type="button" class="link" onclick={() => remove(item.id)} aria-label="remove">✕</button>
							{/if}
						</div>
						{#if item.error}<p class="error">{item.error}</p>{/if}
					</li>
				{/each}
			</ul>
			<button type="button" onclick={uploadAll} disabled={uploading || pending.length === 0}>
				{uploading ? t.uploading : items.some((i) => i.status === 'failed') ? t.retry : t.upload}
			</button>
		{/if}

		{#if allDone}
			<p class="ok" role="status">{t.allDone}</p>
		{/if}

		<p><a href={`/m?lang=${lang}`} data-sveltekit-reload>{t.otherCode}</a></p>
	{/if}
</main>

<style>
	.mobile {
		max-width: 480px;
	}
	.code-label {
		font-weight: 600;
		margin: 0.75rem 0 0.25rem;
	}
	.notice {
		background: var(--ok-bg);
		border: 1px solid var(--ok-border);
		border-radius: 8px;
		padding: 0.75rem 1rem;
		margin-bottom: 1rem;
	}
	.notice dl {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: 0.25rem 1rem;
		margin: 0.5rem 0 0;
	}
	.notice dd {
		margin: 0;
	}
	.file-pick input {
		position: absolute;
		width: 1px;
		height: 1px;
		opacity: 0;
	}
	.file-pick .button {
		display: block;
		text-align: center;
	}
	.hint {
		color: var(--muted);
		font-size: 0.85rem;
	}
	.files {
		list-style: none;
		padding: 0;
		margin: 1rem 0;
	}
	.files li {
		border: 1px solid var(--border);
		border-radius: 8px;
		padding: 0.5rem 0.75rem;
		margin-bottom: 0.5rem;
	}
	.files li.done {
		border-color: var(--ok-border);
	}
	.files li.failed {
		border-color: var(--err);
	}
	.row {
		display: flex;
		justify-content: space-between;
		gap: 0.5rem;
	}
	.name {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.size,
	.status {
		color: var(--muted);
		font-size: 0.85rem;
		white-space: nowrap;
	}
	.ok {
		color: var(--ok);
		font-weight: 600;
	}
</style>
