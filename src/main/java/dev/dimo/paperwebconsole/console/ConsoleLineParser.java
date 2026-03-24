package dev.dimo.paperwebconsole.console;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConsoleLineParser {
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[;\\d]*[ -/]*[@-~]");
    private static final Pattern MC_COLOR_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
    private static final Pattern PAPER_PATTERN = Pattern.compile("^\\[(?<time>\\d{2}:\\d{2}:\\d{2}) (?<level>[^\\]]+)]\\s*:?\\s*(?<message>.*)$");

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Clock clock;

    public ConsoleLineParser(Clock clock) {
        this.clock = clock;
    }

    public ConsoleEntry parseLogLine(String rawLine, Instant observedAt) {
        String cleaned = sanitize(rawLine);
        Matcher matcher = PAPER_PATTERN.matcher(cleaned);
        if (matcher.matches()) {
            return new ConsoleEntry(
                "log",
                matcher.group("time"),
                matcher.group("level").trim(),
                matcher.group("message"),
                observedAt.toEpochMilli()
            );
        }

        return new ConsoleEntry("log", formatTime(observedAt), "INFO", cleaned, observedAt.toEpochMilli());
    }

    public ConsoleEntry commandEntry(String command, Instant observedAt) {
        return new ConsoleEntry("command", formatTime(observedAt), "COMMAND", command, observedAt.toEpochMilli());
    }

    public ConsoleEntry statusEntry(String message, Instant observedAt) {
        return new ConsoleEntry("status", formatTime(observedAt), "SYSTEM", message, observedAt.toEpochMilli());
    }

    public Instant now() {
        return Instant.now(clock);
    }

    private String formatTime(Instant observedAt) {
        return timeFormatter.format(observedAt);
    }

    private String sanitize(String rawLine) {
        String withoutAnsi = ANSI_PATTERN.matcher(rawLine).replaceAll("");
        return MC_COLOR_PATTERN.matcher(withoutAnsi).replaceAll("").replace("\r", "");
    }
}
