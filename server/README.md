# 음메 서버 (Supabase)

지금은 썰마카세 프로젝트(rkcgqhmnhiwgfzglkvto) 안의 `eumme` 스키마에 있어요. 썰마카세 테이블과는 섞이지 않아요.

- 스키마: `eumme` (users, usage, ad_rewards, devices, settings) — auth.users에 FK 없음
- 함수: `eumme.reserve / refund / log_usage / status / grant_ad` (security definer, 앱에서 직접 호출 불가)
- 엣지 함수: `functions/eumme-api` (verify_jwt 끔, 함수 안에서 익명 로그인 토큰 확인)
- 비밀값: `EUMME_GEMINI_KEY` (필수), `EUMME_MODEL` (선택, 기본 gemini-flash-lite-latest)

## 독립 프로젝트로 옮길 때
1. 새 프로젝트에서 Anonymous Sign-Ins 켜기
2. `supabase db dump --schema eumme --data-only=false` 로 스키마 옮기고, 필요하면 `--data-only`로 데이터도
3. 사용자 id가 auth.users에 묶여 있으므로, 기존 사용자를 그대로 옮기려면 auth 사용자도 함께 이전(또는 새로 시작)
4. `eumme-api` 배포 + 비밀값 등록
5. 앱의 서버 주소·공개 키 한 곳(Backend 설정)만 바꿔 새 버전 배포

## API
POST `/functions/v1/eumme-api`, `Authorization: Bearer <익명 로그인 access_token>`, `apikey: <publishable key>`

| action | 보내는 값 | 받는 값 |
|---|---|---|
| status | device | status |
| classify | device, audio_b64, mime, cats[], created_ms, open[] | items[], actions[], status / 402 no_pass |
| reward | device, pack(0~2), tx | result, status (테스트 기간만, 출시 때 SSV) |
