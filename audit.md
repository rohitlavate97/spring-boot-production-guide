# Audit: Spec Compliance — Both Curricula

Scope: compares the two built curricula against their governing master prompts.

- **Guide A — "Mastery Handbook"**: spec = `spring-boot-production-mastery-master-prompt.md`, content = `docs/chapters/010-*.md` … `400-*.md` (40 chapters), `docs/HANDBOOK.md`, `ARCHITECTURE.md`, `code/chapter-010` … `chapter-400`.
- **Guide B — "Troubleshooting Guide"**: spec = `production issues/Master Prompt_ Spring Boot Troubleshooting and Production Debugging Guide.md`, content = `production issues/docs/00-*.md` + `module-01` … `module-28`, `production issues/code/module-01` … `module-28`, `production issues/README.md`.

Method: 6 parallel research agents each read the spec and a slice of the built content, reporting only gaps against the spec's explicit rules. Findings below are deduplicated and organized by severity and by what to actually do about them — this file is meant to drive follow-up work, not just describe problems.

Both curricula are structurally complete (all chapters/modules exist, front matter and section headers are in place, no toy CRUD examples, real domain systems throughout). The gaps are consistently in the same three places: **the spec's proof-of-fix testing requirement**, **numeric-labeling discipline**, and **a handful of deliverables that were named in the spec but never built**. Below is a punch list, not a rewrite of every file.

---

## Guide A — Mastery Handbook

### 🔴 Critical — fix across all 40 chapters

**1. `tests/unit/` + `tests/integration/` layout was never used (Section 9).**
Every `code/chapter-NNN` uses Maven-standard `src/test/java/.../unit/` and, where present, `.../integration/` — the spec's literal `code/chapter-NNN/tests/unit/` / `tests/integration/` path never appears anywhere in 40 chapters. This is consistent enough that it reads as a deliberate (if undocumented) convention choice rather than an oversight — **decide and document it explicitly in the spec or in `docs/HANDBOOK.md`** rather than leaving every chapter silently non-compliant with the literal path.

**2. The "prove the incident, then prove the fix" requirement is unmet in most chapters (Section 9).**
The spec is explicit this is "the proof, not just an assertion of 'should work'." Across all three audited ranges:
- Chapters 300–400 (11/11 chapters): the `*Incorrect` class named in the markdown was **never compiled into `code/chapter-NNN/src/main`** at all — no test can run it because it doesn't exist as code.
- Chapters 150–290: `*Incorrect` classes largely do exist in code but are **never invoked by any test** — e.g. ch180's `WalletTransferServiceIncorrect`, ch220's `VulnerablePaymentServiceIncorrect`, ch230's `InsecureJwtParserIncorrect`, ch290's `StatementExportServiceIncorrect.executeUnboundedAsync()`.
- Chapters 010–140: roughly half (050, 060, 090, 100, 130, 140) reimplement the bug inline inside the test instead of calling the shipped `*Incorrect` class — simulated proof, not real proof.
- A handful of chapters do it correctly and can serve as the template: **ch030, ch040, ch110, ch120, ch170** genuinely instantiate the incorrect class, show it fail, then show the fix pass.

**Action:** pick 2–3 of the compliant chapters (170 is the strongest) as the reference pattern, then retrofit the rest — starting with 300–400 since those don't even have compilable incorrect classes yet.

**3. Rule 5 (illustrative labeling) is inconsistently applied everywhere.**
Every chapter audited has at least one bare, unlabeled latency/throughput/memory figure sitting next to a properly tagged "(illustrative)" one in the same table — e.g. ch140's Performance Comparison table tags only the CPU row and leaves Memory/IOPS/Batch Throughput bare; ch300/310/320/330 have *zero* illustrative tags anywhere. This is a mechanical, high-volume find-and-fix, not a structural rewrite.

### 🟡 High — cross-chapter continuity breaks (Rule 9)

Rule 9 requires surfacing and reconciling conflicts with earlier chapters or `ARCHITECTURE.md` — instead these are silent:

