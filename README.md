# AI Code Reviewer

Kotlin, Spring Boot, Google Gemini 기반의 AI 코드 리뷰 어시스턴트입니다. GitHub 앱으로 동작하여 Pull Request에 대한 요약과 상세 리뷰를 자동으로 제공합니다.

## 프로젝트 개요

이 프로젝트는 Github Pull Request(PR)의 코드 변경 사항을 분석하고, Google Gemini API를 통해 리뷰 코멘트를 생성하여, Github에 게시해줍니다.
기본적으로 PR 생성 시 변경 사항을 요약해주고, `review-bot` 라벨을 통해 요청 시 상세 리뷰를 수행합니다.

## 주요 기능

- **PR 변경점 자동 요약**: PR이 열리면 전체 변경 사항을 요약하여 PR에 코멘트로 남겨줍니다.
- **요청 기반 상세 리뷰**: PR에 `review-bot` 라벨을 추가하면, 파일별로 상세 코드 리뷰를 진행하고 **파일 코멘트**를 작성합니다.
- **안정적인 비동기 처리**: Kotlin Coroutine과 Channel을 사용한 내부 작업 큐를 통해 리뷰 요청을 순차적으로 처리하여, GitHub API와 LLM API의 Rate Limit에 대응합니다.
- **지능적인 Diff 분석**: `import` 구문 변경이나 파일 삭제, 공백 등 의미 없는 변경 사항은 필터링하여 핵심 로직에만 집중합니다.
- **고품질 리뷰 프롬프트**: '시니어 테크 리드' 페르소나를 적용한 정교한 시스템 프롬프트를 통해 깊이 있고 일관된 품질의 리뷰를 생성합니다.
- **GitHub 앱 기반 인증**: 안전한 GitHub 앱 인증(JWT) 방식을 사용하여 각 리포지토리에 대한 작업을 수행합니다.

## 동작 방식

1. **Webhook 수신 (`CodeReviewController`)**
   - GitHub로부터 `pull_request` 이벤트를 수신합니다.
   - `X-Hub-Signature-256` 헤더를 통해 요청이 유효한지 검증합니다.

2. **이벤트 라우팅 (`CodeReviewFacade`)**
   - **PR `opened` 액션**: `CodeSummaryService`를 호출하여 PR 요약 생성을 시작합니다.
   - **`labeled` 액션 (`review-bot` 라벨)**: `ReviewJobQueue`에 상세 리뷰 작업을 추가합니다.

3. **PR 요약 생성 (`CodeSummaryService`)**
   - `GithubDiffClient`를 통해 PR의 diff 내용을 가져옵니다.
   - 전체 diff를 기반으로 요약 프롬프트를 구성하여 Gemini 모델을 호출합니다.
   - 생성된 요약 텍스트를 `GithubReviewClient`를 통해 PR에 일반 코멘트로 등록합니다.

4. **상세 코드 리뷰 (`ReviewJobQueue` & `CodeReviewService`)**
   - 백그라운드 워커가 큐에서 리뷰 작업을 가져옵니다.
   - `GithubDiffUtils`가 diff 텍스트를 파일 단위로 파싱하고, 리뷰가 불필요한 변경(import 등)을 필터링합니다.
   - `CodeReviewService`는 각 코드 변경 청크(chunk)에 대해 Gemini 모델을 호출하여 리뷰를 생성합니다.
     - `Semaphore`를 사용하여 LLM API 동시 요청 수를 제어합니다.
     - API 호출 후 일정 시간(`delay`)을 두어 Rate Limit을 준수합니다.
   - `GithubReviewClient`가 생성된 리뷰를 PR의 해당 코드 라인에 코멘트로 등록합니다.

## 인증 방식

- **GitHub Webhook Secret**: Webhook 페이로드의 무결성을 검증하기 위해 사용됩니다.
- **GitHub App Authentication**:
  - 리뷰 코멘트 작성 등 리포지토리에 대한 작업을 수행하기 위해 GitHub 앱으로 인증합니다.
  - `GithubAppTokenProvider`가 앱의 Private Key로 JWT를 생성하고, 이를 일시적인 Installation Token으로 교환하여 API 요청에 사용합니다.
- **GitHub PAT (Personal Access Token)**:
  - `GithubDiffClient`에서 PR의 diff 정보를 가져오는 등 간단한 읽기 전용 작업에 사용됩니다. 현재는 App 인증과 별도로 관리되고 있습니다.

## 기술 스택

- **언어 및 프레임워크**: Kotlin 1.9, Spring Boot 3 (Web + WebFlux), Coroutine
- **AI 모델**: Google Gemini (google-genai Java SDK)
- **인증/보안**: GitHub Apps (JWT), JJWT
- **데이터 처리**: Jackson Kotlin Module
- **CI/CD 및 배포**: GitHub Actions, Docker (Buildx), EC2

## 배포 프로세스

<img width="1024" height="523" alt="image" src="https://github.com/user-attachments/assets/6ffea30a-8eca-4207-b15d-bb196c573a91" />

## 사용 API Document

- [Google GenAI](https://github.com/google-gemini/cookbook)
- [Github Webhook](http://docs.github.com/webhooks)
- [Get Pull Request Diff](https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#get-a-pull-request)
- [Pull Request Comment](https://docs.github.com/en/rest/pulls/comments?apiVersion=2022-11-28#create-a-review-comment-for-a-pull-request)
