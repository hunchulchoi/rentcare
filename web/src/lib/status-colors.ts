// Incident status badge colors, same meaning as the car app (Pleos informative colors, darkened for 4.5:1):
// received = blue (informative_positive), staff seen = purple (informative_autonomous_driving),
// in progress = orange (regulation_orange), done = green (informative_active). All darkened for 4.5:1.
import type { IncidentStatus } from './i18n.ts';

export const STATUS_COLORS: Record<IncidentStatus, { bg: string; fg: string }> = {
	RECEIVED: { bg: '#0052cc', fg: '#ffffff' },
	ACKNOWLEDGED: { bg: '#5a46fa', fg: '#ffffff' },
	IN_PROGRESS: { bg: '#b34d00', fg: '#ffffff' },
	RESOLVED: { bg: '#00753a', fg: '#ffffff' }
};

function luminance(hex: string): number {
	const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((x) => (x <= 0.03928 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4));
	return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
}

export function contrast(a: string, b: string): number {
	const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
	return (hi + 0.05) / (lo + 0.05);
}
