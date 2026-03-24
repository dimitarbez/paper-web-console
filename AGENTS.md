# Repository Guide For Coding Agents

## Mission

This repository contains a PaperMC plugin that exposes the Minecraft server console in a browser on a dedicated HTTP port. The product is intentionally narrow:

- stream recent and live console output from `logs/latest.log`
- allow authenticated admins to send console commands from the web UI
- present the UI as a polished, modern terminal rather than a game-themed dashboard
- keep the deployment model simple: embedded web server, static assets, single shared admin password, LAN-first security posture

If you add features, keep that product shape intact unless the user explicitly asks to widen scope.

## Current Stack

- Java 21 target
- Gradle Kotlin DSL
- Paper API `1.21.x` as `compileOnly`
- Javalin for the embedded HTTP and WebSocket server
- Gson for JSON payloads and persisted auth state
- JUnit 5 for unit tests
- Plain HTML, CSS, and JavaScript for the frontend

## Build And Tooling Expectations

- Use Java 21 for compile and test work. The project is configured with `options.release = 21`.
- Prefer `./gradlew test` and `./gradlew shadowJar` when the wrapper is available.
- If the Gradle wrapper jar is missing in this workspace, use a modern local Gradle release and regenerate the wrapper before changing build logic.
- Do not introduce a Node, npm, Vite, React, or Tailwind toolchain unless the user explicitly requests a frontend stack change.
- Keep runtime dependencies shaded into the plugin jar through the Shadow plugin.

## Repository Layout

- `build.gradle.kts`
  - build configuration, Java toolchain, dependencies, shaded jar output
- `src/main/java/dev/dimo/paperwebconsole/WebConsolePlugin.java`
  - plugin entrypoint and lifecycle orchestration
- `src/main/java/dev/dimo/paperwebconsole/auth`
  - password hashing, session state, rate limiting, persisted auth data
- `src/main/java/dev/dimo/paperwebconsole/command`
  - Bukkit `/webconsole` admin command
- `src/main/java/dev/dimo/paperwebconsole/config`
  - validated plugin configuration model
- `src/main/java/dev/dimo/paperwebconsole/console`
  - console entries, line parsing, in-memory buffer, `latest.log` tailing
- `src/main/java/dev/dimo/paperwebconsole/web`
  - Javalin server, HTTP routes, WebSocket contract, static page delivery
- `src/main/resources/config.yml`
  - default runtime settings
- `src/main/resources/plugin.yml`
  - Paper metadata, command registration, permissions
- `src/main/resources/web`
  - HTML shell pages plus the browser UI assets
- `src/test/java/dev/dimo/paperwebconsole`
  - unit tests for auth and console parsing logic

## Architectural Overview

### Plugin lifecycle

`WebConsolePlugin` is the composition root.

On enable it should:

1. save default config
2. register `/webconsole`
3. load validated configuration
4. create auth state from the plugin data folder
5. create the in-memory console buffer
6. preload and tail `logs/latest.log`
7. start the embedded web server
8. log setup instructions if auth is not configured yet

On disable it should:

- stop the web server first
- stop the log tailer
- release references so reloads do not keep stale state alive

Runtime reloads are handled inside the plugin class by tearing down and rebuilding the runtime graph.

### Console data flow

- `LogTailer` reads the tail of `logs/latest.log` on startup and emits parsed `ConsoleEntry` objects.
- `ConsoleLineParser` normalizes timestamps, log levels, and ANSI-stripped text.
- `ConsoleBuffer` keeps a bounded in-memory history used for WebSocket history bootstrap.
- `WebConsoleServer` broadcasts each `ConsoleEntry` to connected clients over `/ws/console`.
- Web-issued commands are also converted into `ConsoleEntry` items so they appear in the stream immediately.

### Command execution flow

- The browser sends a WebSocket message with `{ "type": "command", "command": "..." }`.
- `WebConsoleServer` authenticates the session again on each message.
- `WebConsolePlugin.dispatchWebCommand(...)` audits the request and schedules the actual Bukkit console dispatch on the main server thread.

That main-thread handoff is important. Do not call Bukkit command execution from the Javalin or log-tailer threads.

## Security Model

The current security posture is intentionally conservative but scoped to a trusted network.

- HTTP only by design. Do not present this as internet-safe.
- Default bind address must stay `127.0.0.1` unless the admin opts into broader exposure.
- Single shared admin password for v1.
- Store only a salted PBKDF2-derived password hash, never plaintext.
- First-run setup uses a one-time setup token printed to the Minecraft console.
- Sessions are stored server-side and bound to the client IP.
- Session cookies are `HttpOnly` and `SameSite=Strict`.
- Non-GET API routes must pass same-origin checks.
- WebSocket upgrades must also pass same-origin checks and a valid session check.
- Login attempts are rate-limited per IP.
- Web-issued commands must be logged to the server log with client IP context.

Do not weaken these invariants casually. If the user requests reverse proxy support, HTTPS termination, or public exposure, treat that as a security-sensitive redesign rather than a small tweak.

## Product Invariants

Preserve these assumptions unless the user asks for a different product:

- one admin account, not a role system
- browser console, not a general-purpose web admin dashboard
- static bundled assets, not a separate SPA deployment
- no database
- no remote shell beyond Minecraft console commands
- no direct integration with RCON
- no dependence on Minecraft internals for log capture when `logs/latest.log` tailing is sufficient

