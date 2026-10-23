# 배포 (rentcare.devfor.link)

- URL: https://rentcare.devfor.link
- 경로: 브라우저·차량 → Cloudflare → cloudflared 터널(`51dea0da…`) → Traefik :443
  → crowdsec-bouncer → global-ratelimit → Sablier → `rentcare-web-1:3000`
- 서버: `stage.devfor.link` (같은 호스트에 Postgres 18, MinIO, Traefik, Sablier, Portainer)
- Portainer 스택 `rentcare` (ID 59, endpoint 3), Git: `forgejo.dgst.me/choihunchul/rentcare` (비공개)
  `refs/heads/main`의 `compose.deploy.yaml`

## 배포 흐름

1. `main`에 push
2. `git tag vX.Y.Z && git push origin vX.Y.Z`
3. Forgejo webhook(hook 41, `refs/tags/*` push) → Portainer 스택 webhook
4. Portainer가 `main` HEAD를 받아 `docker compose up` (이미지 빌드 → 재생성)

태그는 트리거일 뿐이고 항상 `main` HEAD가 배포된다. 같은 커밋에 태그만 다시 찍으면 재배포는
돌지만 컨테이너는 바뀌지 않는다(2026-10-23 확인).
스택 webhook URL의 UUID는 인증 없는 트리거 — 외부에 노출하지 않는다.

## Sablier (scale to zero)

- 그룹 `rentcare`, 세션 1시간. 1시간 요청이 없으면 컨테이너를 멈춘다.
- 미들웨어 (`~/workspace/conf/traefik/dynamic.yml`):
  - `sablier-rentcare` — 브라우저 페이지. 깨우는 동안 안내 페이지(wakeup-ko).
  - `sablier-rentcare-blocking` — `/api/*`, `/m/upload`, `/vendor/map/*`, `/vendor/files/*`, `/health`.
    앱이 뜰 때까지 요청을 붙잡는다(최대 60초). 차량 API가 HTML 안내 페이지를 받지 않게 한다.
- `restart: "no"`는 의도된 값(Sablier가 시작·정지).

## 스택 환경변수 (Portainer)

비밀값은 Portainer 스택 Env에만 있다. 원본은 Infisical `/postgres`, `/minio`, `web/.env`.

| 변수 | 비고 |
|---|---|
| `DATABASE_URL` | Infisical 값에서 호스트만 `postgres:5432`(postgres_default 네트워크)로 바꿈. TLS 필수 |
| `CAR_API_KEY`, `DEMO_VENDOR_TOKEN` | `web/.env`와 같은 값 (차량 앱 빌드가 같은 키를 씀) |
| `S3_BUCKET`, `AWS_ENDPOINT_URL_S3`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `S3_FORCE_PATH_STYLE` | MinIO. 엔드포인트는 https 공개 주소 |
| `PUBLIC_BASE_URL` | `https://rentcare.devfor.link` (ORIGIN도 이 값) |

값의 `$`는 `$$`로 저장한다(Portainer env-file 인터폴레이션).
Portainer `POST /api/stacks/{id}/git`은 `env`를 통째로 교체하므로 기존 Env를 GET해 포함할 것.

## 인프라 변경 내역 (2026-10-23)

- `~/.cloudflared/config.yml` ingress에 `rentcare.devfor.link → https://localhost:443`(noTLSVerify) 추가,
  `cloudflared tunnel route dns` 로 CNAME 생성, `docker restart cloudflared`. 백업: `config.yml.bak-<시각>`.
- Traefik `dynamic.yml`에 `sablier-rentcare`, `sablier-rentcare-blocking` 추가. 백업: `dynamic.yml.bak-<시각>`.
- Forgejo 저장소를 비공개로 전환. Portainer는 저장소 토큰으로 받아온다
  (지금은 개인 토큰 — 읽기 전용 토큰으로 바꾸길 권장).

## 확인

```bash
curl -s https://rentcare.devfor.link/health            # {"ok":true}
ssh stage.devfor.link 'docker ps | grep rentcare; docker logs rentcare-web-1 --tail 20'
```

## 차량 앱과 서버 주소

- `web/.env` `PUBLIC_BASE_URL=https://rentcare.devfor.link` — 차량 앱 기본 빌드가 이 주소를 쓴다(2026-10-23부터).
- `web/.env` `LOCAL_BASE_URL` — 로컬 서버(Tailscale Funnel). `scripts/server.sh`가 이 주소로 띄우고,
  `scripts/e2e.sh`는 앱을 이 주소로 빌드해 시험한 뒤 배포 주소로 다시 설치한다.
  배포 서버는 Sablier가 바로 깨우므로 장애 시나리오(S6)를 재현할 수 없어서다.
- 로컬과 배포 서버는 같은 DB·MinIO를 쓴다(접수번호가 공유됨).
