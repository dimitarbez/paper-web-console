# Paper Web Console

Paper Web Console is a PaperMC plugin that exposes the Minecraft server console in a browser on a dedicated HTTP port.

<img width="1920" height="870" alt="image" src="https://github.com/user-attachments/assets/2c438b25-224c-49d3-94fa-af430e3c1445" />

<img width="1920" height="870" alt="image" src="https://github.com/user-attachments/assets/989e2a3d-913d-4ba3-81ea-9114ddfe973f" />

It is intentionally narrow:

- stream recent and live console output from `logs/latest.log`
- allow authenticated admins to send console commands from the web UI
- keep deployment simple with an embedded web server and bundled static assets
- stay LAN-first with a single shared admin password and conservative defaults

## Stack

- Java 21
- Gradle Kotlin DSL
- Paper API `1.21.x`
- Javalin
- Gson
- JUnit 5
- Plain HTML, CSS, and JavaScript

## Features

- live console streaming over WebSocket
- bounded in-memory history bootstrap for new clients
- first-run setup with a one-time setup token
- PBKDF2 password hashing
- server-side sessions bound to client IP
- same-origin checks for non-GET HTTP requests and WebSocket upgrades
- per-IP login rate limiting
- browser command input with multiline paste support
- mobile-friendly terminal UI with reconnect, clear, wrap, and logout actions

## Requirements

- Java 21 JDK
- a Paper server compatible with API version `1.21`
- Gradle 8.x if you are not using the wrapper

Verify that you have a full JDK, not just a Java runtime:

```bash
java -version
javac -version
```

Both should report Java 21.

## Build

From the repository root:

```bash
./gradlew test
./gradlew shadowJar
```

The shaded plugin jar is written to:

```text
build/libs/paper-web-console-<version>.jar
```

This project uses the Shadow plugin and produces a single plugin jar with runtime dependencies bundled inside it.

## Release Automation

Pushes to `main` now create a GitHub release automatically through [.github/workflows/release.yml](.github/workflows/release.yml).

The workflow behavior is:

- it runs on every push to `main`
- it finds the latest semantic version tag in the repository
- it bumps the minor version and resets patch to `0`
- it builds `build/libs/paper-web-console-<version>.jar`
- it publishes a GitHub release with generated notes and uploads that jar

Examples:

- if the latest semver tag is `v0.1.0`, the next push to `main` releases `v0.2.0`
- if the latest semver tag is `v0.2.0`, the next push to `main` releases `v0.3.0`

The current non-semver tag `release` is ignored by the workflow, so the next automated release will start from `0.2.0` unless you create a semantic version tag first.

If you rerun the workflow for a commit that already has a semver tag, it will detect that and skip creating a second release.

### If the Gradle wrapper is incomplete

Some checkouts may be missing `gradle-wrapper.jar`. If that happens, use a modern local Gradle installation and regenerate the wrapper or build directly with that Gradle version.

Example:

```bash
gradle --version
gradle wrapper --gradle-version 8.10.2
./gradlew shadowJar
```

If you only need an artifact immediately:

```bash
gradle shadowJar
```

Use Java 21 for all compile and test work. The build is configured with `options.release = 21`.

If Gradle reports that it cannot find a Java 21 toolchain, your shell is likely using a JRE or the wrong `JAVA_HOME`. Point `JAVA_HOME` at a Java 21 JDK and retry:

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
./gradlew shadowJar
```

On Linux, if `java -version` works but Gradle still cannot find the toolchain, resolve the real JDK path and export that exact directory:

```bash
readlink -f "$(which java)"
# example output: /usr/lib/jvm/java-21-openjdk-amd64/bin/java

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test
./gradlew shadowJar
```

This repository also tells Gradle to look at `JAVA_HOME` explicitly through `gradle.properties`, so once `JAVA_HOME` points at a Java 21 JDK, the wrapper should use it.

## Install On A Paper Server

1. Build the shaded jar.
2. Copy `build/libs/paper-web-console-<version>.jar` into your server `plugins/` directory.
3. Start or restart the Paper server.
4. Watch the server log for the web console URL and first-run setup token.

On successful startup the plugin logs a line like:

```text
[PaperWebConsole] Web console listening on http://0.0.0.0:28765
```

On first run it also logs a setup URL like:

```text
http://0.0.0.0:28765/setup?token=...
```

Open that URL once to create the shared admin password.

## Access Model

By default the web server binds to:

```text
0.0.0.0:28765
```

That means:

- the web server listens on all network interfaces on port `28765`
- you can reach it from the server machine with `http://127.0.0.1:28765`
- other LAN devices can use the server's LAN IP unless a firewall blocks the port