| Where | Conflict |
|---|---|
| ch190 (HikariCP) | `minimum-idle: 10` vs ARCHITECTURE.md's 5; `idle-timeout: 600000` vs 300000; `leak-detection-threshold: 2000` vs 60000 (30× off); `max_connections: 500` vs ARCHITECTURE.md's 300 |
| ch290 (Async) | Enterprise Scenario says 20 pods (matches ARCHITECTURE.md); Production Incident timeline says "4 pods" for the same job |
| ch260 (Kafka) | Topic name `payment.events` vs ARCHITECTURE.md's `payment-events`; different partition key, partition count, and DLQ naming |
| ch250 (Redis) | Cache implementation is actually a local `ConcurrentHashMap`/`ConcurrentMapCacheManager` — never talks to Redis at all, despite the chapter topic and despite ARCHITECTURE.md scoping Redis specifically to "payment method lookups, idempotency keys" |
| ch270 (RabbitMQ) | Invents a new "Payout Service & Settlement Engine" node not in ARCHITECTURE.md's topology, and never frames RabbitMQ against Kafka's already-established primary role |
| ch220 (Security) | Teaches custom API-key auth (with keys hardcoded in `Map.of(...)`, itself contradicting ARCHITECTURE.md §9 secret management) as the main vehicle instead of the mandated OAuth2 Resource Server |
| ch230 (JWT) | `JwtKeyManager` generates a fresh in-memory RSA keypair per boot — would break JWKS verification across the Payment Service's 20 pods in the real topology |
| `ARCHITECTURE.md` itself | Never amended despite the ch190 conflict above — file has exactly 1 commit total, so the tension the spec calls for ("get a DB rated for ~150 concurrent comfortably vs 200 total connections") is stated as disconnected table rows, never tied together in prose |

**Action:** this is worth one dedicated pass — read ARCHITECTURE.md + the flagged chapters together and either (a) update ARCHITECTURE.md via an amendment commit reflecting real decisions made in later chapters, or (b) fix the later chapters to match. Given ARCHITECTURE.md is the shared mental model for the whole handbook, drift here compounds with every future chapter.

### 🟠 Medium

- **Word-count guardrail (Section 10, 2,500–4,000 words):** chapters 300–400 are *all* under floor except ch340 (range ~1,632–2,236 words); chapters 070, 100, 130, 140, 180, 240, 260 are also under floor in the 010–290 range. No chapter is over the ceiling except ch010 (~4,080, marginal). This isn't a spec violation requiring a split (Section 10 is about *over*-budget topics needing a split) — it's under-delivery of prose depth, worth revisiting in the thinner chapters (070, 140, 240, 260 read as the thinnest).
- **Section titled "Logs & Diagnostics" instead of the mandated "Logs"** in ch190, ch270, ch280, ch290 — cosmetic but should match Section 5's exact header.
- **Testing tech mismatch:** chapters 150, 160, 180, 200, 210, 220, 230 use H2 in tests despite the chapter's own topic being PostgreSQL-specific behavior (e.g. ch180 discusses `pg_locks`/`xmax`/`SKIP LOCKED` but tests run on H2, which doesn't support these). Chapter 200 in particular promises both PostgreSQL and MySQL in front matter but only has an H2-backed test setup.
- **ch340 (Resilience4j):** the markdown's own code block has invalid Java (`import ... CircuitBreaker as R4jCircuitBreaker;`) — a Rule 6 violation ("code must run") in the doc, even though the real shipped code avoids the mistake. Doc and code have silently diverged.
- Stray committed log files under `code/chapter-120/` (`debug.log`, `error.log`) — should be gitignored/removed.

### ✅ Consistently solid (no action needed)
Front matter completeness, all-16-sections presence/ordering, 5-tier interview questions, and avoidance of toy CRUD examples were clean across all 40 chapters. `docs/HANDBOOK.md` TOC matches the actual chapter set with no orphans and is amended incrementally (43 commits, ~1 per chapter) — this is the one artifact behaving exactly as the spec intends.

---

## Guide B — Troubleshooting Guide

### 🔴 Critical

