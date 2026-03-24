package dev.dimo.paperwebconsole.console;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ConsoleBuffer {
    private final int limit;
    private final Deque<ConsoleEntry> entries = new ArrayDeque<>();

    public ConsoleBuffer(int limit) {
        this.limit = limit;
    }

    public synchronized void add(ConsoleEntry entry) {
        if (entries.size() >= limit) {
            entries.removeFirst();
        }
        entries.addLast(entry);
    }

    public synchronized List<ConsoleEntry> snapshot() {
        return new ArrayList<>(entries);
    }

    public synchronized int size() {
        return entries.size();
    }
}
