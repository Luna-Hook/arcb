package dev.arcb;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ARCBPlugin extends JavaPlugin implements TabExecutor {

    private CommandManager commandManager;
    private CraftBlockManager craftBlockManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureDirectories();
        saveResourceIfMissing("commands" + File.separator + "-clearshulker.yml");
        saveResourceIfMissing("craftblocks" + File.separator + "-blockmace.yml");

        commandManager = new CommandManager(this);
        craftBlockManager = new CraftBlockManager(this);

        if (getCommand("arcb") != null) {
            getCommand("arcb").setExecutor(this);
            getCommand("arcb").setTabCompleter(this);
        }

        reloadAll();
    }

    @Override
    public void onDisable() {
        commandManager.stopAll();
    }

    public void reloadAll() {
        reloadConfig();
        commandManager.loadAll();
        craftBlockManager.loadAll();
        if (isPluginEnabled()) {
            int c = commandManager.getActiveCount();
            int b = craftBlockManager.getActiveCount();
            if (c > 0 || b > 0) {
                getLogger().info("Ran all ARCB commands (" + c + " command(s), " + b + " craft block(s) active)");
            }
        }
    }

    public boolean isPluginEnabled() {
        return getConfig().getBoolean("plugin-enabled", true);
    }

    void setPluginEnabled(boolean enabled) {
        getConfig().set("plugin-enabled", enabled);
        saveConfig();
        reloadAll();
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public CraftBlockManager getCraftBlockManager() {
        return craftBlockManager;
    }

    // ---------- resource helpers ----------

    private void ensureDirectories() {
        new File(getDataFolder(), "commands").mkdirs();
        new File(getDataFolder(), "craftblocks").mkdirs();
    }

    private void saveResourceIfMissing(String path) {
        File f = new File(getDataFolder(), path);
        if (!f.exists()) {
            saveResource(path, false);
        }
    }

    // ---------- command dispatch ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("arcb.admin")) {
            sendMessage(sender, "messages.no-permission");
            return true;
        }
        if (args.length == 0) {
            sendMessage(sender, "messages.usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "plugin" -> handlePlugin(sender, args);
            case "commands" -> handleCommands(sender, args);
            case "craftblocks" -> handleCraftBlocks(sender, args);
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            default -> sendMessage(sender, "messages.usage");
        }
        return true;
    }

    private void handlePlugin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "messages.usage");
            return;
        }
        String act = args[1].toLowerCase(Locale.ROOT);
        switch (act) {
            case "enable" -> {
                if (isPluginEnabled()) {
                    sendMessage(sender, "messages.plugin-already-enabled");
                    return;
                }
                setPluginEnabled(true);
                sendMessage(sender, "messages.plugin-enabled");
            }
            case "disable" -> {
                if (!isPluginEnabled()) {
                    sendMessage(sender, "messages.plugin-already-disabled");
                    return;
                }
                setPluginEnabled(false);
                sendMessage(sender, "messages.plugin-disabled");
            }
            default -> sendMessage(sender, "messages.usage");
        }
    }

    private void handleCommands(CommandSender sender, String[] args) {
        if (args.length < 2) {
            listNamed(sender, commandManager, "status-commands-header", "status-command");
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> listNamed(sender, commandManager, "status-commands-header", "status-command");
            case "enable" -> {
                if (args.length < 3) { sendMessage(sender, "messages.usage"); return; }
                String name = args[2];
                switch (commandManager.enableFile(name)) {
                    case 0 -> sendMessage(sender, "messages.command-already-enabled", "%name%", name);
                    case 1 -> { sendMessage(sender, "messages.command-enabled", "%name%", name); reloadAll(); }
                    case -1 -> sendMessage(sender, "messages.command-not-found", "%name%", name);
                }
            }
            case "disable" -> {
                if (args.length < 3) { sendMessage(sender, "messages.usage"); return; }
                String name = args[2];
                switch (commandManager.disableFile(name)) {
                    case 0 -> sendMessage(sender, "messages.command-already-disabled", "%name%", name);
                    case 1 -> { sendMessage(sender, "messages.command-disabled", "%name%", name); reloadAll(); }
                    case -1 -> sendMessage(sender, "messages.command-not-found", "%name%", name);
                }
            }
            case "group" -> handleGroup(sender, args, commandManager);
            case "whitelist" -> handleWhitelist(sender, args, commandManager);
            default -> sendMessage(sender, "messages.usage");
        }
    }

    private void handleCraftBlocks(CommandSender sender, String[] args) {
        if (args.length < 2) {
            listNamed(sender, craftBlockManager, "status-craftblocks-header", "status-craftblock");
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> listNamed(sender, craftBlockManager, "status-craftblocks-header", "status-craftblock");
            case "enable" -> {
                if (args.length < 3) { sendMessage(sender, "messages.usage"); return; }
                String name = args[2];
                switch (craftBlockManager.enableFile(name)) {
                    case 0 -> sendMessage(sender, "messages.craftblock-already-enabled", "%name%", name);
                    case 1 -> { sendMessage(sender, "messages.craftblock-enabled", "%name%", name); reloadAll(); }
                    case -1 -> sendMessage(sender, "messages.craftblock-not-found", "%name%", name);
                }
            }
            case "disable" -> {
                if (args.length < 3) { sendMessage(sender, "messages.usage"); return; }
                String name = args[2];
                switch (craftBlockManager.disableFile(name)) {
                    case 0 -> sendMessage(sender, "messages.craftblock-already-disabled", "%name%", name);
                    case 1 -> { sendMessage(sender, "messages.craftblock-disabled", "%name%", name); reloadAll(); }
                    case -1 -> sendMessage(sender, "messages.craftblock-not-found", "%name%", name);
                }
            }
            case "group" -> handleGroup(sender, args, craftBlockManager);
            case "whitelist" -> handleWhitelist(sender, args, craftBlockManager);
            default -> sendMessage(sender, "messages.usage");
        }
    }

    private void handleGroup(CommandSender sender, String[] args, FileManager mgr) {
        if (args.length < 4) {
            sendMessage(sender, "messages.usage");
            return;
        }
        String act = args[3].toLowerCase(Locale.ROOT);
        String name = args[2];
        switch (act) {
            case "enable" -> {
                switch (mgr.enableGroup(name)) {
                    case 0 -> sendMessage(sender, "messages.group-already-enabled", "%name%", name);
                    case 1 -> { sendMessage(sender, "messages.group-enabled", "%name%", name); reloadAll(); }
                    case -1 -> sendMessage(sender, "messages.group-not-found", "%name%", name);
                }
            }
            case "disable" -> {
                switch (mgr.disableGroup(name)) {
                    case 0 -> sendMessage(sender, "messages.group-already-disabled", "%name%", name);
                    case 1 -> { sendMessage(sender, "messages.group-disabled", "%name%", name); reloadAll(); }
                    case -1 -> sendMessage(sender, "messages.group-not-found", "%name%", name);
                }
            }
            default -> sendMessage(sender, "messages.usage");
        }
    }

    private void handleWhitelist(CommandSender sender, String[] args, FileManager mgr) {
        if (args.length < 3) {
            sendMessage(sender, "messages.usage");
            return;
        }
        String sub = args[2].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                if (args.length < 4) { sendMessage(sender, "messages.usage"); return; }
                String name = args[3];
                List<String> wl = mgr.getWhitelist(name);
                if (wl == null) {
                    sendMessage(sender, "messages.whitelist-file-not-found", "%name%", name);
                    return;
                }
                sendMessage(sender, "messages.whitelist-list-header", "%name%", name);
                if (wl.isEmpty()) {
                    sendMessage(sender, "messages.whitelist-none");
                    return;
                }
                for (String p : wl) {
                    sendMessage(sender, "messages.whitelist-entry", "%player%", p);
                }
            }
            case "add" -> {
                if (args.length < 5) { sendMessage(sender, "messages.usage"); return; }
                String name = args[3];
                String player = args[4];
                switch (mgr.addWhitelist(name, player)) {
                    case 0 -> sendMessage(sender, "messages.whitelist-already-present", "%name%", name, "%player%", player);
                    case 1 -> sendMessage(sender, "messages.whitelist-added", "%name%", name, "%player%", player);
                    case -1 -> sendMessage(sender, "messages.whitelist-file-not-found", "%name%", name);
                }
            }
            case "remove" -> {
                if (args.length < 5) { sendMessage(sender, "messages.usage"); return; }
                String name = args[3];
                String player = args[4];
                switch (mgr.removeWhitelist(name, player)) {
                    case 0 -> sendMessage(sender, "messages.whitelist-not-present", "%name%", name, "%player%", player);
                    case 1 -> sendMessage(sender, "messages.whitelist-removed", "%name%", name, "%player%", player);
                    case -1 -> sendMessage(sender, "messages.whitelist-file-not-found", "%name%", name);
                }
            }
            default -> sendMessage(sender, "messages.usage");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!getConfig().getBoolean("settings.allow-runtime-reload", true)) {
            sendMessage(sender, "messages.reload-disabled");
            return;
        }
        reloadAll();
        sendMessage(sender, "messages.reloaded");
    }

    private void handleStatus(CommandSender sender) {
        sendMessage(sender, "messages.status-header");
        sendMessage(sender, "messages.status-plugin", "%value%", isPluginEnabled() ? "&aON" : "&cOFF");
        listNamed(sender, commandManager, "status-commands-header", "status-command");
        listNamed(sender, craftBlockManager, "status-craftblocks-header", "status-craftblock");
    }

    private void listNamed(CommandSender sender, FileManager mgr, String headerPath, String entryPath) {
        sendMessage(sender, headerPath);
        List<String> names = new ArrayList<>(mgr.getAllNames());
        Collections.sort(names);
        if (names.isEmpty()) {
            sender.sendMessage("  &7(none)");
        }
        for (String n : names) {
            boolean enabled = mgr.isEnabled(n);
            sendMessage(sender, entryPath, "%name%", n, "%value%", enabled ? "&aON" : "&cOFF");
        }
    }

    // ---------- messaging ----------

    void sendMessage(CommandSender sender, String path, String... replacements) {
        String msg = getConfig().getString(path, path);
        String prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.prefix", ""));
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        String result = prefix + ChatColor.translateAlternateColorCodes('&', msg);
        if (!result.isBlank()) {
            sender.sendMessage(result);
        }
    }

    // ---------- tab completion ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("arcb.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(args[0], List.of("plugin", "commands", "craftblocks", "status", "reload"));
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "plugin" -> { return filter(args[1], List.of("enable", "disable")); }
                case "commands" -> { return filter(args[1], List.of("list", "enable", "disable", "group", "whitelist")); }
                case "craftblocks" -> { return filter(args[1], List.of("list", "enable", "disable", "group", "whitelist")); }
            }
        }
        if (args.length == 3) {
            String root = args[0].toLowerCase(Locale.ROOT);
            switch (root) {
                case "commands" -> {
                    switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "enable" -> { return filter(args[2], commandManager.getDisabledNames()); }
                        case "disable" -> { return filter(args[2], commandManager.getEnabledNames()); }
                        case "group" -> { return filter(args[2], commandManager.getAllGroups()); }
                        case "whitelist" -> { return filter(args[2], List.of("add", "remove", "list")); }
                    }
                }
                case "craftblocks" -> {
                    switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "enable" -> { return filter(args[2], craftBlockManager.getDisabledNames()); }
                        case "disable" -> { return filter(args[2], craftBlockManager.getEnabledNames()); }
                        case "group" -> { return filter(args[2], craftBlockManager.getAllGroups()); }
                        case "whitelist" -> { return filter(args[2], List.of("add", "remove", "list")); }
                    }
                }
            }
        }
        if (args.length == 4) {
            String root = args[0].toLowerCase(Locale.ROOT);
            if (root.equals("commands") && args[1].equalsIgnoreCase("group")) {
                String grp = args[2];
                boolean groupExists = commandManager.getAllGroups().contains(grp);
                boolean groupEnabled = groupExists && commandManager.isGroupEnabled(grp);
                if (groupEnabled) {
                    return filter(args[3], List.of("disable"));
                } else {
                    return filter(args[3], List.of("enable"));
                }
            }
            if (root.equals("craftblocks") && args[1].equalsIgnoreCase("group")) {
                String grp = args[2];
                boolean groupExists = craftBlockManager.getAllGroups().contains(grp);
                boolean groupEnabled = groupExists && craftBlockManager.isGroupEnabled(grp);
                if (groupEnabled) {
                    return filter(args[3], List.of("disable"));
                } else {
                    return filter(args[3], List.of("enable"));
                }
            }
            if (args[1].equalsIgnoreCase("whitelist")) {
                String wlSub = args[2].toLowerCase(Locale.ROOT);
                if (wlSub.equals("add") || wlSub.equals("remove") || wlSub.equals("list")) {
                    FileManager mgr = root.equals("commands") ? commandManager : root.equals("craftblocks") ? craftBlockManager : null;
                    if (mgr != null) {
                        return filter(args[3], mgr.getAllNames());
                    }
                }
            }
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("whitelist")
                && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("remove"))) {
            List<String> online = new ArrayList<>();
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                online.add(p.getName());
            }
            return filter(args[4], online);
        }
        return Collections.emptyList();
    }

    private List<String> filter(String input, List<String> candidates) {
        String low = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase(Locale.ROOT).startsWith(low)) {
                out.add(c);
            }
        }
        return out;
    }

    // ---------- SilentSender ----------

    static final class SilentSender implements CommandSender {
        static final SilentSender INSTANCE = new SilentSender();

        @Override public void sendMessage(String message) {}
        @Override public void sendMessage(String... messages) {}
        @Override public void sendMessage(UUID uuid, String message) {}
        @Override public void sendMessage(UUID uuid, String... messages) {}
        @Override public void sendMessage(net.kyori.adventure.text.Component message) {}
        @Override public Spigot spigot() { return Bukkit.getConsoleSender().spigot(); }
        @Override public String getName() { return Bukkit.getConsoleSender().getName(); }
        @Override public net.kyori.adventure.text.Component name() { return net.kyori.adventure.text.Component.text(getName()); }
        @Override public Server getServer() { return Bukkit.getServer(); }
        @Override public boolean isPermissionSet(String name) { return true; }
        @Override public boolean isPermissionSet(Permission perm) { return true; }
        @Override public boolean hasPermission(String name) { return true; }
        @Override public boolean hasPermission(Permission perm) { return true; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return Bukkit.getConsoleSender().addAttachment(plugin, name, value); }
        @Override public PermissionAttachment addAttachment(Plugin plugin) { return Bukkit.getConsoleSender().addAttachment(plugin); }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return Bukkit.getConsoleSender().addAttachment(plugin, name, value, ticks); }
        @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return Bukkit.getConsoleSender().addAttachment(plugin, ticks); }
        @Override public void removeAttachment(PermissionAttachment attachment) { Bukkit.getConsoleSender().removeAttachment(attachment); }
        @Override public void recalculatePermissions() {}
        @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return Bukkit.getConsoleSender().getEffectivePermissions(); }
        @Override public boolean isOp() { return true; }
        @Override public void setOp(boolean value) {}
    }
}
