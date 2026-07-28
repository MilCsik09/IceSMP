package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Native player-report store: players file a short text report on another player
 * (online or not) with {@code /report}, admins triage them with {@code /reports}. Reports persist
 * to {@code reports.yml}.
 *
 * <p>Thread-safety: {@link #reports} and {@link #lastReportAt} are {@link ConcurrentHashMap}s so
 * plain reads (the {@code /reports} listing) are lock-free from any region thread; every compound
 * mutation ({@link #fileReport} / {@link #resolve}) is {@code synchronized} so a concurrent
 * file-then-save or resolve-then-save never races — mirrors {@link DonationChestManager}'s
 * pattern. The rate-limit check in {@link #fileReport} additionally uses
 * {@link ConcurrentHashMap#compute} so the check-then-set of the reporter's last-report timestamp
 * is itself atomic, independent of the outer {@code synchronized} (two different reporters must
 * never block on each other just to check their own cooldown).</p>
 *
 * <p>Admin notification on a successful report is FOLIA-safe: it iterates
 * {@link Bukkit#getOnlinePlayers()} and, for each candidate admin, both the permission check AND
 * the {@code sendMessage} happen inside that player's own region-thread hop
 * ({@code target.getScheduler().run(plugin, task -> {...}, null)}) — mirrors the kill-reward /
 * MarketGUI seller-notification pattern (see {@code CLAUDE.md} Folia rules).</p>
 */
public final class ReportManager implements PersistentStore {

    /** A single player report. {@code resolvedBy} is null while {@code resolved} is false. */
    public record Report(int id, UUID reporterUuid, String reporterName, String targetName, String reason,
                          long createdAtMillis, boolean resolved, String resolvedBy) {
    }

    /** Admin permission node gated on this feature — kept as a literal, not a {@code Permissions} import. */
    private static final String ADMIN_PERMISSION = "icesmp.admin.moderation";

    private static final long RATE_LIMIT_MILLIS = 60_000L;
    private static final long RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000;

    private final JavaPlugin plugin;
    private final hu.taliann.icesmp.utils.MessageManager messageManager;
    private final File storageFile;
    private final Map<Integer, Report> reports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReportAt = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public ReportManager(final JavaPlugin plugin, final hu.taliann.icesmp.utils.MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
        this.storageFile = new File(plugin.getDataFolder(), "reports.yml");
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public void load() {
        reports.clear();
        nextId.set(1);

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = hu.taliann.icesmp.storage.YamlStore.loadTracked(storageFile, plugin.getLogger());
            final ConfigurationSection section = yaml.getConfigurationSection("reports");
            final long now = System.currentTimeMillis();
            int maxId = 0;
            boolean pruned = false;
            if (section != null) {
                for (final String idKey : section.getKeys(false)) {
                    final int id;
                    try {
                        id = Integer.parseInt(idKey);
                    } catch (final NumberFormatException ignored) {
                        continue;
                    }
                    final boolean resolved = section.getBoolean(idKey + ".resolved", false);
                    final long createdAt = section.getLong(idKey + ".created-at", now);

                    // Retention: drop closed reports older than 30 days so the file doesn't grow forever.
                    if (resolved && now - createdAt > RETENTION_MILLIS) {
                        pruned = true;
                        continue;
                    }

                    final UUID reporterUuid;
                    try {
                        reporterUuid = UUID.fromString(section.getString(idKey + ".reporter-id", ""));
                    } catch (final IllegalArgumentException ignored) {
                        continue;
                    }

                    reports.put(id, new Report(
                            id,
                            reporterUuid,
                            section.getString(idKey + ".reporter-name", "?"),
                            section.getString(idKey + ".target-name", "?"),
                            section.getString(idKey + ".reason", ""),
                            createdAt,
                            resolved,
                            section.getString(idKey + ".resolved-by", null)
                    ));
                    maxId = Math.max(maxId, id);
                }
            }
            nextId.set(maxId + 1);
            loadPendingFeedback(yaml.getConfigurationSection("pending-feedback"));
            plugin.getLogger().info("Loaded " + reports.size() + " report(s).");
            if (pruned) {
                // Shrink the on-disk file immediately rather than waiting for the next mutation.
                save();
            }
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load reports.yml: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Report report : reports.values()) {
            final String basePath = "reports." + report.id();
            yaml.set(basePath + ".reporter-id", report.reporterUuid().toString());
            yaml.set(basePath + ".reporter-name", report.reporterName());
            yaml.set(basePath + ".target-name", report.targetName());
            yaml.set(basePath + ".reason", report.reason());
            yaml.set(basePath + ".created-at", report.createdAtMillis());
            yaml.set(basePath + ".resolved", report.resolved());
            if (report.resolvedBy() != null) {
                yaml.set(basePath + ".resolved-by", report.resolvedBy());
            }
        }

        for (final Map.Entry<UUID, java.util.List<String>> entry : pendingFeedbackSnapshot().entrySet()) {
            synchronized (entry.getValue()) {
                yaml.set("pending-feedback." + entry.getKey(), new java.util.ArrayList<>(entry.getValue()));
            }
        }

        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save reports.yml: " + exception.getMessage());
            throw new java.io.UncheckedIOException("Failed to save reports.yml", exception);
        }
    }

    /**
     * Files a new report, rate-limited per reporter (max one every {@value #RATE_LIMIT_MILLIS} ms).
     * On success, every online admin ({@link #ADMIN_PERMISSION}) is notified — each on their own
     * region thread.
     *
     * @param reporter the reporting player
     * @param targetName the reported player's name (need not be online)
     * @param reason the free-text reason (caller is expected to have validated its length)
     * @return null on success, otherwise a message key describing why it failed
     */
    public String fileReport(final Player reporter, final String targetName, final String reason) {
        final UUID reporterId = reporter.getUniqueId();
        final long now = System.currentTimeMillis();

        // Atomic check-then-set: only commit the new timestamp if the cooldown has elapsed,
        // otherwise leave the old timestamp untouched (so the cooldown window doesn't keep sliding).
        final boolean[] limited = {false};
        lastReportAt.compute(reporterId, (id, last) -> {
            if (last != null && now - last < RATE_LIMIT_MILLIS) {
                limited[0] = true;
                return last;
            }
            return now;
        });
        if (limited[0]) {
            return "report-rate-limited";
        }

        final Report report;
        synchronized (this) {
            final int id = nextId.getAndIncrement();
            report = new Report(id, reporterId, reporter.getName(), targetName, reason, now, false, null);
            reports.put(id, report);
            save();
        }

        notifyAdmins(report);
        return null;
    }

    private void notifyAdmins(final Report report) {
        final String message = messageManager.get("report-admin-notify",
                "&c[Bejelentés] &f%s &7bejelentette: &f%s &7(&8#%d&7) — &f%s",
                report.reporterName(), report.targetName(), report.id(), report.reason());

        for (final Player admin : Bukkit.getOnlinePlayers()) {
            // Folia: another entity's permission check AND message send both happen on ITS OWN
            // region thread, never on the caller's — mirrors the kill-reward / MarketGUI pattern.
            admin.getScheduler().run(plugin, task -> {
                if (admin.hasPermission(ADMIN_PERMISSION)) {
                    admin.sendMessage(message);
                }
            }, null);
        }
    }

    /** Every unresolved report, oldest first — snapshot read, safe from any region thread. */
    public List<Report> openReports() {
        return reports.values().stream()
                .filter(report -> !report.resolved())
                .sorted(Comparator.comparingInt(Report::id))
                .toList();
    }

    /** Every report (open and resolved), newest first — for the {@code /reports all} listing. */
    public List<Report> allReportsSorted() {
        return reports.values().stream()
                .sorted(Comparator.comparingInt(Report::id).reversed())
                .toList();
    }

    /**
     * Marks a report resolved.
     *
     * @param id the report id
     * @param adminName the resolving admin's name
     * @return true if an open report with that id existed and was resolved; false if it doesn't
     *         exist or was already resolved
     */
    public synchronized boolean resolve(final int id, final String adminName) {
        final Report existing = reports.get(id);
        if (existing == null || existing.resolved()) {
            return false;
        }
        reports.put(id, new Report(existing.id(), existing.reporterUuid(), existing.reporterName(),
                existing.targetName(), existing.reason(), existing.createdAtMillis(), true, adminName));
        // A bejelentő visszajelzést kap — online: azonnal (saját régió-szálán);
        // offline: eltárolt üzenet, a következő belépéskor kézbesíti a ReportFeedbackListener.
        final String feedback = messageManager.get("report-feedback-resolved",
                "&a✔ A bejelentésedet (&f%s&a) egy moderátor lezárta. Köszönjük!", existing.targetName());
        final Player reporter = plugin.getServer().getPlayer(existing.reporterUuid());
        if (reporter != null) {
            reporter.getScheduler().run(plugin, task -> reporter.sendMessage(feedback), null);
        } else {
            pendingFeedback.computeIfAbsent(existing.reporterUuid(),
                    key -> java.util.Collections.synchronizedList(new java.util.ArrayList<>())).add(feedback);
        }
        save();
        return true;
    }

    /** Kézbesítetlen visszajelzések (offline bejelentőknek) — a reports.yml-be perzisztálva. */
    private final Map<UUID, java.util.List<String>> pendingFeedback = new ConcurrentHashMap<>();

    /**
     * A belépő játékos függő visszajelzéseinek kézbesítése (a join a játékos saját
     * régió-szálán fut, a közvetlen sendMessage legális). Kézbesítés után törlés + mentés.
     */
    public void deliverPendingFeedback(final Player player) {
        final java.util.List<String> pending = pendingFeedback.remove(player.getUniqueId());
        if (pending == null || pending.isEmpty()) {
            return;
        }
        synchronized (pending) {
            pending.forEach(player::sendMessage);
        }
        save();
    }

    /** A pending-feedback szekció betöltése (a load() hívja). */
    void loadPendingFeedback(final org.bukkit.configuration.ConfigurationSection section) {
        pendingFeedback.clear();
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            try {
                final java.util.List<String> messages = section.getStringList(key);
                if (!messages.isEmpty()) {
                    pendingFeedback.put(UUID.fromString(key),
                            java.util.Collections.synchronizedList(new java.util.ArrayList<>(messages)));
                }
            } catch (final IllegalArgumentException ignored) {
            }
        }
    }

    /** A pending-feedback szekció kiírása (a save() hívja). */
    Map<UUID, java.util.List<String>> pendingFeedbackSnapshot() {
        return pendingFeedback;
    }

    /** Count of unresolved reports — cheap snapshot, safe from any region thread. */
    public int openCount() {
        return (int) reports.values().stream().filter(report -> !report.resolved()).count();
    }
}
