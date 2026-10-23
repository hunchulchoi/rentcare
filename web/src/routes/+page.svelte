<svelte:head>
	<title>RentCare</title>
</svelte:head>

<script lang="ts">
	import Logo from '#lib/Logo.svelte';
</script>

<main>
	<h1><Logo height={64} /></h1>
	<ul>
		<li><a href="/m?lang=en">Add photos to a report (English)</a></li>
		<li><a href="/m?lang=ko">신고에 사진 추가 (한국어)</a></li>
		<li><a href="/vendor">렌터카 업체 화면</a></li>
	</ul>
</main>
