package dev.dimo.paperwebconsole.console;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ConsoleLineParserTest {
    @Test
    void parsesPaperLogPrefixAndStripsAnsiSequences() {
        ConsoleLineParser parser = new ConsoleLineParser(Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC));

        ConsoleEntry entry = parser.parseLogLine("\u001B[32m[12:30:45 INFO]: Done (1.23s)! For help, type \"help\"\u001B[0m", Instant.parse("2026-03-24T12:30:45Z"));

        assertEquals("12:30:45", entry.timestamp());
        assertEquals("INFO", entry.level());
        assertEquals("Done (1.23s)! For help, type \"help\"", entry.text());
    }
}
