// Public landing page (/). Renters only: the vendor console lives at /vendor and is not linked here.
import { isUiLang, type UiLang } from './i18n.ts';

const home = {
	ko: {
		title: '렌트케어',
		tagline: '렌터카 차량 화면에서 고장·사고를 접수하고, 반납 조건을 미리 확인하세요.',
		photoCta: '휴대폰으로 사진·영상 추가',
		photoNote: '차량 화면의 QR을 찍었거나 첨부 코드를 받았다면 여기서 사진을 보낼 수 있습니다.',
		stepsTitle: '이렇게 처리됩니다',
		step1Title: '차량에서 접수',
		step1: '정차한 뒤 차량 화면에서 상황을 고르면 대여·차량 정보와 위치가 함께 전달됩니다.',
		step2Title: '휴대폰으로 사진 추가',
		step2: '차량 화면의 6자리 코드를 입력해 사진·영상을 보냅니다. 링크에는 코드가 들어 있지 않습니다.',
		step3Title: '업체 확인',
		step3: '렌터카 업체 담당자가 확인하면 차량 화면에 표시됩니다. 확인은 출동 확정이 아닙니다.',
		emergency: '긴급 상황에서는 먼저 112·119에 연락하세요.',
		language: '언어',
		demo: '공모전 시연용 서비스입니다. 실제 계약·출동과 연결되지 않습니다.'
	},
	en: {
		title: 'RentCare',
		tagline: 'Report a breakdown or accident from the rental car screen, and check the return terms before you go.',
		photoCta: 'Add photos & videos from your phone',
		photoNote: 'Scanned the QR on the car screen or got an attach code? Send your photos here.',
		stepsTitle: 'How it works',
		step1Title: 'Report in the car',
		step1: 'Once parked, pick what happened on the car screen. Rental, vehicle and location details go with it.',
		step2Title: 'Add photos by phone',
		step2: 'Type the 6-character code shown in the car to send photos or videos. Links never contain the code.',
		step3Title: 'Rental company check',
		step3: 'When staff have seen it, the car screen shows it. Seen by staff does not mean help is on the way.',
		emergency: 'In an emergency, call 112 / 119 first.',
		language: 'Language',
		demo: 'Contest demo service. Not connected to real contracts or dispatch.'
	}
};

export type HomeStrings = Record<keyof (typeof home)['en'], string>;
export const HOME_STRINGS: Record<UiLang, HomeStrings> = home;

/** Photo page link: keep an explicit language choice, otherwise the phone browser decides. */
export function photoHref(explicitLang: string | null | undefined): string {
	return isUiLang(explicitLang) ? `/m?lang=${explicitLang}` : '/m';
}
