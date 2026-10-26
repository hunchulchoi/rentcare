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
	},
	ja: {
		title: 'RentCare',
		tagline: 'レンタカーの車の画面から故障・事故を受付し、返却条件を事前に確認できます。',
		photoCta: 'スマートフォンで写真・動画を追加',
		photoNote: '車の画面のQRを読み取ったか、添付コードを受け取った場合はここから写真を送れます。',
		stepsTitle: '対応の流れ',
		step1Title: '車で受付',
		step1: '停車してから車の画面で状況を選ぶと、レンタル・車両情報と位置が一緒に送られます。',
		step2Title: 'スマートフォンで写真を追加',
		step2: '車の画面の6文字のコードを入力して写真・動画を送ります。リンクにコードは含まれません。',
		step3Title: 'レンタカー会社の確認',
		step3: 'レンタカー会社の担当者が確認すると車の画面に表示されます。確認は出動の確定ではありません。',
		emergency: '緊急時はまず 112・119 に連絡してください。',
		language: '言語',
		demo: 'コンテストのデモ用サービスです。実際の契約・出動にはつながっていません。'
	},
	zh: {
		title: 'RentCare',
		tagline: '在租赁车辆屏幕上报修故障或事故，并提前确认还车条件。',
		photoCta: '用手机添加照片和视频',
		photoNote: '如果已扫描车辆屏幕上的二维码或收到附件码，可以在这里发送照片。',
		stepsTitle: '处理流程',
		step1Title: '在车上报修',
		step1: '停车后在车辆屏幕上选择情况，租赁、车辆信息和位置会一并发送。',
		step2Title: '用手机添加照片',
		step2: '输入车辆屏幕上的6位代码发送照片或视频。链接中不包含代码。',
		step3Title: '租车公司确认',
		step3: '租车公司工作人员确认后，车辆屏幕上会显示。确认并不代表已安排出动。',
		emergency: '紧急情况下请先拨打 112 / 119。',
		language: '语言',
		demo: '这是比赛演示用服务，不连接实际合同或出动。'
	}
};

export type HomeStrings = Record<keyof (typeof home)['en'], string>;
export const HOME_STRINGS: Record<UiLang, HomeStrings> = home;

/** Photo page link: keep an explicit language choice, otherwise the phone browser decides. */
export function photoHref(explicitLang: string | null | undefined): string {
	return isUiLang(explicitLang) ? `/m?lang=${explicitLang}` : '/m';
}
