package dev.dimo.paperwebconsole.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PluginConfiguration(
    String bindAddress,
    int port,
    int historyLimit,
    int sessionHours,
    int setupTokenTtlMinutes,
    int loginRateLimitWindowMinutes,
    int loginRateLimitMaxAttempts,
    UiConfiguration ui
) {
    public static PluginConfiguration from(FileConfiguration fileConfiguration) {
        String bindAddress = requireText(fileConfiguration.getString("bindAddress", "127.0.0.1"), "bindAddress");
        int port = requireRange(fileConfiguration.getInt("port", 28765), 1, 65535, "port");
        int historyLimit = requireRange(fileConfiguration.getInt("historyLimit", 250), 25, 10_000, "historyLimit");
        int sessionHours = requireRange(fileConfiguration.getInt("sessionHours", 12), 1, 168, "sessionHours");
        int setupTokenTtlMinutes = requireRange(fileConfiguration.getInt("setupTokenTtlMinutes", 30), 5, 1_440, "setupTokenTtlMinutes");
        int loginRateLimitWindowMinutes = requireRange(fileConfiguration.getInt("loginRateLimitWindowMinutes", 15), 1, 1_440, "loginRateLimitWindowMinutes");
        int loginRateLimitMaxAttempts = requireRange(fileConfiguration.getInt("loginRateLimitMaxAttempts", 5), 2, 50, "loginRateLimitMaxAttempts");
        UiConfiguration ui = new UiConfiguration(
            fileConfiguration.getBoolean("ui.showTimestamps", true),
            requireRange(fileConfiguration.getInt("ui.maxBufferedLines", 2_000), 200, 20_000, "ui.maxBufferedLines"),
            fileConfiguration.getBoolean("ui.defaultWrapMode", true)
        );

        return new PluginConfiguration(
            bindAddress,
            port,
            historyLimit,
            sessionHours,
            setupTokenTtlMinutes,
            loginRateLimitWindowMinutes,
            loginRateLimitMaxAttempts,
            ui
        );
    }

    private static String requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank.");
        }
        return value.trim();
    }

    private static int requireRange(int value, int minimum, int maximum, String key) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum + ".");
        }
        return value;
    }

    public record UiConfiguration(boolean showTimestamps, int maxBufferedLines, boolean defaultWrapMode) {
    }
}
