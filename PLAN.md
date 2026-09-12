# IRIS V1 — Generation Service: Architecture Plan

## Context

`iris-generation-service` is currently an empty Spring Initializr skeleton
(confirmed: only `IrisGenerationServiceApplication.java`, one empty test, and
`application.properties` with just the app name — `pom.xml` already has
`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`,
`spring-boot-starter-validation`, H2 + Oracle drivers, and Lombok). Nothing
else exists yet.

The goal is a V1 that is genuinely minimal — one Spring Boot service, no
camera/AR/agent concerns — but with two seams built in from day one so V2
(a FastAPI agent layer) can be added later without touching this service's
internals:
1. A clean, stable REST API (`POST /prompts`, `GET /jobs/{id}`) — this *is*
   the seam for V2. FastAPI will only ever speak HTTP to this API; it will
   never see Java classes. No extra "integration layer" needs to be built
   for that — the REST boundary already provides it.
2. An Adapter interface (`AiGenerationClient`) so the Meshy-specific code
   is isolated from `prompt/` and `job/`, in case the AI provider changes
   or a second provider is added later.

This plan covers package layout, the `GenerationJob` entity, the two REST
endpoints, the polling-based async flow, exactly where the adapter seam
sits, and a step-by-step build order that gets a fake AI client working
end-to-end before any real Meshy integration is written.

## 1. Package structure

```
com.iris.iris_generation_service
├── prompt/
│   ├── PromptController.java      REST: POST /prompts
│   └── PromptRequest.java         DTO — @NotBlank/@Size prompt text
├── job/
│   ├── GenerationJob.java         @Entity
│   ├── JobStatus.java             enum: PENDING, PROCESSING, DONE, FAILED
│   ├── JobRepository.java         JpaRepository<GenerationJob, Long>
│   ├── JobService.java            create + poll-update logic (the only
│   │                              class that talks to aiclient)
│   ├── JobController.java         REST: GET /jobs/{id}
│   ├── JobResponse.java           DTO returned by both endpoints
│   └── JobPoller.java             @Scheduled — polls Meshy for jobs in
│                                  PROCESSING and updates them via JobService
├── aiclient/
│   ├── AiGenerationClient.java    interface (the Adapter)
│   ├── GenerationHandle.java      opaque handle returned by startGeneration
│   ├── GenerationResult.java      status + modelUrl/error, from checkStatus
│   ├── meshy/
│   │   ├── MeshyAiGenerationClient.java   implements AiGenerationClient
│   │   ├── MeshyProperties.java           @ConfigurationProperties (api key, base url)
│   │   └── (Meshy request/response DTOs, package-private)
│   └── fake/
│       └── FakeAiGenerationClient.java    implements AiGenerationClient,
│                                          no network calls, @Profile-gated
└── IrisGenerationServiceApplication.java
```

**Dependency direction** (enforced by not importing across the grain):
`prompt` → `job` → `aiclient` (interface + `GenerationHandle`/`GenerationResult`
only). `PromptController` calls `JobService`; it never imports anything from
`aiclient`. Only `JobService` constructs/uses `AiGenerationClient`.

## 2. `GenerationJob` entity

| Field | Type | Why |
|---|---|---|
| `id` | `Long` (`@Id @GeneratedValue`) | Public job id, used in `GET /jobs/{id}` |
| `prompt` | `String` | The original text, kept for reference/debugging |
| `status` | `JobStatus` enum | Drives the state machine PENDING→PROCESSING→DONE/FAILED |
| `providerJobId` | `String`, nullable | Meshy's own task id — the raw string behind `GenerationHandle`, needed to poll Meshy. Kept as a plain string (not a Meshy type) so the entity stays provider-agnostic |
| `modelUrl` | `String`, nullable | The `.glb` URL, set on DONE |
| `errorMessage` | `String`, nullable | Set on FAILED, surfaced via `GET /jobs/{id}` for debugging |
| `createdAt` / `updatedAt` | `Instant` | Basic audit trail; `updatedAt` also lets you eyeball stuck jobs during dev |

Deliberately **not** included (flagged as V2+/premature): a `provider`
column for multi-provider selection, `retryCount`/backoff bookkeeping, or
any user/tenant id. All three only earn their place once there's a real
second provider or multiple callers — add them then, they're cheap to bolt
on later.

## 3. REST endpoints

**`POST /prompts`**
- Request: `PromptRequest { @NotBlank @Size(max=...) String prompt }`
- Behavior: `JobService` saves a `GenerationJob` (status `PENDING`), calls
  `aiClient.startGeneration(prompt)` synchronously (this is just Meshy's
  fast "create task" call, not the slow generation itself), sets
  `providerJobId` and status `PROCESSING`, saves again. If the start call
  itself throws, the job is marked `FAILED` with `errorMessage` instead of
  letting the exception surface — the request still succeeds.
