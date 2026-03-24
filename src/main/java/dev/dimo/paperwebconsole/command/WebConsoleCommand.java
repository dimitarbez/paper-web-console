package dev.dimo.paperwebconsole.command;

import dev.dimo.paperwebconsole.WebConsolePlugin;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class WebConsoleCommand implements CommandExecutor, TabCompleter {
    private final WebConsolePlugin plugin;

    public WebConsoleCommand(WebConsolePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("webconsole.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /webconsole <status|reload|reset-auth>");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sender.sendMessage(ChatColor.GRAY + plugin.statusSummary());
                yield true;
            }
            case "reload" -> {
                boolean reloaded = plugin.reloadRuntime();
                sender.sendMessage(reloaded ? ChatColor.GREEN + "Web console configuration reloaded." : ChatColor.RED + "Reload failed. Check the server log.");
                yield true;
            }
            case "reset-auth" -> {
                try {
                    plugin.resetAuthentication();
                    sender.sendMessage(ChatColor.GREEN + "Authentication reset. A fresh setup URL was logged to the console.");
                } catch (IOException exception) {
                    sender.sendMessage(ChatColor.RED + "Failed to reset authentication. Check the server log.");
                    plugin.getLogger().log(Level.SEVERE, "Failed to reset authentication.", exception);
                }
                yield true;
            }
            default -> {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /webconsole <status|reload|reset-auth>");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("status", "reload", "reset-auth")
                .stream()
                .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
        }
        return List.of();
    }
}
