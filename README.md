# code-review

Kotlin과 Spring Boot 기반으로 만든 자동 코드 리뷰 서비스로, PR에 포함된 변경 내용을 분석하여 LLM을 통해 리뷰 코멘트를 생성하고 GitHub에 자동으로 남긴다. 리뷰 생성 실패 시 재시도까지 관리하여 일관된 코드 리뷰 자동화를 제공한다.

---

## 프로젝트 개요

이 프로젝트는 GitHub Pull Request 이벤트(Webhook)를 받아 변경된 파일의 diff를 분석한 뒤, Google GenAI를 활용해 리뷰 코멘트를 생성하고 GitHub API를 통해 자동으로 리뷰를 남기는 시스템이다. 리뷰 생성 과정에서 발생할 수 있는 요청 한도 초과(429)·서버 오류(503) 같은 상황은 내부 재시도 큐에서 관리하여 안정적으로 처리한다.

---

## CodeReview 프로세스

1. **PR 발생 → Webhook 수신**  
   GitHub Webhook이 PR 이벤트 정보를 서버로 전달한다.

2. **Diff 수집 및 분석**  
   PR의 변경 파일을 조회하고 파일 단위로 diff snippet을 분리한다.

3. **리뷰 생성 요청(LLM 호출)**  
   파일별 diff snippet을 기반으로 프롬프트를 구성해 Google GenAI 모델에 리뷰 생성을 요청한다.

4. **GitHub에 리뷰 코멘트 등록**  
   생성된 리뷰 코멘트를 GitHub Pull Request에 자동으로 작성한다.

5. **오류 발생 시 재시도 큐로 이동**
    - 모델 응답 지연, 429/503 등 재시도가 필요한 오류는 재시도 큐에 적재
    - 일정 간격으로 스케줄러가 큐를 실행하여 재리뷰 시도
    - 최대 재시도 횟수 초과 시 실패 로그만 남기고 종료

이 구조를 통해 PR 하나의 리뷰가 파일 단위로 병렬 처리되고, 장애 상황에서도 안정적으로 완료될 수 있도록 설계되어 있다.

---

## 사용 기술 스택

- **언어 및 런타임**  
  Kotlin 1.9, JVM 21

- **프레임워크**  
  Spring Boot 3.5(Web + WebFlux), Kotlin Coroutine, Reactor

- **AI 모델**  
  Google GenAI Java SDK(LLM 기반 리뷰 생성)

- **데이터 처리**  
  Jackson Kotlin Module, Reactor Extensions

- **인증/보안**  
  GitHub App 인증(JWT + Installation Token), JJWT 기반 서명 처리

- **CI/CD 및 배포**  
  GitHub Actions(self-hosted runner), Docker Buildx, Docker Hub, EC2

---

## 배포 프로세스

1. **Build Job**
    - self-hosted Runner에서 Gradle 빌드
    - JAR 파일 생성 후 아티팩트 업로드

2. **Dockerize Job**
    - 다운로드한 JAR을 기반으로 Docker 이미지를 빌드
    - Buildx를 사용해 `linux/amd64` 와 `linux/arm64` 멀티 아키텍처 이미지 생성
    - Docker Hub로 push

3. **Deploy Job**
    - SSH로 EC2에 연결
    - 환경변수를 전달하고 `code-review-deploy.sh` 실행
    - 기존 컨테이너 중지 후 최신 이미지로 재배포
    - 자동 재시작 설정으로 운영 환경 안정성 확보

이 파이프라인을 통해 PR → LLM 리뷰 생성 → GitHub 코멘트 작성 → 장애 시 재시도 → 자동화된 Docker 배포까지 전 과정이 자동으로 이어진다.

## 사용 API Document

- [Google GenAI](https://github.com/google-gemini/cookbook)
- [Github Webhook](http://docs.github.com/webhooks)
- [Get Pull Request Diff](https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#get-a-pull-request)
- [Pull Request Comment](https://docs.github.com/en/rest/pulls/comments?apiVersion=2022-11-28#create-a-review-comment-for-a-pull-request)