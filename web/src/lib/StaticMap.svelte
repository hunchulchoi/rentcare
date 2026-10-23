<script lang="ts">
	import { grid } from '#lib/map-tiles.ts';

	// Static (no pan/zoom) map centered on the point, tiles from our own proxy.
	let {
		lat, lon, tileBase, zoom = 16, height = 320, width = 1024, attribution = true, pin = 44
	}: { lat: number; lon: number; tileBase: string; zoom?: number; height?: number; width?: number; attribution?: boolean; pin?: number } = $props();
	const W = $derived(width); // drawn width; the frame clips it and keeps the point in the middle
	const tiles = $derived(grid(lat, lon, zoom, W, height));
</script>

<figure class="map" style:height="{height}px">
	<div class="canvas" style:width="{W}px" style:height="{height}px">
		{#each tiles as t (`${t.x}/${t.y}`)}
			<img src={`${tileBase}/${t.z}/${t.x}/${t.y}.png`} alt="" width="256" height="256" style:left="{t.left}px" style:top="{t.top}px" loading="lazy" />
		{/each}
		<svg class="pin" viewBox="0 0 24 24" width={pin} height={pin} aria-hidden="true">
			<path d="M12 2C8.1 2 5 5.1 5 9c0 5.2 7 13 7 13s7-7.8 7-13c0-3.9-3.1-7-7-7z" fill="#E5361B" stroke="#fff" stroke-width="1.5" />
			<circle cx="12" cy="9" r="2.6" fill="#fff" />
		</svg>
	</div>
	{#if attribution}
		<figcaption>© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors</figcaption>
	{/if}
</figure>

<style>
	.map {
		position: relative;
		overflow: hidden;
		margin: 0.5rem 0 1rem;
		border-radius: 20px;
		border: 1px solid var(--border);
		background: #e8eef0;
	}
	.canvas {
		position: absolute;
		left: 50%;
		top: 0;
		transform: translateX(-50%);
	}
	img {
		position: absolute;
		max-width: none;
	}
	.pin {
		position: absolute;
		left: 50%;
		top: 50%;
		transform: translate(-50%, -100%);
		filter: drop-shadow(0 2px 3px rgb(0 0 0 / 0.35));
	}
	figcaption {
		position: absolute;
		right: 0;
		bottom: 0;
		padding: 2px 8px;
		font-size: 0.75rem;
		background: rgb(255 255 255 / 0.85);
		border-top-left-radius: 8px;
	}
</style>
