package dev.dimo.paperwebconsole.console;

public record ConsoleEntry(
    String source,
    String timestamp,
    String level,
    String text,
    long observedAtEpochMillis
) {
}
