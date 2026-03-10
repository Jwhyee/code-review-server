# Gemini CLI - Project Guidelines

## 0. Mandatory Document Rules
- **ALWAYS** read `.gemini/docs/STRUCTURE.md` and `.gemini/docs/DOCUMENT.md` before starting any task.
- **CONTINUOUSLY** update `STRUCTURE.md` and `DOCUMENT.md` as the project evolves or new information is discovered.
- **APPEND-ONLY** for `DOCUMENT.md`: When updating `DOCUMENT.md` with new logic or planning, do not delete existing text. Apply a strikethrough (~~text~~) to outdated information and append the new information at the bottom.
- **VERSION BUMP**: Always increment the version number in `DOCUMENT.md` when making updates, including the current date.

## Engineering Standards
- Follow the established architecture: `core`, `domain`, `client`, `application`.
- Maintain the use of Kotlin Coroutines for all asynchronous operations.
- Ensure all new GitHub webhook events are properly validated for signatures.
- Respect the concurrency and cooldown mechanisms in `CodeReviewService`.
