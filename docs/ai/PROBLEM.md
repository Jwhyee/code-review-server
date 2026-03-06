## 1단계: CTO의 팩트 폭격 (Painful Truth)

* **다중 인스턴스 환경에서의 끔찍한 Race Condition (DB Polling의 한계):** `@Scheduled`를 활용해 실패한 이벤트를 폴링(Polling)하는 로직을 추가하셨습니다. 만약 트래픽이 늘어 이 서버를 2대 이상으로 스케일 아웃(Scale-out)한다면 어떻게 됩니까? 두 대의 서버가 동시에 동일한 `FAILED` 이벤트를 조회하고 동시에 재처리를 시도하여 동일한 PR에 리뷰 코멘트가 2~3개씩 중복으로 달리는 대참사가 발생할 것입니다. 동시성 제어가 전혀 고려되지 않았습니다.
* **스스로 불러온 Rate Limit 재앙 (Scatter-Gather의 함정):** 대규모 PR을 쪼개서 `async` 블록으로 병렬 호출(`awaitAll`)하는 로직은 논리적으로는 훌륭합니다. 하지만 40,000자 단위로 10개의 청크가 나왔다고 가정해 봅시다. 10개의 코루틴이 동시에 Gemini API를 때리면 어떻게 됩니까? Phase 2 아키텍처에서 언급했던 `Semaphore` 기반의 Rate Limit 제어 로직이 이 병렬 호출 구간에서는 감쪽같이 누락되어 429 에러(Too Many Requests)를 유발할 것입니다.
* **부분 실패(Partial Failure)에 대한 복구 비용 낭비:** Scatter-Gather 패턴에서 9개의 청크는 성공했는데 마지막 1개의 청크가 타임아웃으로 실패했다면, 이 `summary` 함수는 예외를 던지고 재시도 스케줄러에 의해 **처음부터 다시 10개를 모두 API 호출**해야 합니다. 이는 불필요한 토큰 낭비이자 심각한 비용 누수입니다.

---

## 2단계: 집요한 심문 (Interrogation)

면접관으로서 다음 세 가지를 집요하게 묻겠습니다. 포트폴리오의 코드를 방어할 준비를 하십시오.

1. **DB Lock & Concurrency:** 별도의 메시지 브로커(Redis/Kafka) 없이 RDBMS 폴링만으로 다중 서버 환경에서 동일한 이벤트를 중복으로 가져가지 않게(Mutual Exclusion) 하려면 데이터베이스 레벨에서 어떤 락(Lock) 메커니즘을 사용해야 합니까? (예: `SELECT FOR UPDATE SKIP LOCKED`, 낙관적 락 등)
2. **Backpressure & Rate Limit:** `chunkSummaries`를 병렬로 매핑하는 `async` 내부에서 외부 API의 초당 요청 수(RPS) 제한을 초과하지 않도록 동시성을 어떻게 제어(Throttling)하시겠습니까?
3. **Idempotency at Chunk Level:** PR 단위가 아닌 '청크 단위'의 실패를 캐싱하거나 기록하여, 재시도(Retry) 시 이미 성공한 청크는 API를 다시 호출하지 않도록 설계할 수 있습니까?

---

## 3단계: 리팩토링 제안 (Refactored Snippet)

지원자님이 분산 환경에서의 DB 락(Lock)을 이해하고 있고, Rate Limit 제어기를 코루틴 내부에 성공적으로 주입했다고 가정하고 제안하는 리팩토링 버전입니다.

### 01. 인메모리 큐의 한계 극복 및 다중 환경 동시성 제어 (Self-Healing)

* **🟢 TO-BE (개선 및 최적화):**
* Webhook 수신 즉시 RDBMS에 `PENDING` 상태로 영속화하여 유실 차단.
* 다중 인스턴스(Scale-out) 환경에서 동일한 실패 이벤트를 여러 서버가 중복 처리하지 않도록, **`SELECT ... FOR UPDATE SKIP LOCKED`** 기반의 비관적 락(Pessimistic Lock)을 적용하여 DB 레벨의 안전한 분산 작업 큐(Job Queue) 구현.

```kotlin
// Repository: 다중 서버 충돌 방지를 위한 SKIP LOCKED 적용
@Query("""
    SELECT w FROM WebhookEvent w 
    WHERE w.status = 'FAILED' 
    ORDER BY w.createdAt ASC 
""")
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(QueryHint(name = "javax.persistence.lock.timeout", value = "-2")) // SKIP LOCKED
fun getEventsToRetry(pageable: Pageable): List<WebhookEvent>
```

### 02. 대규모 PR 대응을 위한 동적 청킹 및 Rate-Limited Scatter-Gather

* **🟢 TO-BE (개선 및 최적화):**
* Context Window 초과 시 도메인 단위 Hunk 분할 (Scatter-Gather 패턴).
* 외부 API Rate Limit 초과 방지를 위해 병렬 처리 구간(`async`)에 **전역 Semaphore 기반의 Throttling**을 주입하여 초당 동시 요청 수를 물리적으로 통제.

```kotlin
// Rate-Limited Scatter-Gather 패턴 기반 병렬 요약 처리
suspend fun summary(contexts: List<ReviewContext>) = coroutineScope {
    val chunks = ChunkingUtils.chunkContexts(contexts)
    
    // 1. Scatter: API 한도를 준수하는 병렬 요청
    val chunkSummaries = chunks.map { chunk ->
        async {
            apiRateLimiter.withPermit { // 동시성 제어 (Rate Limiting)
                generateSummaryOnce(chunk, SUMMARY_PROMPT)
            }
        }
    }.awaitAll().filterNotNull()

    // 2. Gather: 결과 취합 및 최종 요약
    val finalSummary = if (chunkSummaries.size > 1) {
        apiRateLimiter.withPermit {
            generateSummaryOnce(chunkSummaries.joinToString("\n"), SUMMARY_PROMPT_GATHER)
        }
    } else {
        chunkSummaries.first()
    }
    
    postSummary(payload, finalSummary)
}
```