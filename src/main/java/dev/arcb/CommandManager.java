package dev.arcb;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CommandManager implements FileManager {

    private final ARCBPlugin plugin;
    private final File commandsFolder;

    private final Map<String, CommandEntry> entries = new LinkedHashMap<>();
    private final Map<String, BukkitTask> activeTasks = new HashMap<>();

    CommandManager(ARCBPlugin plugin) {
        this.plugin = plugin;
        this.commandsFolder = new File(plugin.getDataFolder(), "commands");
    }

    @Override
    public void loadAll() {
        stopAll();
        entries.clear();
        if (!commandsFolder.isDirectory()) {
            commandsFolder.mkdirs();
            return;
        }
        scan(commandsFolder, "", false);
        if (!plugin.isPluginEnabled()) {
            return;
        }
        for (CommandEntry e : entries.values()) {
            if (!e.enabled) continue;
            long delay = e.intervalTicks;
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> execute(e), delay, e.intervalTicks);
            activeTasks.put(e.name, task);
        }
    }

    @Override
    public void stopAll() {
        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }

    private void scan(File dir, String prefix, boolean parentDisabled) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String raw = f.getName();
            boolean compDisabled = raw.startsWith("-");
            String clean = compDisabled ? raw.substring(1) : raw;
            boolean disabled = parentDisabled || compDisabled;
            String relPath = prefix.isEmpty() ? clean : prefix + "/" + clean;

            if (f.isDirectory()) {
                scan(f, relPath, disabled);
            } else if (raw.endsWith(".yml")) {
                String noExt = clean.endsWith(".yml") ? clean.substring(0, clean.length() - 4) : clean;
                String normName = prefix.isEmpty() ? noExt : prefix + "/" + noExt;
                EntryState state = new EntryState(normName, relPath + ".yml", f, !disabled);
                loadAndStore(state);
            }
        }
    }

    private void loadAndStore(EntryState state) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(state.file);
        String cmd = yml.getString("command", "").trim();
        if (cmd.isEmpty()) {
            plugin.getLogger().warning("Skipping command file '" + state.path + "': no 'command' field.");
            return;
        }
        long interval = Math.max(1L, yml.getLong("interval-ticks", 20L));
        List<String> wl = yml.getStringList("whitelist");
        Set<String> whitelist = new HashSet<>();
        for (String s : wl) whitelist.add(s.toLowerCase(Locale.ROOT));
        entries.put(state.name, new CommandEntry(state.name, cmd, interval, state.enabled, whitelist));
    }

    private void execute(CommandEntry e) {
        if (!plugin.isPluginEnabled() || !e.enabled) return;
        if (e.command.contains("*")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (e.whitelist.contains(p.getName().toLowerCase(Locale.ROOT))) continue;
                dispatch(e.command.replace("*", p.getName()));
            }
            return;
        }
        dispatch(e.command);
    }

    private void dispatch(String cmd) {
        String norm = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        if (norm.startsWith("clear ")) {
            clearDirect(norm);
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), norm);
    }

    private void clearDirect(String cmd) {
        String[] parts = cmd.split(" ");
        if (parts.length < 2) return;
        Player target = Bukkit.getPlayerExact(parts[1]);
        if (target == null) return;
        if (parts.length >= 3) {
            Material mat = Material.matchMaterial(parts[2].toUpperCase(Locale.ROOT));
            if (mat != null) {
                target.getInventory().remove(mat);
            }
        } else {
            target.getInventory().clear();
        }
    }

    // ---- FileManager interface ----

    @Override public int enableFile(String name) { return toggleFile(name, false); }
    @Override public int disableFile(String name) { return toggleFile(name, true); }

    private int toggleFile(String name, boolean addDash) {
        EntryState state = resolveFile(name);
        if (state == null) return -1;
        boolean already = state.enabled == !addDash;
        if (already) return 0;
        File target = parentWithDash(state.file, addDash);
        if (state.file.renameTo(target)) {
            return 1;
        }
        plugin.getLogger().warning("Failed to rename " + state.file + " -> " + target);
        return -1;
    }

    @Override public int enableGroup(String name) { return toggleGroup(name, false); }
    @Override public int disableGroup(String name) { return toggleGroup(name, true); }

    private int toggleGroup(String name, boolean addDash) {
        GroupState gs = resolveGroup(name);
        if (gs == null) return -1;
        boolean already = gs.enabled == !addDash;
        if (already) return 0;
        File target = parentWithDash(gs.folder, addDash);
        if (gs.folder.renameTo(target)) {
            return 1;
        }
        plugin.getLogger().warning("Failed to rename group " + gs.folder + " -> " + target);
        return -1;
    }

    private File parentWithDash(File f, boolean addDash) {
        String parent = f.getParent();
        String raw = f.getName();
        String name = raw.startsWith("-") ? raw.substring(1) : raw;
        String newName = addDash ? "-" + name : name;
        return parent == null ? new File(newName) : new File(parent, newName);
    }

    private EntryState resolveFile(String name) {
        File f = lookupFile(commandsFolder, name);
        if (f == null) return null;
        boolean hasDash = f.getName().startsWith("-");
        boolean parentDisabled = hasDisabledParent(f, commandsFolder);
        return new EntryState(name, null, f, !hasDash && !parentDisabled);
    }

    private GroupState resolveGroup(String name) {
        File folder = lookupFolder(commandsFolder, name);
        if (folder == null) return null;
        boolean hasDash = folder.getName().startsWith("-");
        return new GroupState(folder, !hasDash);
    }

    private File lookupFile(File base, String name) {
        String[] parts = name.split("/");
        File dir = base;
        for (int i = 0; i < parts.length - 1; i++) {
            File next = findComponent(dir, parts[i]);
            if (next == null || !next.isDirectory()) return null;
            dir = next;
        }
        String fileName = parts[parts.length - 1];
        return findYmlFile(dir, fileName);
    }

    private File lookupFolder(File base, String name) {
        String[] parts = name.split("/");
        File dir = base;
        for (String p : parts) {
            File next = findComponent(dir, p);
            if (next == null || !next.isDirectory()) return null;
            dir = next;
        }
        return dir;
    }

    private File findComponent(File dir, String cleanName) {
        File[] list = dir.listFiles();
        if (list == null) return null;
        for (File f : list) {
            String raw = f.getName();
            String base = raw.startsWith("-") ? raw.substring(1) : raw;
            if (base.equals(cleanName)) return f;
        }
        return null;
    }

    private File findYmlFile(File dir, String cleanName) {
        File[] list = dir.listFiles();
        if (list == null) return null;
        for (File f : list) {
            if (!f.getName().endsWith(".yml")) continue;
            String raw = f.getName();
            String base = raw.startsWith("-") ? raw.substring(1) : raw;
            String noExt = base.endsWith(".yml") ? base.substring(0, base.length() - 4) : base;
            if (noExt.equals(cleanName)) return f;
        }
        return null;
    }

    private boolean hasDisabledParent(File f, File base) {
        File p = f.getParentFile();
        while (p != null && !p.equals(base)) {
            if (p.getName().startsWith("-")) return true;
            p = p.getParentFile();
        }
        return false;
    }

    @Override
    public boolean isEnabled(String name) {
        CommandEntry e = entries.get(name);
        if (e != null) return e.enabled;
        EntryState s = resolveFile(name);
        return s != null && s.enabled;
    }

    @Override
    public boolean isGroupEnabled(String name) {
        GroupState gs = resolveGroup(name);
        return gs != null && gs.enabled;
    }

    @Override
    public List<String> getAllNames() {
        return new ArrayList<>(entries.keySet());
    }

    @Override
    public List<String> getEnabledNames() {
        List<String> out = new ArrayList<>();
        for (CommandEntry e : entries.values()) if (e.enabled) out.add(e.name);
        return out;
    }

    @Override
    public List<String> getDisabledNames() {
        List<String> out = new ArrayList<>();
        for (CommandEntry e : entries.values()) if (!e.enabled) out.add(e.name);
        return out;
    }

    @Override
    public List<String> getAllGroups() {
        return collectGroups(commandsFolder, "");
    }

    private List<String> collectGroups(File dir, String prefix) {
        List<String> groups = new ArrayList<>();
        File[] list = dir.listFiles();
        if (list == null) return groups;
        for (File f : list) {
            if (!f.isDirectory()) continue;
            String raw = f.getName();
            String clean = raw.startsWith("-") ? raw.substring(1) : raw;
            String rel = prefix.isEmpty() ? clean : prefix + "/" + clean;
            groups.add(rel);
            groups.addAll(collectGroups(f, rel));
        }
        return groups;
    }

    @Override
    public int getActiveCount() {
        return activeTasks.size();
    }

    // ---- whitelist management ----

    @Override
    public int addWhitelist(String name, String player) {
        File f = lookupFile(commandsFolder, name);
        if (f == null) return -1;
        String lower = player.toLowerCase(Locale.ROOT);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        List<String> wl = yml.getStringList("whitelist");
        for (String s : wl) {
            if (s.equalsIgnoreCase(lower)) return 0;
        }
        wl.add(player);
        yml.set("whitelist", wl);
        try {
            yml.save(f);
            plugin.reloadAll();
            return 1;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save whitelist for '" + name + "': " + e.getMessage());
            return -1;
        }
    }

    @Override
    public int removeWhitelist(String name, String player) {
        File f = lookupFile(commandsFolder, name);
        if (f == null) return -1;
        String lower = player.toLowerCase(Locale.ROOT);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        List<String> wl = yml.getStringList("whitelist");
        boolean removed = wl.removeIf(s -> s.equalsIgnoreCase(lower));
        if (!removed) return 0;
        yml.set("whitelist", wl);
        try {
            yml.save(f);
            plugin.reloadAll();
            return 1;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save whitelist for '" + name + "': " + e.getMessage());
            return -1;
        }
    }

    @Override
    public List<String> getWhitelist(String name) {
        CommandEntry e = entries.get(name);
        if (e != null) return new ArrayList<>(e.whitelist);
        if (lookupFile(commandsFolder, name) != null) return List.of();
        return null;
    }

    // ---- internal records ----

    private record CommandEntry(String name, String command, long intervalTicks, boolean enabled, Set<String> whitelist) {}
    private record EntryState(String name, String path, File file, boolean enabled) {}
    private record GroupState(File folder, boolean enabled) {}
}
