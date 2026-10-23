<script lang="ts">
	import { STATUS_LABELS_KO, type IncidentStatus } from '#lib/i18n.ts';
	import { STATUS_COLORS } from '#lib/status-colors.ts';

	let { status }: { status: IncidentStatus } = $props();
	const c = $derived(STATUS_COLORS[status]);
</script>

<!-- Color supports the text label; it is never the only signal. -->
<span class="status" style:background={c.bg} style:color={c.fg}>{STATUS_LABELS_KO[status]}</span>

<style>
	.status {
		display: inline-block;
		padding: 0.15rem 0.65rem;
		border-radius: 999px;
		font-size: 0.8rem;
		font-weight: 700;
		white-space: nowrap;
	}
</style>
