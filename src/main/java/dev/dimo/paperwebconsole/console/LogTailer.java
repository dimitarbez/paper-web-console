package dev.dimo.paperwebconsole.console;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LogTailer {
    private final Path logFile;
    private final int historyLimit;
    private final ConsoleLineParser lineParser;
    private final Consumer<ConsoleEntry> consumer;
    private final Logger logger;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ByteArrayOutputStream partialLine = new ByteArrayOutputStream();

    private ScheduledExecutorService executorService;
    private volatile long position;
    private volatile Object fileKey;

    public LogTailer(
        Path logFile,
        int historyLimit,
        ConsoleLineParser lineParser,
        Consumer<ConsoleEntry> consumer,
        Logger logger,
        Clock clock
    ) {
        this.logFile = logFile;
        this.historyLimit = historyLimit;
        this.lineParser = lineParser;
        this.consumer = consumer;
        this.logger = logger;
        this.clock = clock;
    }

    public synchronized void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        loadInitialHistory();
        syncCursor();

        executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "paper-web-console-logtailer");
            thread.setDaemon(true);
            return thread;
        });
        executorService.scheduleWithFixedDelay(this::safePoll, 250L, 250L, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    private void loadInitialHistory() throws IOException {
        if (!Files.exists(logFile)) {
            return;
        }

        Deque<String> recentLines = new ArrayDeque<>();
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (recentLines.size() >= historyLimit) {
                    recentLines.removeFirst();
                }
                recentLines.addLast(line);
            }
        }

        for (String line : recentLines) {
            consumer.accept(lineParser.parseLogLine(line, Instant.now(clock)));
        }
    }

    private void syncCursor() throws IOException {
        if (!Files.exists(logFile)) {
            position = 0L;
            fileKey = null;
            partialLine.reset();
            return;
        }

        BasicFileAttributes attributes = Files.readAttributes(logFile, BasicFileAttributes.class);
        position = attributes.size();
        fileKey = attributes.fileKey();
        partialLine.reset();
    }

    private void safePoll() {
        if (!running.get()) {
            return;
        }

        try {
            poll();
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to tail latest.log.", exception);
        }
    }

    private void poll() throws IOException {
        if (!Files.exists(logFile)) {
            position = 0L;
            fileKey = null;
            partialLine.reset();
            return;
        }

        BasicFileAttributes attributes = Files.readAttributes(logFile, BasicFileAttributes.class);
        Object currentFileKey = attributes.fileKey();
        long currentSize = attributes.size();

        if (!Objects.equals(fileKey, currentFileKey) || currentSize < position) {
            position = 0L;
            fileKey = currentFileKey;
            partialLine.reset();
        }

        try (SeekableByteChannel channel = Files.newByteChannel(logFile, StandardOpenOption.READ)) {
            channel.position(position);
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (channel.read(buffer) > 0) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    byte next = buffer.get();
                    position++;
                    if (next == '\n') {
                        emitCurrentLine();
                    } else {
                        partialLine.write(next);
                    }
                }
                buffer.clear();
            }
        }
    }

    private void emitCurrentLine() {
        byte[] lineBytes = partialLine.toByteArray();
        partialLine.reset();

        String rawLine = new String(lineBytes, StandardCharsets.UTF_8).replace("\r", "");
        consumer.accept(lineParser.parseLogLine(rawLine, Instant.now(clock)));
    }
}
