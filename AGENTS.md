# AGENTS.md — fyreplace-api (Quarkus gRPC)

Reactive Quarkus (Java 25) + gRPC port of the legacy Django API,
[fyreplace-api-django](https://github.com/fyreplace/fyreplace-api-django). The Django repo is the
behavioral specification: consult it when porting, but do not translate 1:1 — the architectures
differ by language and framework.

- API contracts (`.proto` files) live in the git submodule `src/main/proto/protos`
  (github.com/fyreplace/fyreplace-protos). **Never edit generated Java stubs.** To change an API
  shape: commit in the submodule first, bump the pointer here, rebuild (stubs regenerate from
  `./gradlew build`). RPC names look like `fyreplace.protos.UserService/RetrieveMe`.
- gRPC shares the HTTP port 8080 (`quarkus.grpc.server.use-separate-server=false`), which is also
  why HTTP-level auth applies to gRPC calls.

## Fyreplace-wide rules

- **Minimal comments** — write self-explanatory code with precise naming. Add a comment only for a non-obvious constraint or an intentional deviation; never narrate what the code already says.
- **Conventional commits** — commit messages follow the [Conventional Commits](https://www.conventionalcommits.org) format: `type(scope): subject` (e.g. `feat(ui): add dark mode`). Keep them short: a concise subject line (≤ 72 characters) and, when needed, a brief body.

## Commands

| Task | Command |
|---|---|
| Build + test (enforces formatting) | `./gradlew build` |
| Compile only / tests only | `./gradlew compileJava` / `./gradlew test` |
| Format (required before commit) / check | `./gradlew spotlessApply` / `spotlessCheck` |
| Dev mode (live coding, Dev UI at `/q/dev`) | `./gradlew quarkusDev` |
| Compile MJML email templates | `make emails` |
| Generate JWT RSA keypair | `make keygen-rsa` |
| Try RPCs by hand (dev mode, reflection on) | `grpcurl -plaintext localhost:8080 list` |
| Mint a dev JWT (dev mode) | `curl localhost:8080/dev/users/<username>/token` |

Requirements: JDK 25, **PostgreSQL 18+** (`uuidv7()` is PG18+ — pin
`quarkus.datasource.devservices.image` if Dev Services pulls an older image), Docker (Dev Services
starts a Postgres container when no database is configured), Node/npx for `make emails`. A
gitignored `.env` at the repo root can point at a real database instead; keep its variable names in
sync with `application.properties`.

## Architecture map

- `src/main/java/app/fyreplace/grpc/endpoints/` — one `@GrpcService` impl per proto service,
  extending `EndpointBase` (injects every entity `Repository` plus the conversion services).
  Endpoints stay thin: validation/authorization are chain steps into
  `ValidationService`/`AuthorizationService`. RPCs still awaiting the port throw
  `NotImplementedYet` (see Porting status).
- `src/main/java/app/fyreplace/grpc/ExceptionInterceptor` — replaces Quarkus's default gRPC
  exception handler: translates `ApiException` (both sync throws and `Uni`/`Multi` failures) into
  gRPC statuses with the detail string as description, and maps `NoResultException` → `NOT_FOUND`.
  When a REST API arrives, mirror it with JAX-RS `ExceptionMapper`s.
- `src/main/java/app/fyreplace/errors/` — transport-agnostic `ApiException` hierarchy
  (`Unauthenticated`, `PermissionDenied`, `NotFound`, `InvalidArgument`, `AlreadyExists`,
  `Cancelled`). The detail string is the machine-readable client message (`email_taken`) that
  clients match on. Throw these everywhere; never `StatusRuntimeException` outside `grpc/`.
- `src/main/java/app/fyreplace/data/entities/` — Hibernate Reactive + Panache entities, each with
  a nested `public static final class Repository` implementing the matching base interface. State
  properties (`User.isAlive`, `isBanned`, `hasUsablePassword`, …) live on entities; input checks
  belong in `ValidationService`, never on entities. `User.Repository.softDeleteById` overrides the
  shared soft delete to also wipe user PII and the avatar `StoredFile` — stored files are never
  orphaned.
- `src/main/java/app/fyreplace/services/` — cross-cutting beans:
  - `ToProtoService` / `FromProtoService` — every entity↔proto conversion goes through these.
  - `CurrentUserService` — resolves the caller from `SecurityIdentity`; reads the `user_id` claim,
    not the principal name (`upn` = username).
  - `ValidationService` — all input checks, as chainable `Uni<Void>` steps. Availability checks
    are DB-backed (`ALREADY_EXISTS`), format checks throw only. Owns the reserved-username list and
    Django's NFKD→ASCII→uppercase normalization. Usernames are lax by design (only length bounds);
    email is length bounds + strict pattern.
  - `AuthorizationService` — caller/token checks that **throw only** (`checkUser`, `checkPending`,
    `checkPasswordless`), plus the data-returning lookups `connectUser(email)` and
    `emailTokenUser(token)`.
  - `StorageService` — `file`/`memory`/`s3` implementations in `services/storage/`, selected by
    `app.storage.type` (tests use `memory`).
  - `JwtService` — 7-day tokens; `sub`/`user_id` = user id, `upn` = username. **Email tokens**
    carry a `timestamp` claim and are valid for `AuthorizationService.EMAIL_TOKEN_LIFE` (24 h). There is no
    `connection_id` claim — the connections concept was dropped (no `connections` table); connection
    flows authorize by token type/freshness instead.
  - `ImageService` (JPEG/PNG/WEBP only — `services/mimetype/KnownFileType`), `LocaleService`,
    `TemplateDataService`.
  - `ExecutorService` — safe duplicated Vert.x context / executor (`safeContext()`,
    `safeExecutor()`); use it to run Panache or blocking work outside the current context
    (tests use it via `TestsBase`).
  - `services/gravatar/` — `GravatarService` fetches the default avatar from Gravatar
    (`app.gravatar.base-url`, SHA-256 of the trimmed/lowercased email per the official docs,
    `?d=404&s=256`) once per account creation, fire-and-forget off the endpoint's session
    (mirrors Django's Celery `fetch_default_user_avatar.delay`); `app.gravatar.enabled=false`
    disables it (`%test` sets that).
- `src/main/java/app/fyreplace/mails/` — `MailBase` subclasses rendering Qute templates from
  `src/main/resources/templates/<MailName>/`; subjects in `i18n/strings_emails.properties`; per-mail
  data records in `mails/data/`.
- `src/main/java/app/fyreplace/rest/` — `HealthEndpoint` (root `/`) and the dev-only `DevEndpoint`
  (`app.local-dev=true`, set in `%dev`).
- `src/main/resources/db/migration/` — Flyway. `B1__Django_baseline.sql` is a schema-only pg_dump
  of the legacy DB; `V2.0.0__Initial_migration.sql` is the single conversion migration.
  `flyway.migrate-at-start=true`, Hibernate `validate` — **the schema is Flyway-owned; never let
  Hibernate generate it.**

## Conventions

### Code style

Palantir Java Format via Spotless; run `./gradlew spotlessApply` before committing. Classes are
`public final`, fields/methods package-visible by default, `_` for unused parameters. The codebase
is almost comment-free by design: rely on precise naming and small methods, and comment only what
code cannot express (non-obvious constraints, intentional deviations from the Django spec).

### Nullability

Every method parameter, return type, and record component carries `@Nonnull` or `@Nullable`
(`jakarta.annotation`); primitives and `void` take none. Bind each annotation to the type it
qualifies; only Palantir may break it onto its own line. No `final` on parameters. A `Uni` is
never null and the annotation cannot target type arguments, so a `Uni` that may complete with a
null item takes an `OrNull` name suffix (`Vote.Repository.castOrNull`). `ToProtoService` shows the
style.

### Reactive chains

Everything is `Uni`/`Multi` end-to-end, with injected repository beans (no static `Panache` calls
in main code). Prefer operator chains over imperative control flow. One `.map`/`.flatMap` per value
step, `.call(...)`/`.invoke(...)` for side-effect steps, single-expression lambdas — a lambda that
needs its own operators, a block, or a local variable is a missing method: extract a private,
purpose-named one (see `User.Repository.softDeleteById`). Move blocking work off the event loop
with `.emitOn(Infrastructure.getDefaultWorkerPool())` — outside session scope (next rule).

### Sessions and transactions

A gRPC method gets no automatic session (that courtesy is JAX-RS-only):

- Reads carry `@WithSession`, writes `@WithTransaction` — on the endpoint method. A read *sub*-flow
  inside a larger chain gets `@WithSession` on a dedicated CDI method
  (e.g. `AuthorizationService.checkConnectUser`).
- Repository methods are transaction-annotation-free: the **caller** owns the unit of work.
- A session is pinned to the Vert.x thread that opened it (HR000069): **never `.emitOn(...)` inside
  a session/transaction scope** — the close rides the completion thread.
  `AccountEndpointImpl.connect` shows the shape: session-scoped lookup, then `emitOn` the bcrypt
  check, which touches no DB.

### Errors, SQL, auth

- Signal errors by throwing `app.fyreplace.errors` exceptions; `ExceptionInterceptor` maps them to
  gRPC statuses.
- Raw SQL lives **only** in Flyway migrations and entity `Repository` classes — services,
  endpoints, and tests call dedicated repository methods.
- Auth is declarative: `@Authenticated` + JWT (keys via `make keygen-rsa`; `%dev`/`%test`
  properties). Anonymous calls fail with `UNAUTHENTICATED` (`missing_credentials`); without the
  annotation an RPC stays reachable anonymously but still authenticates when a bearer token is
  present (Django `@no_auth`). Entity rules (`User.Rank`, forbidden usernames) live on entities.
- Pagination goes through `EndpointBase.paginate(...)`; cursors are proto `Page`/`Cursor` mapped by
  `FromProtoService.page(...)` — but see the double-subscribe gotcha below.

## Testing

### Fixtures

Test data is **always seeded through Flyway, never inline in test code**:

- `V1.999.*__Seed_*.sql` (`src/test/resources/db/migration/`) — legacy-shaped rows that `V2.0.0`
  converts; asserted by the migration tests under `src/test/java/app/fyreplace/data/migration/`.
- `V999.999.*` (versions above the initial migration) — new-shaped fixtures for the other data
  tests: `ban_target`, `trigger_author`, `chapter_author` (+chapters), notifications, and the
  account-scenario users `pending_user`, `passwordless_user`, `password_user` (bcrypt hash of
  `password123`), `deleted_user`.

Tests run with `%test.quarkus.flyway.clean-at-start=true`, so every seeded row shifts count
assertions — keep the `testEntityCount` checks updated.

### Data-layer tests

Extend `DataTestsBase` (migration tests extend `MigrationTestsBase`); assert with
`TransactionalUniAsserter` + `@QuarkusTest` + `@RunOnVertxContext` (method-scoped).

- Calling Panache from a lifecycle hook needs a **safe duplicated** Vert.x context:
  `@RunOnVertxContext(runOnEventLoop = false)` does *not* work with Panache. Use the
  `ExecutorService.awaitOnSafeContext` bridge instead — every test base extends `app.fyreplace.TestsBase`.
- `TransactionalUniAsserter` **commits every step** (each step is its own transaction) and has no
  rollback: keep the DB net-zero — run the scenario in one `execute(...)` step, clean up inside the
  chain, and reset seeded rows at both ends (see `UsersBanTests`, `SubscriptionsTriggerTests`).
- Standard write shape: `persist → refresh() → invoke(assert) → delete`. `DataTestsBase.refresh()`
  flushes then reloads, so assertions read DB state; the in-memory object after a bare `persist`
  proves nothing. Read-only tests over committed seed data need no flush.
- Short-circuit password checks with `User.hasUsablePassword` before `BcryptUtil.matches` (which
  throws on non-MCF hashes).

### gRPC endpoint tests

Live in `src/test/java/app/fyreplace/grpc/endpoints/` — one package per proto service, one class
per RPC, one `@Test` per calling scenario. `GrpcTestsBase` provides the channel, Mutiny stub
factory, and per-call Bearer `CallCredentials` (`@GrpcClient` cannot carry per-call metadata) plus
`assertFailure`; `AccountTestsBase` adds user/token conveniences and the account hooks that
restore/sweep the seeded scenario users, so individual tests carry no inline reset steps.

- Use plain `UniAsserter` — **not** `TransactionalUniAsserter`: its per-step session ends on
  whatever thread completed the previous step, and a gRPC call completes on a grpc executor thread
  — the resulting HR000068/69 errors break every assertion (also the root cause of
  `UNKNOWN: CompositeException` and `UNAVAILABLE: Channel shutdown invoked`).
- `@RunOnVertxContext` is **method-scoped, never class-scoped**: class scope dispatches
  `@BeforeEach`/`@AfterEach` onto the event loop, where the blocking `ExecutorService.awaitOnSafeContext` bridge
  deadlocks.
- In-process DB access goes through the base's `session(...)`/`transaction(...)` helpers, which hop
  onto a safe duplicated context *before* constructing `Panache.withSession/withTransaction` (they
  read the current context eagerly).
- `@QuarkusTestResource(JwtTestsResource.class)` on each gRPC test class generates an ephemeral RSA
  keypair for the `%test` profile.

## Gotchas

### Threading

**Hibernate Reactive thread-pinning (HR000068/HR000069) is the #1 integration hazard.** A session
is bound to the event-loop thread that opened it and its cleanup follows the completion thread of
the chain it wraps. Any thread hop inside a session/transaction scope makes the failure surface as
`CompositeException: [real failure, HR000068/69]` → `UNKNOWN` on the wire, masking the original
status. Keep session scopes free of thread hops.

Dev-mode live restarts can lose the Hibernate Reactive pool
(`UnknownServiceException: ReactiveConnectionPool`) — a full stop and fresh `quarkusDev` recovers.

### Mutiny

- A `Uni`-returning call nested inside a plain lambda (`.map`/`transform`) is **never subscribed** —
  it silently does not run. Use it as a `.call`/`flatMap` step (classic case:
  `map(s -> query.executeUpdate())` — the statement never runs; see
  `Chapter.Repository.moveByLocation`).
- `onFailure(call/flatMap)` merges a *recovering* failure into a `CompositeException` with the
  original instead of replacing it. To replace based on a synchronous check, use
  `.onFailure(...).transform(...)`.

### gRPC and JWT

- SmallRye JWT parses claims into Jakarta JSON values: a numeric claim reads back as
  `jakarta.json.JsonNumber`, not `java.lang.Number`. And read the caller from the `user_id` claim —
  `getName()` is `upn` (the username).
- A request `Multi` can only be subscribed once — subscribing twice fails with `Already subscribed`.
  `EndpointBase.paginate` and `ChapterEndpointImpl.updateImage` therefore `cache()` the incoming
  request before deriving several streams from it; do the same anywhere a request stream is consumed
  more than once.

### Database

- Soft-deletion semantics **differ per entity**. Deleted comments stay visible (`is_deleted` is a
  flag clients render as a placeholder; triggers, views, and queries intentionally include them).
  Deleted posts and users are hidden — filtered out of reads. Do not generalize one rule to the
  other.
- A rejected write poisons the transaction (Postgres 25P02): never flush-recover an expected
  failure inside a live asserter step. Run the write via `ExecutorService.awaitOnSafeContext` and assert on the
  rethrown failure's cause chain (see `ReportTests.testDuplicateReportIsRejected`).
- Hibernate 6 binds strictly: an association path (`post = ?1`) requires the entity, never its id —
  compare `post.id = ?1` instead.

### Porting status

`AccountService` is fully ported (including the Gravatar default-avatar fetch on `create`, with
SHA-256 per the current Gravatar docs rather than Django's legacy MD5), but connection flows are a **lean port**: no connection nonce,
`sendConnectionEmail`/`confirmConnection` authorize by email-token freshness only, and
`sendActivationEmail` throws `NOT_FOUND` on unknown emails where Django silently succeeded
(documented deviations). Still open: `Account.listConnections`/`disconnect`/`disconnectAll`
return empty defaults. Consult the legacy repo for the behavior being replaced.

### Misc

- Re-run `make emails` after touching any `.mjml` so the checked-in `html.html` bodies stay in
  sync with the Qute templates.
- `make keygen-rsa` writes private keys under `src/main/resources/keys/` — never commit those.
