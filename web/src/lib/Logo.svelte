<script lang="ts">
	import logo from '#lib/assets/logo.png';

	let { height = 40 }: { height?: number } = $props();
</script>

<img src={logo} alt="렌트케어 RentCare" {height} style:height="{height}px" style:width="auto" />