- Response: `201 Created`, `Location: /jobs/{id}`, body `JobResponse`
- Status codes: `201` normal path (including the immediate-failure case
  above — the *job* failed, the *request* to create it didn't); `400` on
  validation failure (blank/oversized prompt)

**`GET /jobs/{id}`**
- Response: `200` with `JobResponse { id, status, prompt, modelUrl, errorMessage, createdAt }`
- `404` if no job with that id exists

`JobResponse` is a DTO, never the entity itself, per convention.

## 4. Async flow — polling, no webhook

Two independent polling loops, which is expected and fine for V1:
- **Client ↔ this service**: caller repeatedly does `GET /jobs/{id}`.
- **This service ↔ Meshy**: `JobPoller`, a `@Scheduled(fixedDelay = ...)`
  method in `job/`, runs every few seconds, loads all jobs with status
  `PROCESSING` via `JobRepository`, and for each asks `JobService` to poll
  it. `JobService` rebuilds a `GenerationHandle` from the stored
  `providerJobId`, calls `aiClient.checkStatus(handle)`, and applies the
  resulting `GenerationResult` (still processing → no-op; succeeded → DONE
  + `modelUrl`; failed → FAILED + `errorMessage`).

No message queue, no async executor, no SSE/WebSocket push to the client —
those matter once V3's live AR view needs real-time updates; a plain
`@Scheduled` poller is genuinely enough for V1.

## 5. Where the Adapter seam sits (and why nothing extra is needed for V2)

`AiGenerationClient` lives in `aiclient/`, with `GenerationHandle`/
`GenerationResult` as the only types that cross into `job/`. `job/` never
sees a Meshy class. Swapping providers later means writing a new
`aiclient/<provider>/` implementation and flipping which bean is active —
zero changes to `prompt/`, `job/`, or the REST layer.

Importantly: this interface is *not* the seam V2 will use. V2's FastAPI
layer talks to this service purely over HTTP (`POST /prompts`, `GET
/jobs/{id}`) — it runs in a different process/language and will never
import `AiGenerationClient` or anything under `aiclient/`. So the REST API
being clean (plural nouns, DTOs, proper status codes) is what makes this
service V2-ready; the Adapter pattern is a separate, internal concern that
only protects against swapping Meshy for another provider. No additional
"integration API" needs to be built now for V2 — the existing plan already
covers it.

Selecting fake vs. real Meshy client: two `@Component`s, each behind
`@Profile` (e.g. `fake` vs default/`meshy`), so `application.properties`
picks the active one via `spring.profiles.active`. This is how you get a
fully working app before any Meshy credentials exist.

## 6. Build order (smallest testable step first)

1. **Domain skeleton, no REST**: `JobStatus`, `GenerationJob` entity,
   `JobRepository`. Sanity-check with H2 console or a quick repository test
   — save and re-fetch a job.
2. **Adapter contract + fake**: `AiGenerationClient` interface,
   `GenerationHandle`/`GenerationResult`, `FakeAiGenerationClient`
   (deterministic — e.g. always `PROCESSING` on first check, `DONE` with a
   dummy URL on the second). No network code at all.
3. **`JobService` against the fake**: create-job-and-start-generation +
   poll-and-update, as plain methods — unit-testable without Spring MVC or
   a scheduler yet.
4. **`GET /jobs/{id}`**: `JobController` + `JobResponse`. Create a job via
   `JobService` directly (e.g. in a test) and fetch it over REST.
5. **`POST /prompts`**: `PromptController` + `PromptRequest`, delegating to
   `JobService`. Full REST surface now exists, running entirely on the
   fake client.
6. **`JobPoller`**: wire up `@Scheduled`, confirm a job walks
   PENDING→PROCESSING→DONE with zero manual intervention, still on the
   fake.
7. **Swap in real Meshy**: `MeshyAiGenerationClient` + `MeshyProperties` +
   Meshy DTOs, activated via profile/property. This step should require
   *no* changes to `prompt/` or `job/` — that's the actual test of whether
   the adapter boundary was drawn correctly.
8. **Polish** (still V1): validation error responses, 404 handling, maybe
   one integration test against Meshy's real sandbox.

## Explicitly out of scope for V1 (V2/V3-only — skip)

- Any webhook/callback endpoint for Meshy (needs a public URL you don't
  have locally — pure polling is correct for now).
- Push/streaming updates to the caller (SSE/WebSocket) — relevant once V3's
  AR view needs live updates, not before.
- A `provider` column or any multi-provider selection logic — add only when
  a second provider actually exists.
- Auth on the REST API for the future FastAPI caller — not mentioned as a
  current need; add if/when V2 actually requires it.
- Anything about photos, placement, or routing logic — that's V2's job,
  living entirely outside this repo.
