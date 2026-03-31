# Diagram Generator

텍스트 설명을 입력하면 Gemini AI가 Mermaid.js 다이어그램 코드를 생성하고, 브라우저에서 바로 렌더링해주는 웹 서비스입니다.

---

## 주요 기능

- 자연어 설명 → Mermaid 다이어그램 자동 생성 (Gemini 2.5 Flash)
- 한국어 / 영어 UI 전환 (i18n)
- 생성된 다이어그램 PNG 다운로드, Mermaid 코드 클립보드 복사
- 브라우저 LocalStorage 기반 히스토리 (최대 10개, 재렌더링 지원)
- IP별 요청 속도 제한 (Token Bucket, X-Forwarded-For 지원)

## 기술 스택

| 구분 | 사용 기술 |
|------|-----------|
| Backend | Spring Boot 4, Java 17 |
| AI | Google Gemini API (gemini-2.5-flash) |
| Frontend | Vanilla JS, Mermaid.js 11 |
| Deploy | Docker (multi-stage build) |

---

## 로컬 실행

### 사전 요구사항

- Java 17+
- Google Gemini API 키 ([발급 링크](https://aistudio.google.com/app/apikey))

### 설정

```bash
cp .env.example .env
```

`.env` 파일을 열고 아래 항목을 채워넣습니다.

```dotenv
GEMINI_API_KEY=your_api_key_here
```

### 실행

```bash
./mvnw spring-boot:run
```

브라우저에서 `http://localhost:8080` 접속

---

## Docker 배포

```bash
# 이미지 빌드 및 백그라운드 실행
docker compose up -d --build

# 로그 확인
docker compose logs -f
```

서버에 `.env` 파일만 직접 생성하면 됩니다. 이미지 빌드 시 `.env`는 포함되지 않습니다.

자세한 배포 절차는 [DEPLOYMENT.md](./DEPLOYMENT.md)를 참고하세요.

---

## 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `GEMINI_API_KEY` | (필수) | Gemini API 인증 키 |
| `GEMINI_MODEL` | `gemini-2.5-flash` | 사용할 모델 |
| `SERVER_PORT` | `8080` | 서버 포트 |
| `RATE_LIMIT_CAPACITY` | `20` | IP당 최대 토큰 수 |
| `RATE_LIMIT_REFILL_SECONDS` | `60` | 토큰 충전 주기(초) |

전체 목록은 `.env.example` 참조

---

## 프로젝트 구조

```
src/main/
├── java/.../
│   ├── client/        # Gemini API 호출
│   ├── config/        # Rate limit, Security, AppProperties
│   ├── controller/    # REST 엔드포인트, 전역 예외 처리
│   ├── dto/           # 요청/응답 DTO
│   ├── exception/     # 커스텀 예외
│   └── service/       # 다이어그램 생성 및 Mermaid 코드 sanitize
└── resources/
    ├── application.yml
    └── static/
        └── index.html # 단일 페이지 프론트엔드
```

---

## 라이선스

MIT