## Backend Conventions

- Keep configuration validation in `PluginConfiguration` or close to it.
- Keep auth concerns in the `auth` package. Do not spread password or session logic into route handlers.
- Keep WebSocket payload shapes explicit and small. This codebase currently uses local record types for transport objects.
- Prefer immutable records and straightforward state containers where possible.
- Use `Clock` injection for time-sensitive components when adding testable logic.
- Preserve the bounded-memory behavior of the console history.
- Keep background work isolated to dedicated classes like `LogTailer`.

## Frontend Conventions

The UI direction is terminal-first and intentionally restrained.

- Keep the look modern, minimal, and neutral.
- Avoid Minecraft textures, pixel fonts, neon gamer dashboards, and novelty console chrome.
- Use the existing warm-neutral palette and monospace-first typography as the baseline.
- Preserve the split between terminal output and the slim status rail.
- Favor small, meaningful transitions over constant animation.
- Keep the UI usable on narrow screens by retaining the collapsible side panel pattern.
- Avoid introducing framework complexity for simple interactions.

### UX behaviors worth preserving

- auto-scroll pauses when the user scrolls up
- command history recall with Up and Down
- Enter sends, Shift+Enter supports multiline input
- copy-per-line behavior
- visible connection state
- quick reconnect, clear, wrap, and logout actions

## HTTP And WebSocket Contract

Current HTTP routes:

- `GET /`
- `GET /login`
- `GET /setup`
- `GET /api/status`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `POST /api/setup`

Current WebSocket endpoint:

- `WS /ws/console`

Current client-to-server WebSocket messages:

- `type = "ping"`
- `type = "command"`

Current server-to-client WebSocket messages:

- `type = "history"`
- `type = "log"`
- `type = "status"`
- `type = "error"`

If you change these contracts, update both the server and the static frontend in the same change and document the new payload shape in this file.

## Configuration Contract

The current config surface is:

- `bindAddress`
- `port`
- `historyLimit`
- `sessionHours`
- `setupTokenTtlMinutes`
- `loginRateLimitWindowMinutes`
- `loginRateLimitMaxAttempts`
- `ui.showTimestamps`
- `ui.maxBufferedLines`
- `ui.defaultWrapMode`

Rules for config changes:

- validate new values with explicit ranges or non-empty checks
- provide safe defaults
- keep comments in `config.yml` aligned with behavior
- treat broadening network exposure as a security-sensitive change

## Testing Guidance

There are currently focused unit tests around auth and console parsing. Keep building on that style.

Add or update tests when touching:

- password hashing or verification
- session expiry or IP binding
- login throttling
- config validation ranges
- log line parsing
- log rotation and truncation handling
- message contract serialization

Prefer unit tests for pure logic and small state machines. Integration testing against a live Paper server is valuable, but that should supplement unit coverage rather than replace it.

## Suggested Local Verification

When the environment supports it, use this sequence:

1. run `./gradlew test`
2. run `./gradlew shadowJar`
3. drop the shaded jar into a Paper test server
4. verify first-run setup
5. verify login and logout
6. verify history preload and live streaming
7. verify command dispatch and server-side audit logging
8. verify `/webconsole reload` and `/webconsole reset-auth`

If build tooling is unavailable in the environment, say so clearly in your final response and separate implemented code changes from unverified behavior.

## Known Sensitive Areas

- `WebConsolePlugin.dispatchWebCommand(...)`
  - command dispatch must stay on the Bukkit main thread
- `AuthManager`
  - changes can affect setup, login, logout, and session integrity at once
- `WebConsoleServer.verifySameOrigin(...)`
  - same-origin enforcement is easy to weaken accidentally
- `LogTailer`
  - watch for truncation, rotation, and partial-line edge cases
- static frontend assets
  - route changes and payload changes must remain in sync with server behavior

## Preferred Change Strategy

For most work in this repository:

1. inspect the relevant package boundaries first
2. keep changes local to one concern where possible
3. add or update tests for the affected logic
4. verify the browser and backend contracts still match
5. summarize any build or runtime limitations honestly

## Avoid These Mistakes

- do not add an exposed public-web deployment story without explicit user intent
- do not bypass the existing auth flow for convenience during development
- do not move command execution off the Bukkit scheduler handoff
- do not make the UI look like a Minecraft control panel unless asked
- do not add a frontend build pipeline for small UI changes
- do not silently change cookie, origin, or session semantics
- do not let memory usage grow unbounded with console history

## If You Need To Extend The Project

Reasonable future extensions:

- better test coverage around WebSocket payloads and auth workflows
- clearer status reporting in the UI
- log rotation regression tests
- optional reverse-proxy-aware configuration, if requested and designed carefully
- optional richer filtering or search in the local browser view

Changes that need explicit user confirmation before proceeding:

- multiple user accounts or roles
- HTTPS inside the plugin
- internet-facing deployment guidance
- command autocomplete against the server
- storing console history outside memory
- swapping the frontend to a JS framework

## Documentation Maintenance

If you materially change the architecture, endpoints, config schema, or security model, update this file in the same patch. `AGENTS.md` should stay accurate enough that a new coding agent can use it as the first source of truth for how to work in this repository.
