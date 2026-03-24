package dev.dimo.paperwebconsole;

import dev.dimo.paperwebconsole.auth.AuthManager;
import dev.dimo.paperwebconsole.auth.PasswordHasher;
import dev.dimo.paperwebconsole.command.WebConsoleCommand;
import dev.dimo.paperwebconsole.config.PluginConfiguration;
import dev.dimo.paperwebconsole.console.ConsoleBuffer;
import dev.dimo.paperwebconsole.console.ConsoleEntry;
import dev.dimo.paperwebconsole.console.ConsoleLineParser;
import dev.dimo.paperwebconsole.console.LogTailer;
import dev.dimo.paperwebconsole.web.WebConsoleServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WebConsolePlugin extends JavaPlugin {
    private final Clock clock = Clock.systemDefaultZone();

    private PluginConfiguration configuration;
    private ConsoleLineParser consoleLineParser;
    private ConsoleBuffer consoleBuffer;
    private AuthManager authManager;
    private LogTailer logTailer;
    private WebConsoleServer webConsoleServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerCommands();

        try {
            startRuntime();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Unable to start the web console plugin.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        stopRuntime();
    }

    public synchronized boolean reloadRuntime() {
        stopRuntime();
        reloadConfig();

        try {
            startRuntime();
            return true;
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Reload failed. Disabling plugin to avoid a partial state.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    public synchronized void resetAuthentication() throws IOException {
        ensureRunning();
        authManager.resetAuth();
        if (webConsoleServer != null) {
            webConsoleServer.disconnectAll("Authentication reset by an admin.");
        }

        getLogger().warning("Authentication has been reset.");
        logSetupInstructions();
    }

    public synchronized String statusSummary() {
        ensureRunning();
        return String.format(
            Locale.ROOT,
            "bind=%s port=%d configured=%s activeSessions=%d bufferedLines=%d",
            configuration.bindAddress(),
            configuration.port(),
            authManager.isConfigured(),
            authManager.activeSessions(),
            consoleBuffer.size()
        );
    }

    public synchronized PluginConfiguration configuration() {
        ensureRunning();
        return configuration;
    }

    public synchronized String currentBaseUrl() {
        ensureRunning();
        return "http://" + displayHost(configuration.bindAddress()) + ":" + configuration.port();
    }

    public synchronized void dispatchWebCommand(String rawCommand, String clientIp) {
        ensureRunning();
        for (String command : rawCommand.split("\\R")) {
            String trimmed = command.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            ConsoleEntry entry = consoleLineParser.commandEntry(trimmed, Instant.now(clock));
            handleConsoleEntry(entry);
            getLogger().info("Web console command from " + clientIp + ": /" + trimmed);

            getServer().getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed));
        }
    }

    public synchronized AuthManager authManager() {
        ensureRunning();
        return authManager;
    }

    private void startRuntime() throws Exception {
        configuration = PluginConfiguration.from(getConfig());
        consoleLineParser = new ConsoleLineParser(clock);
        consoleBuffer = new ConsoleBuffer(configuration.ui().maxBufferedLines());

        Files.createDirectories(getDataFolder().toPath());
        authManager = AuthManager.load(
            getDataFolder().toPath().resolve("auth.json"),
            new PasswordHasher(),
            Duration.ofHours(configuration.sessionHours()),
            Duration.ofMinutes(configuration.setupTokenTtlMinutes()),
            Duration.ofMinutes(configuration.loginRateLimitWindowMinutes()),
            configuration.loginRateLimitMaxAttempts(),
            clock
        );

        logTailer = new LogTailer(
            resolveLatestLogPath(),
            configuration.historyLimit(),
            consoleLineParser,
            this::handleConsoleEntry,
            getLogger(),
            clock
        );
        logTailer.start();

        webConsoleServer = new WebConsoleServer(this, configuration, authManager, consoleBuffer);
        webConsoleServer.start();

        handleConsoleEntry(consoleLineParser.statusEntry("Web console listening on " + currentBaseUrl(), Instant.now(clock)));
        getLogger().info("Web console listening on " + currentBaseUrl());
        if (!authManager.isConfigured()) {
            logSetupInstructions();
        }
    }

    private void stopRuntime() {
        if (webConsoleServer != null) {
            webConsoleServer.stop();
            webConsoleServer = null;
        }

        if (logTailer != null) {
            logTailer.stop();
            logTailer = null;
        }

        consoleBuffer = null;
        consoleLineParser = null;
        authManager = null;
        configuration = null;
    }

    private void handleConsoleEntry(ConsoleEntry entry) {
        if (consoleBuffer != null) {
            consoleBuffer.add(entry);
        }
        if (webConsoleServer != null) {
            webConsoleServer.broadcastLog(entry);
        }
    }

    private void logSetupInstructions() {
        String setupUrl = currentBaseUrl() + "/setup?token=" + authManager.currentSetupToken();
        getLogger().warning("Initial setup is pending.");
        getLogger().warning("Open this URL once to finish setup: " + setupUrl);
    }

    private void registerCommands() {
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("webconsole"), "Command /webconsole must exist in plugin.yml");
        WebConsoleCommand command = new WebConsoleCommand(this);
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }

    private Path resolveLatestLogPath() {
        Path dataFolder = getDataFolder().toPath().toAbsolutePath();
        Path pluginsDirectory = dataFolder.getParent();
        Path serverRoot = pluginsDirectory != null ? pluginsDirectory.getParent() : null;
        if (serverRoot == null) {
            serverRoot = Path.of(".").toAbsolutePath().normalize();
        }
        return serverRoot.resolve("logs").resolve("latest.log");
    }

    private String displayHost(String bindAddress) {
        if ("0.0.0.0".equals(bindAddress) || "::".equals(bindAddress)) {
            return "localhost";
        }
        return bindAddress;
    }

    private void ensureRunning() {
        if (configuration == null || authManager == null || consoleBuffer == null) {
            throw new IllegalStateException("Plugin runtime is not initialized.");
        }
    }
}