If you want LAN access, edit the generated server config at:

```text
plugins/PaperWebConsole/config.yml
```

Example:

```yml
bindAddress: "0.0.0.0"
port: 28765
```

Then restart the server and allow the port through any local firewall.

## Security Notes

This plugin is intentionally conservative, but it is not designed to be internet-safe.

- HTTP only by design
- default bind address is `0.0.0.0`
- single shared admin password
- password hash only, never plaintext
- setup uses a one-time token with expiration
- session cookie is `HttpOnly` and `SameSite=Strict`
- sessions are bound to client IP
- login attempts are rate-limited per IP
- web-issued commands are logged with client IP context

Treat reverse-proxy support, public exposure, and HTTPS termination as security-sensitive changes rather than routine setup.

## Admin Command

The plugin registers:

```text
/webconsole <status|reload|reset-auth>
```

Permission:

```text
webconsole.admin
```

Subcommands:

- `status`: show the current bind address, port, auth state, active sessions, and buffered line count
- `reload`: reload plugin configuration by rebuilding the runtime
- `reset-auth`: clear auth state and print a fresh setup URL to the server console

## Configuration

Default config keys:

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

Default resource file:

- [src/main/resources/config.yml](/home/dimitarbez/Dev/mc-web-cli/src/main/resources/config.yml)

The generated runtime config lives under your server's plugin data folder:

```text
plugins/PaperWebConsole/config.yml
```

## HTTP And WebSocket Contract

HTTP routes:

- `GET /`
- `GET /login`
- `GET /setup`
- `GET /api/status`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `POST /api/setup`

WebSocket endpoint:

- `WS /ws/console`

Client-to-server WebSocket messages:

- `type = "ping"`
- `type = "command"`

Server-to-client WebSocket messages:

- `type = "history"`
- `type = "log"`
- `type = "status"`
- `type = "error"`

## Development Notes

### Runtime architecture

- `WebConsolePlugin` is the composition root
- `LogTailer` preloads and tails `logs/latest.log`
- `ConsoleLineParser` normalizes log lines into `ConsoleEntry`
- `ConsoleBuffer` stores bounded in-memory history
- `WebConsoleServer` serves the static UI and WebSocket endpoint
- `AuthManager` handles setup, password verification, sessions, and login throttling

### Important behavior

- Bukkit command dispatch must stay on the main server thread
- console history must remain bounded in memory
- browser and backend message contracts must stay in sync
- static assets are bundled inside the plugin jar; there is no frontend build pipeline

### Tests

Current unit tests cover:

- password hashing
- session registry behavior
- login rate limiting
- console line parsing

Run them with:

```bash
./gradlew test
```

## Repository Layout

```text
build.gradle.kts                         Build configuration and dependencies
settings.gradle.kts                      Gradle project name and plugin management
src/main/java/dev/dimo/paperwebconsole   Plugin source
src/main/resources/config.yml            Default plugin config
src/main/resources/plugin.yml            Paper plugin metadata
src/main/resources/web                   Bundled static web UI
src/test/java/dev/dimo/paperwebconsole   Unit tests
AGENTS.md                                Repository guide for coding agents
```

## Suggested Local Verification

When your environment supports it:

1. Run `./gradlew test`
2. Run `./gradlew shadowJar`
3. Drop the jar into a Paper test server
4. Verify first-run setup
5. Verify login and logout
6. Verify history preload and live streaming
7. Verify command dispatch and audit logging
8. Verify `/webconsole reload` and `/webconsole reset-auth`
