import { pickUiLang } from '#lib/i18n.ts';
import type { PageServerLoad } from './$types';

// Language: ?lang=ko|en, otherwise the browser's Accept-Language.
export const load: PageServerLoad = ({ url, request }) => {
	const explicit = url.searchParams.get('lang');
	return { lang: pickUiLang(explicit, request.headers.get('accept-language')), explicit };
};
