# Project Planning
The objective of this project is to provide automated, high-quality code reviews for GitHub Pull Requests using Google's Gemini AI. By integrating directly with GitHub Webhooks, the system can automatically analyze incoming code changes and provide immediate feedback, reducing the burden on human reviewers and maintaining high code quality.

# Core Logic & Mechanisms
The system operates through a series of orchestrated steps:
1.  **Webhook Reception**: A Spring Boot controller (`CodeReviewController`) receives payloads from GitHub for specific events (e.g., `pull_request`).
2.  **Signature Verification**: The system validates the `X-Hub-Signature-256` header against a shared secret to ensure authenticity.
3.  **Event Orchestration**: `CodeReviewFacade` determines the appropriate action based on the GitHub event type.
4.  **Review Processing**:
    *   The system extracts code diffs and contexts for review.
    *   `CodeReviewService` filters and prioritizes files to review (handling limits and concurrency).
    *   Each relevant hunk is sent to `GoogleGeminiClient` with a structured prompt.
5.  **Feedback Loop**: Gemini's responses are parsed and posted as review comments on the GitHub Pull Request via `GithubReviewClient`.
6.  **Concurrency & Rate Limiting**: A `Semaphore` limits simultaneous AI calls, and a mandatory cooldown period is applied between calls to comply with API limits.

# Versioning
- `v1.0.0 - 2026-03-10`: Initial foundational documentation and core review logic discovery.
