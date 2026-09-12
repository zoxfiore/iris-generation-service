# IRIS — Project Context

## Overview
IRIS (Intelligent Reality Interactive System) is a camera-based AI platform:
users describe or photograph an object, an AI generates a 3D model of it,
and the object gets placed into the real-world camera feed via AR.

This repo (`iris-generation-service`) is the **V1 scope only**: a standalone
Spring Boot service that receives a text prompt, triggers a 3D generation via
an external AI provider (Meshy), stores the job, and serves the generated
.glb model. No camera, no AR, no agent, no second service — those come later.

## Roadmap (context only — do not build ahead of current version)
- **V1 (this repo, now)**: Spring Boot generation service only.
- **V2 (later)**: a FastAPI service in front of this one, acting as an AI
  agent that routes user input (prompt or photo) to either this generation
  service or a future placement service.
- **V3 (later)**: a React frontend with camera access for AR placement.

V1 must stay genuinely simple — do not add code that only makes sense once
V2/V3 exist. But its REST API must stay clean enough that the future FastAPI
layer can call it without needing internal changes.

## Tech stack
- Backend: Java 25, Spring Boot 4.1.1, Maven
- Database: Oracle in production, H2 in-memory for local dev/tests
  (via Spring Data JPA — same code, different `application.properties`)
- Environment: WSL2 Ubuntu, VS Code
- External API: Meshy AI (text-to-3D generation, asynchronous)

## Architecture (V1)
- Modular monolith, one Spring Boot service, packages: `prompt/`, `job/`,
  `aiclient/` — dependency direction strictly `prompt → job → aiclient`
  (interface only)
- REST API: `POST /prompts` creates a job, `GET /jobs/{id}` returns its status
- Async via polling (no webhook — no public URL in local dev): job status
  goes PENDING → PROCESSING → DONE/FAILED
- Stateless: no in-memory state between requests, everything lives in the DB

## Design patterns to follow
- Adapter: all calls to the external AI provider (Meshy) go through an
  `AiGenerationClient` interface. `prompt/` and `job/` never see
  Meshy-specific classes — only `GenerationHandle`/`GenerationResult`.
  This is also what lets the future FastAPI layer (V2) call this service's
  public REST API without ever touching provider-specific internals.
- Repository: `JobRepository extends JpaRepository<...>` — empty interface,
  Spring Data JPA generates the implementation (IoC).

## Code conventions
- REST endpoint names in plural, English (`/prompts`, `/jobs`)
- Separate entities and DTOs — never expose the JPA entity directly in the API
- Use Lombok (`@Data`, `@Builder`) to reduce boilerplate
- Validate inputs with `@NotBlank`/`@Size` on request DTOs
- One constructor per class (no `@Autowired` needed — implicit injection)

## How to help me (IMPORTANT)
- I want to write the code myself to learn. Do NOT write or modify any
  file unless I explicitly ask you to.
- Default to Plan Mode: help me think through structure, architecture
  choices, and next steps — propose a plan, not code.
- Flag anything in a plan that would only make sense once V2/V3 exist,
  so I know to skip it for V1.
- If I'm stuck on a specific error, explain the cause before suggesting
  a fix — I prefer understanding over copy-pasting.
- Respond in English.