**1. Every module defers Q&A answers to a "Master Answer Guide" that does not exist anywhere in the repo.**
All 28 modules end with the same boilerplate pointing to this file. It was never created. This is the single highest-value fix in Guide B — either write the answer guide (one file, aggregating all 28 modules' answers) or change the boilerplate to stop promising it.

**2. The spec's core interactivity model (`Learning Method`, Parts A–H) was not implemented at all — the guide was built as a static reference manual instead.**
The spec is explicit and repeated: "ask what would you check first... do **not** reveal the answer immediately," "never jump directly to the fix." In practice, all 28 modules use a fixed 12-section template where root cause is stated outright in section 3, immediately after the scenario — there is no reader-input placeholder anywhere, and 13 of modules 15–28 print `*Answer:*`/`*Diagnosis:*` directly under each question in the same section instead of deferring it. This is a **guide-wide format decision**, not a per-module bug — worth a conscious call on whether to (a) accept the reference-manual format as the actual product and update the spec to match, or (b) do a structural pass converting scenarios into gated investigation-challenge format. Given the scale (28 modules), (a) is almost certainly the pragmatic choice unless self-testing is a priority use case.

**3. Module 28 ("Production Incident Response") — the capstone — is the single weakest artifact in either guide.**
- Only **4 of the 20 promised incidents** (Playbooks 01, 02, 03, 11) have any real runbook detail. The other 16 are a one-line table row each — no detection, evidence, hypotheses, or RCA.
- The 14-step framework is described once, generically, and never actually walked through per-incident.
- `code/module-28` ships only a generic `IncidentTriageEngine` (severity scoring) — none of the 20 incidents have simulation/reproduction code, unlike every other module (15–27) which do ship working repros.
- It doesn't follow the mandated 12-section-per-issue format at all (uses a custom ICS-matrix/playbook-table structure instead).

**Action:** this needs the most direct attention of anything in either guide — treat it as "16 of 20 incidents are unwritten," not "needs polish."

### 🟡 High

**4. `⚠️ Do not run blindly in production` warning is entirely absent — zero occurrences across all 28 modules**, despite genuinely dangerous commands throughout: `pg_terminate_backend` (M17/M21/M25/M28), `mvn flyway:clean`/`flyway repair` (M25), `redis-cli monitor`/`DEL` on prod keys (M19/M21), `kubectl rollout undo`, live `MALLOC_ARENA_MAX` env injection, `kubectl exec`/pod kills (M28), `jstack` on a prod PID (M12/M13), `jcmd <PID> GC.heap_dump` on a live PID (M14). This is a mechanical find-and-tag pass, not a rewrite — Rule 9 of the spec is explicit about this marker.

**5. Structural pattern across nearly every module: one mega-scenario instead of per-sub-topic coverage.**
Each module's topic list in the spec has 13–23 named sub-issues; each module doc reproduces exactly one of them as a full scenario, with the rest only name-dropped in a question or a bullet. Concretely uncovered despite being explicitly named in the spec:
- M1: 22 of 23 sub-topics (only circular dependency is a real scenario)
- M6: `@Cacheable` and `@Retryable` — explicitly called out as "annotations that appear correct but don't work," never given code
- M7: isolation levels, read-only transactions, transaction timeout, optimistic/pessimistic locking (no `@Version`/`LockModeType` code anywhere)
- M8: infinite JSON recursion (not mentioned at all), cascade problems, DTO projections, entity lifecycle states
- M9: the spec's own headline incident for this module ("CPU normal, all requests timing out, connections occupied") is posed as a question but never walked through in the body
- M11: IDOR, method security (`@PreAuthorize` absent from doc and code), clock skew (no `setAllowedClockSkewSeconds` in code — question-only)
- M13: jcmd, Java Flight Recorder, VisualVM, async-profiler — spec-required tool list, only `jstack` is ever used
- M14: the module's own mandated scenario ("pod OOMKilled but no `OutOfMemoryError` in the log") is never built out, only asked as a one-line question

**Action:** not all of these need fixing — but M9 and M14 specifically failing to cover their own spec-mandated headline scenario is worth prioritizing, since those are the modules' entire reason for existing.

**6. Guide-level deliverables named in the spec's "Final Deliverables" section (12 items) — 7 of 12 are missing outright:**

| # | Deliverable | Status |
|---|---|---|
| 1 | Roadmap beginner→expert | ✅ `00-program-architecture.md` |
| 2 | All 28 modules | ✅ present, 1:1 with spec |
| 3 | ≥100 hands-on scenarios | ❌ ~47 total (27 modules × 1 + 20 in M28); no consolidated count anywhere |
| 4 | ≥20 incident simulations | ⚠️ 20 are named in M28 but only 4 have real content (see Critical #3) |
| 5 | Debugging decision tree | ❌ missing entirely |
| 6 | Symptom→cause→evidence matrix | ❌ only per-module local sections exist, no consolidated cross-module matrix |
| 7 | Command cheat sheet (Java/Spring/Linux/Docker/K8s) | ❌ scattered across modules, no standalone doc |
| 8 | Actuator/observability checklist | ❌ missing as a standalone artifact |
| 9 | Production readiness checklist | ❌ only per-module 2–3 bullet mini-lists exist |
| 10 | Personal debugging lab project | ❌ missing |
| 11 | Capstone: diagnose *simultaneous* failures | ❌ M28's 20 incidents are independent, one-at-a-time — never a multi-fault-at-once exercise |
| 12 | Final assessment | ❌ missing as a distinct exam; only the recurring per-module Q&A exists |

**Action:** items 5–10 and 12 are each a single, boundedly-sized new document (decision tree, matrix, cheat sheet, two checklists, assessment) — cheap to produce and would close most of this gap quickly. Item 11 requires touching M28's design (see Critical #3), and item 3 is really a byproduct of fixing #5 above (more sub-topics covered → more scenarios → closer to 100).

### 🟠 Medium

- **`README.md` doesn't overclaim, but it's silent where it should flag gaps.** It marks all 28 modules "✅ Complete" (accurate for content existing) but says nothing about the interactivity-format deviation or the 7 missing guide-level deliverables — a reader has no way to know from the README that these are absent.
- **Evidence Collection sections are sometimes thinner than the symptom warrants:** M7 and M8 both describe symptoms that call for thread-dump/heap-dump analysis but never explain capturing or reading one in the Evidence Collection section — closer to the "check the logs" vagueness the spec explicitly prohibits (Important Rules #1).
- **Domain/DB inconsistency:** M7 and M8 use H2 in code despite PostgreSQL being the guide's stated primary database, with no Docker config — same pattern seen in Guide A.
- **`00-program-architecture.md`:** the spec's 4-tier difficulty system (Beginner/Intermediate/Advanced/Expert) is never used — every module is rated Intermediate/Advanced/Expert only, including Module 1 itself (rated "Intermediate" despite being the explicit entry point). Also missing: a consolidated hands-on labs list and an explicit "recommended completion order" distinct from the dependency graph.
- **M3:** `mvn dependency:resolve` is never mentioned despite being spec-required alongside `dependency:tree`/`clean verify`.

### ✅ Consistently solid (no action needed)
12-section format presence/ordering, question counts (5 interview / 5 production / 3 trick), real runnable code matching the "How to Reproduce" section, and module count/filenames matching the spec 1:1 were clean across all 28 modules (M28's format is the one exception, per Critical #3). Module 15 is the one module that correctly defers answers to a separate section rather than printing them inline — worth using as the template if converting the rest.

---

## Suggested order of attack

If tackling both guides, the highest-leverage sequence is:

1. **Guide B, Module 28** — 16 of 20 incidents are currently empty rows; this is the capstone and the biggest single gap in either guide.
2. **Guide B, the `⚠️` warning pass** — mechanical, fast, closes a repo-wide rule violation in one sweep.
3. **Guide B, the 6 missing standalone deliverables** (decision tree, symptom matrix, cheat sheet, actuator checklist, readiness checklist, final assessment) — each is a bounded new document.
4. **Guide A, chapters 300–400** — no compiled "Incorrect" classes at all in this range, so no test can prove anything; this is the single biggest hole in Guide A's testing story.
5. **Guide A, the ARCHITECTURE.md ↔ ch190/ch250/ch260/ch270 reconciliation pass** — drift here compounds forward, worth fixing before it gets cited by more chapters.
6. **Both guides**: the Rule 5 illustrative-labeling pass and the Section 9 "reproduce-then-fix" retrofit for chapters 150–290 and modules 1–14 — high volume, mechanical, lower urgency than 1–5.
