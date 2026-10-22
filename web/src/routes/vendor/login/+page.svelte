<script lang="ts">
	import type { PageProps } from './$types';

	let { form }: PageProps = $props();
</script>

<svelte:head>
	<title>업체 로그인 · RentCare</title>
</svelte:head>

<div class="login">
	<h1>업체 직원 로그인</h1>
	<form method="POST">
		<label for="token">직원 토큰</label>
		<input id="token" name="token" type="password" autocomplete="current-password" required />
		<label for="staff">담당자 이름 (선택)</label>
		<input id="staff" name="staff" maxlength="40" autocomplete="name" />
		{#if form?.message}<p class="error" role="alert">{form.message}</p>{/if}
		<button type="submit">로그인</button>
	</form>
</div>

<style>
	.login {
		max-width: 380px;
		margin: 2rem auto;
	}
</style>
