package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-wide community goals with durable one-shot contribution receipts. */
public final class CommunityGoalManager implements PersistentStore {

    private record Completion(ConfigurationSection goal, boolean serverWide,
                              FactionType goalFaction, int count) {
    }

    private record AppliedContribution(boolean changed, List<Completion> completions) {
    }

    /**
     * Tartós kifizetés-outbox bejegyzés. A számláló csökkentése és a forrás-nyugta MÁR tartós,
     * mielőtt a jutalom kifizetődne — enélkül egy crash a kifizetés közben úgy hagyná a világot,
     * hogy a nyugta miatt az esemény sosem játszódik újra, a kincstár/szezon/buff jutalom viszont
     * elmarad. A bejegyzés a kifizetés UTÁN, külön tartós írással tűnik el.
     */
    private record PendingCompletion(UUID completionId, String goalId, String displayName,
                                     boolean serverWide, FactionType faction,
                                     double treasuryReward, int seasonPoints, int buffMinutes) {

        /** Cél-store-onként stabil, egyszeri művelet-azonosító. */
        String grantId(final String target, final FactionType applyTo) {
            return "community:" + completionId + ':' + target + ':' + applyTo.name();
        }
    }

    private static final int MAX_COMPLETIONS_PER_CONTRIBUTION = 3;
    private static final long RECEIPT_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(7L);
    private static final int MAX_RECEIPTS = 50_000;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final FactionTreasuryManager treasuryManager;
    private final MessageManager messageManager;
    private final SeasonManager seasonManager;
    private final File storageFile;
    private final Map<String, Long> progress = new ConcurrentHashMap<>();
    /** Source-event UUID -> durable claim time. */
    private final Map<String, Long> contributionReceipts = new ConcurrentHashMap<>();
    /** Még ki nem fizetett, de már tartósan könyvelt teljesítések (a manager monitora védi). */
    private final List<PendingCompletion> pendingCompletions = new ArrayList<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    public CommunityGoalManager(final JavaPlugin plugin, final ConfigManager configManager,
                                final FactionManager factionManager,
                                final FactionTreasuryManager treasuryManager,
                                final MessageManager messageManager,
                                final SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.treasuryManager = treasuryManager;
        this.messageManager = messageManager;
        this.seasonManager = seasonManager;
        this.storageFile = new File(plugin.getDataFolder(), "community-goals.yml");
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public synchronized void load() {
        progress.clear();
        contributionReceipts.clear();
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        final ConfigurationSection section = yaml.getConfigurationSection("progress");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                final Object raw = section.get(key);
                if (!(raw instanceof Number number) || number.longValue() < 0L) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen community progress: " + key);
                    // A failCorrupt mindig dob, de void: a fordító a minta-kötést csak akkor
                    // látja biztosan hozzárendeltnek, ha ez az ág megszakad (a CurrencyManager
                    // ugyanígy return-öl minden failCorrupt után).
                    continue;
                }
                progress.put(key.toLowerCase(Locale.ROOT), number.longValue());
            }
        }
        final ConfigurationSection receipts = yaml.getConfigurationSection("contribution-receipts");
        if (receipts != null) {
            for (final String key : receipts.getKeys(false)) {
                try {
                    UUID.fromString(key);
                } catch (final IllegalArgumentException invalid) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen contribution receipt UUID: " + key);
                }
                final long claimedAt = receipts.getLong(key, 0L);
                if (claimedAt <= 0L) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen contribution receipt idő: " + key);
                }
                contributionReceipts.put(key, claimedAt);
            }
        }
        pruneReceipts(System.currentTimeMillis());

        pendingCompletions.clear();
        final ConfigurationSection outbox = yaml.getConfigurationSection("pending-completions");
        if (outbox != null) {
            for (final String key : outbox.getKeys(false)) {
                final String rawId = outbox.getString(key + ".completion-id");
                final String goalId = outbox.getString(key + ".goal-id");
                if (rawId == null || goalId == null || goalId.isBlank()) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen függő közösségi kifizetés: " + key);
                    return;
                }
                final UUID completionId;
                try {
                    completionId = UUID.fromString(rawId);
                } catch (final IllegalArgumentException invalid) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Értelmezhetetlen kifizetés-azonosító: " + key);
                    return;
                }
                final String factionName = outbox.getString(key + ".faction");
                final FactionType faction = factionName == null
                        ? null : FactionType.fromInput(factionName);
                final boolean serverWide = outbox.getBoolean(key + ".server-wide", false);
                if (!serverWide && faction == null) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Frakció-célzott kifizetés frakció nélkül: " + key);
                    return;
                }
                pendingCompletions.add(new PendingCompletion(completionId, goalId,
                        outbox.getString(key + ".display-name", "Közösségi cél"),
                        serverWide, faction,
                        Math.max(0.0D, outbox.getDouble(key + ".treasury-reward", 0.0D)),
                        Math.max(0, outbox.getInt(key + ".season-points", 0)),
                        Math.max(0, outbox.getInt(key + ".buff-minutes", 0))));
            }
        }
        if (!pendingCompletions.isEmpty()) {
            plugin.getLogger().warning("Közösségi cél: " + pendingCompletions.size()
                    + " függő kifizetés a legutóbbi leállásból — újrajátszás.");
            flushPendingCompletions();
        }
    }

    @Override
    public synchronized void save() {
        saveStrict();
    }

    /** Writes receipts and counters in the same atomic file image. */
    private boolean saveStrict() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<String, Long> entry : progress.entrySet()) {
            yaml.set("progress." + entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<String, Long> entry : contributionReceipts.entrySet()) {
            yaml.set("contribution-receipts." + entry.getKey(), entry.getValue());
        }
        for (int index = 0; index < pendingCompletions.size(); index++) {
            final PendingCompletion pending = pendingCompletions.get(index);
            final String base = "pending-completions." + index;
            yaml.set(base + ".completion-id", pending.completionId().toString());
            yaml.set(base + ".goal-id", pending.goalId());
            yaml.set(base + ".display-name", pending.displayName());
            yaml.set(base + ".server-wide", pending.serverWide());
            yaml.set(base + ".faction", pending.faction() == null ? null : pending.faction().name());
            yaml.set(base + ".treasury-reward", pending.treasuryReward());
            yaml.set(base + ".season-points", pending.seasonPoints());
            yaml.set(base + ".buff-minutes", pending.buffMinutes());
        }
        try {
            YamlStore.saveAtomic(storageFile, yaml);
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save community-goals.yml: "
                    + exception.getMessage());
            return false;
        }
    }

    private void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                save();
            }, 2L, TimeUnit.SECONDS);
        }
    }

    private ConfigurationSection goalsSection() {
        return configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("community-goals");
    }

    public synchronized void resetForNewSeason() {
        final Map<String, Long> oldProgress = new HashMap<>(progress);
        final Map<String, Long> oldReceipts = new HashMap<>(contributionReceipts);
        final List<PendingCompletion> oldPending = new ArrayList<>(pendingCompletions);
        progress.clear();
        contributionReceipts.clear();
        pendingCompletions.clear();
        if (!saveStrict()) {
            progress.putAll(oldProgress);
            contributionReceipts.putAll(oldReceipts);
            pendingCompletions.addAll(oldPending);
        }
    }

    public long getProgress(final String goalId) {
        return progress.getOrDefault(goalId.toLowerCase(Locale.ROOT), 0L);
    }

    /** Ordinary non-economic event contribution; completion is durable before rewards run. */
    public synchronized void contribute(final Player player, final String objectiveType,
                                        final String materialOrEntity, final int amount) {
        final Map<String, Long> before = new HashMap<>(progress);
        final AppliedContribution applied = applyContribution(
                player, objectiveType, materialOrEntity, amount);
        if (!applied.changed()) {
            return;
        }
        if (applied.completions().isEmpty()) {
            requestSave();
            return;
        }
        // A visszagörgetés INDEX szerint vágja le a most hozzáadottakat: azonos cél-id alapján
        // törölve egy korábbi crashből ottmaradt függő kifizetést is elvinnénk.
        final int outboxMark = pendingCompletions.size();
        enqueueCompletions(applied.completions());
        if (!saveStrict()) {
            progress.clear();
            progress.putAll(before);
            pendingCompletions.subList(outboxMark, pendingCompletions.size()).clear();
            return;
        }
        flushPendingCompletions();
    }

    /**
     * Claims one source event and applies every matching community counter in the same durable file
     * image. Returning false means the event was already claimed or persistence failed; callers must
     * not advance personal progress either.
     */
    public synchronized boolean contributeOnce(final Player player, final String objectiveType,
                                                final String materialOrEntity, final int amount,
                                                final UUID contributionId) {
        if (player == null || contributionId == null || amount <= 0) {
            return false;
        }
        final long now = System.currentTimeMillis();
        pruneReceipts(now);
        final String receipt = contributionId.toString();
        if (contributionReceipts.containsKey(receipt)) {
            return false;
        }

        final Map<String, Long> before = new HashMap<>(progress);
        contributionReceipts.put(receipt, now);
        final AppliedContribution applied = applyContribution(
                player, objectiveType, materialOrEntity, amount);
        final int outboxMark = pendingCompletions.size();
        enqueueCompletions(applied.completions());
        if (!saveStrict()) {
            progress.clear();
            progress.putAll(before);
            contributionReceipts.remove(receipt);
            pendingCompletions.subList(outboxMark, pendingCompletions.size()).clear();
            return false;
        }
        flushPendingCompletions();
        return true;
    }

    private AppliedContribution applyContribution(final Player player, final String objectiveType,
                                                  final String materialOrEntity, final int rawAmount) {
        final ConfigurationSection goals = goalsSection();
        if (goals == null || !configManager.getBoolean("community-goals.enabled", true)) {
            return new AppliedContribution(false, List.of());
        }
        final int amount = Math.max(1, rawAmount);
        final FactionType playerFaction = factionManager.getFaction(player.getUniqueId());
        final List<Completion> completions = new ArrayList<>();
        boolean changed = false;

        for (final String goalIdRaw : goals.getKeys(false)) {
            if ("enabled".equalsIgnoreCase(goalIdRaw)
                    || "season-points".equalsIgnoreCase(goalIdRaw)) {
                continue;
            }
            final ConfigurationSection goal = goals.getConfigurationSection(goalIdRaw);
            if (goal == null || !objectiveType.equalsIgnoreCase(
                    goal.getString("objective.type", ""))) {
                continue;
            }
            final String goalFactionName = goal.getString("faction", "ALL");
            final boolean serverWide = goalFactionName.isBlank()
                    || "ALL".equalsIgnoreCase(goalFactionName);
            final FactionType goalFaction = serverWide
                    ? null : FactionType.fromInput(goalFactionName);
            if (!serverWide && (goalFaction == null || playerFaction != goalFaction)) {
                continue;
            }
            final var materials = goal.getStringList("objective.materials");
            final String entityFilter = goal.getString("objective.entity-type", "");
            if (!materials.isEmpty()) {
                if (materialOrEntity == null || materials.stream().noneMatch(
                        name -> name.equalsIgnoreCase(materialOrEntity))) {
                    continue;
                }
            } else if (!entityFilter.isBlank() && (materialOrEntity == null
                    || !entityFilter.equalsIgnoreCase(materialOrEntity))) {
                continue;
            }

            changed = true;
            final String goalId = goalIdRaw.toLowerCase(Locale.ROOT);
            final long target = Math.max(1L, goal.getLong("objective.count", 1L));
            final long old = getProgress(goalId);
            long remaining = old > Long.MAX_VALUE - amount
                    ? Long.MAX_VALUE : old + amount;
            int completed = 0;
            while (remaining >= target && completed < MAX_COMPLETIONS_PER_CONTRIBUTION) {
                remaining -= target;
                completed++;
            }
            progress.put(goalId, remaining);
            if (completed > 0) {
                completions.add(new Completion(goal, serverWide, goalFaction, completed));
            }
        }
        return new AppliedContribution(changed, List.copyOf(completions));
    }

    /**
     * A teljesítéseket az OUTBOXBA teszi (a hívó ezután írja ki tartósan a közös fájl-képet),
     * majd kifizeti és a bejegyzést külön írással eltünteti. Crash a kifizetés közben = a
     * bejegyzés a lemezen marad, és a következő indulás {@link #flushPendingCompletions()}-ja
     * újrajátssza — a jutalom nem veszhet el.
     */
    private void enqueueCompletions(final List<Completion> completions) {
        for (final Completion completion : completions) {
            final ConfigurationSection goal = completion.goal();
            // A jutalom PILLANATKÉPE kerül a naplóba, nem csak a cél azonosítója: egy közben
            // átírt vagy törölt config-bejegyzés különben eltérő — vagy elveszett — újrajátszást
            // adna. Egy teljesítés = egy bejegyzés, ezért a count-ot itt bontjuk szét.
            for (int index = 0; index < completion.count(); index++) {
                pendingCompletions.add(new PendingCompletion(UUID.randomUUID(),
                        goal.getName(),
                        goal.getString("display-name", "Közösségi cél"),
                        completion.serverWide(), completion.goalFaction(),
                        Math.max(0.0D, goal.getDouble("reward-treasury", 0.0D)),
                        Math.max(0, configManager.getInt("community-goals.season-points", 8)),
                        Math.max(0, goal.getInt("reward-buff-minutes", 0))));
            }
        }
    }

    /**
     * Kifizeti és tartósan eltünteti a függő teljesítéseket. Minden gazdasági hatás a cél-store
     * SAJÁT idempotens útján megy (a nyugta az egyenleggel/pontokkal egyetlen atomi fájl-képbe
     * kerül), ezért a bejegyzés CSAK akkor törölhető, ha minden cél visszaigazolta magát —
     * enélkül a nyugtázás és a cél-store tényleges mentése közti crash elveszítette vagy
     * (nem idempotens újrajátszással) duplázta a jutalmat.
     */
    private void flushPendingCompletions() {
        if (pendingCompletions.isEmpty()) {
            return;
        }
        final List<PendingCompletion> settled = new ArrayList<>();
        for (final PendingCompletion pending : new ArrayList<>(pendingCompletions)) {
            if (applyCompletion(pending)) {
                settled.add(pending);
            }
        }
        if (settled.isEmpty()) {
            return;
        }
        pendingCompletions.removeAll(settled);
        if (!saveStrict()) {
            // A hatások idempotensek (grant-id), ezért a visszakerülő bejegyzés újrajátszása
            // NEM duplázza a jutalmat — csak a naplót tisztítja majd le a következő indulás.
            pendingCompletions.addAll(settled);
            plugin.getLogger().severe("A közösségi kifizetés nyugtázása nem sikerült — a függő "
                    + "bejegyzések maradnak; az újrajátszás idempotens, nem fizet kétszer.");
        }
    }

    /**
     * Egy teljesítés kifizetése. A hirdetés és a buff best-effort (ismételve is ártalmatlan),
     * a kincstár és a szezon-pont viszont grant-azonosítóhoz kötött.
     *
     * @return true, ha MINDEN tartós hatás visszaigazolt
     */
    private boolean applyCompletion(final PendingCompletion pending) {
        final List<FactionType> targets = pending.serverWide()
                ? List.of(FactionType.values()) : List.of(pending.faction());
        boolean allAcked = true;
        if (pending.treasuryReward() > 0.0D) {
            for (final FactionType faction : targets) {
                if (!treasuryManager.depositOnce(pending.grantId("treasury", faction),
                        faction, pending.treasuryReward())) {
                    allAcked = false;
                }
            }
        }
        if (pending.seasonPoints() > 0) {
            final String source = pending.serverWide() ? "community-server" : "community";
            for (final FactionType faction : targets) {
                if (!seasonManager.addPointsOnce(pending.grantId("season", faction),
                        faction, pending.seasonPoints(), source)) {
                    allAcked = false;
                }
            }
        }
        if (!allAcked) {
            plugin.getLogger().severe("Közösségi kifizetés RÉSZBEN sikerült (" + pending.goalId()
                    + ", " + pending.completionId() + ") — a bejegyzés marad, az újrajátszás "
                    + "a hiányzó felet pótolja (a már megtörtént rész nem ismétlődik).");
            return false;
        }
        announceCompletion(pending);
        applyBuff(pending);
        return true;
    }

    private void pruneReceipts(final long now) {
        final long cutoff = now - RECEIPT_RETENTION_MILLIS;
        contributionReceipts.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        if (contributionReceipts.size() <= MAX_RECEIPTS) {
            return;
        }
        final int remove = contributionReceipts.size() - MAX_RECEIPTS;
        contributionReceipts.entrySet().stream()
                .sorted(Comparator.comparingLong(Map.Entry::getValue))
                .limit(remove)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(contributionReceipts::remove);
    }

    /** Hirdetés a pillanatképből (ismételve is ártalmatlan, nem gazdasági hatás). */
    private void announceCompletion(final PendingCompletion pending) {
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "community-goal-completed",
                "<gold>🏛 Közösségi cél teljesítve: <white>{goal}</white>! {who}</gold>",
                Map.of(
                        "goal", pending.displayName(),
                        "who", pending.serverWide() ? "Az egész szerver összefogott!"
                                : "A(z) " + pending.faction().getDisplayName() + " frakció diadala!"
                )
        ));
    }

    /** Buff a pillanatképből: átmeneti hatás, ezért nem kap grant-nyugtát. */
    private void applyBuff(final PendingCompletion pending) {
        if (pending.buffMinutes() <= 0) {
            return;
        }
        final int durationTicks = pending.buffMinutes() * 60 * 20;
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (!pending.serverWide() && factionManager.getFaction(
                    online.getUniqueId()) != pending.faction()) {
                continue;
            }
            online.getScheduler().run(plugin, task -> {
                online.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, durationTicks, 0, false, true, true));
                online.addPotionEffect(new PotionEffect(
                        PotionEffectType.HERO_OF_THE_VILLAGE,
                        durationTicks, 0, false, true, true));
            }, null);
        }
    }
}
