# OVERVIEW
An AI-powered automated code review system designed to integrate with GitHub Pull Requests. Built with Kotlin and Spring Boot, it utilizes Google Gemini AI to analyze code changes and provide automated feedback directly on GitHub.

# STRUCTURE
```
.
├── .github/workflows/          # CI/CD pipelines (GitHub Actions)
├── src/
│   ├── main/
│   │   ├── kotlin/com/project/codereview/
│   │   │   ├── application/    # Configuration and utility classes
│   │   │   ├── client/         # External API clients (GitHub, Google Gemini)
│   │   │   ├── core/           # Core business logic, controllers, and services
│   │   │   ├── domain/         # Domain models and repository interfaces
│   │   │   └── CodeReviewApplication.kt # Main entry point
│   │   └── resources/          # Application properties and static assets
│   └── test/                   # Unit and integration tests
├── build.gradle.kts            # Build configuration
└── Dockerfile                  # Containerization configuration
```

# WHERE TO LOOK
| Task / Workflow | Exact File Path |
| :--- | :--- |
| GitHub Webhook Entry Point | `src/main/kotlin/com/project/codereview/core/controller/CodeReviewController.kt` |
| Event Handling Orchestration | `src/main/kotlin/com/project/codereview/core/service/CodeReviewFacade.kt` |
| AI Review Logic | `src/main/kotlin/com/project/codereview/core/service/CodeReviewService.kt` |
| Gemini API Integration | `src/main/kotlin/com/project/codereview/client/google/GoogleGeminiClient.kt` |
| GitHub API Integration | `src/main/kotlin/com/project/codereview/client/github/GithubReviewClient.kt` |
| GitHub Diff Parsing | `src/main/kotlin/com/project/codereview/client/github/GithubDiffUtils.kt` |
| Domain Models | `src/main/kotlin/com/project/codereview/domain/model/` |

# CODE MAP
| Symbol | Type | Location |
| :--- | :--- | :--- |
| `CodeReviewController` | Class (RestController) | `src/main/kotlin/com/project/codereview/core/controller/CodeReviewController.kt` |
| `CodeReviewFacade` | Class (Service) | `src/main/kotlin/com/project/codereview/core/service/CodeReviewFacade.kt` |
| `CodeReviewService` | Class (Service) | `src/main/kotlin/com/project/codereview/core/service/CodeReviewService.kt` |
| `GoogleGeminiClient` | Class (Service) | `src/main/kotlin/com/project/codereview/client/google/GoogleGeminiClient.kt` |
| `GithubReviewClient` | Class (Service) | `src/main/kotlin/com/project/codereview/client/github/GithubReviewClient.kt` |
| `GithubSignature` | Object (Utility) | `src/main/kotlin/com/project/codereview/core/util/GithubSignature.kt` |

# CONVENTIONS (THIS PROJECT)
- **Kotlin Coroutines**: Extensive use of `suspend` functions and coroutine scopes for non-blocking I/O.
- **Spring Boot 3.x**: Leverages the latest Spring Boot features and dependencies.
- **GitHub App Auth**: Uses JWT (JJWT) for authenticating as a GitHub App.
- **Signature Validation**: All incoming webhooks must be validated using `X-Hub-Signature-256`.
- **Concurrency Control**: `Semaphore` is used in `CodeReviewService` to limit concurrent AI calls.
- **Rate Limiting**: Implements a cooldown period (1 minute + jitter) after each Gemini call.

# ANTI-PATTERNS / TECH DEBT
- **Hardcoded Limits**: Max review counts and concurrency limits are currently hardcoded in `CodeReviewService`.
- **Error Handling**: Catch-all `Throwable` in some service methods; could be more specific.
- **Payload Deserialization**: `FAIL_ON_UNKNOWN_PROPERTIES = false` is used, which helps with evolving APIs but might hide breaking changes.

# COMMANDS
- **Build**: `./gradlew build`
- **Test**: `./gradlew test`
- **Run Locally**: `./gradlew bootRun`

# NOTES
- The project relies on environment variables for GitHub secrets and Gemini API keys (see `application.yaml` for expected keys).
