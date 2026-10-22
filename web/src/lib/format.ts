// Display helpers for vendor screens (Korean, KST).

const dtf = new Intl.DateTimeFormat('ko-KR', {
	timeZone: 'Asia/Seoul',
	year: 'numeric',
	month: '2-digit',
	day: '2-digit',
	hour: '2-digit',
	minute: '2-digit',
	hour12: false
});

export function fmtKst(value: Date | string | null | undefined): string {
	if (!value) return '-';
	const d = value instanceof Date ? value : new Date(value);
	if (Number.isNaN(d.getTime())) return '-';
	return dtf.format(d);
}

export function fmtBytes(n: number): string {
	if (n < 1024) return `${n} B`;
	if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
	return `${(n / 1024 / 1024).toFixed(1)} MB`;
}
