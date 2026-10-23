<script lang="ts">
	import { onMount } from 'svelte';
	import { ATTACH_CODE_LENGTH, cleanCodeInput } from '#lib/attach-code.ts';

	// One real input (paste, autofill, IME and screen readers work) drawn as 6 boxes.
	let {
		value = $bindable(''),
		name = 'code',
		label,
		disabled = false,
		invalid = false,
		oncomplete
	}: { value?: string; name?: string; label: string; disabled?: boolean; invalid?: boolean; oncomplete?: () => void } = $props();

	let focused = $state(false);
	// Interactive marker for scripts/browser-login-check.mjs.
	let ready = $state(false);
	onMount(() => (ready = true));
	let input: HTMLInputElement;
	const active = $derived(Math.min(value.length, ATTACH_CODE_LENGTH - 1));

	function onInput(e: Event & { currentTarget: HTMLInputElement }) {
		const next = cleanCodeInput(e.currentTarget.value);
		const completed = next.length === ATTACH_CODE_LENGTH && value.length !== ATTACH_CODE_LENGTH;
		value = next;
		e.currentTarget.value = next; // drop rejected characters from the field too
		if (completed) oncomplete?.();
	}
</script>

<div class="boxes" class:focused class:invalid class:disabled data-ready={ready ? '1' : undefined}>
	<input
		bind:this={input}
		{name}
		{value}
		{disabled}
		oninput={onInput}
		onfocus={() => (focused = true)}
		onblur={() => (focused = false)}
		onclick={() => input.setSelectionRange(value.length, value.length)}
		aria-label={label}
		aria-invalid={invalid}
		inputmode="text"
		autocomplete="one-time-code"
		autocapitalize="characters"
		autocorrect="off"
		spellcheck="false"
		enterkeyhint="go"
		required
	/>
	{#each Array(ATTACH_CODE_LENGTH) as _, k (k)}
		<span class="box" class:filled={!!value[k]} class:active={focused && k === active} aria-hidden="true">{value[k] ?? ''}</span>
	{/each}
</div>

<style>
	.boxes {
		position: relative;
		display: grid;
		grid-template-columns: repeat(6, 1fr);
		gap: 8px;
		max-width: 360px;
		margin: 6px 0 4px;
	}
	input {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		opacity: 0;
		/* keep it focusable and tappable; text is drawn in the boxes */
		font-size: 16px; /* no iOS zoom on focus */
		border: 0;
		padding: 0;
		cursor: text;
		caret-color: transparent;
	}
	.box {
		aspect-ratio: 4 / 5;
		display: grid;
		place-items: center;
		border: 2px solid var(--border);
		border-radius: 14px;
		background: #fff;
		font: 700 1.6rem/1 ui-monospace, SFMono-Regular, Menlo, monospace;
		color: var(--fg);
		transition: border-color 0.1s;
	}
	.box.filled {
		border-color: #9fb3c8;
	}
	.box.active {
		border-color: #00756f;
		box-shadow: 0 0 0 3px #d3f2ef;
	}
	.invalid .box {
		border-color: var(--err);
	}
	.disabled .box {
		opacity: 0.6;
	}
</style>
