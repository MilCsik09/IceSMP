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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

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
     * hogy a nyugta miatt az esemény sosem játszódik újra, a kincstár/szezon jutalom viszont
     * elmarad. A bejegyzés a kifizetés UTÁN, külön tartós írással tűnik el.
     *
     * <p>A szezonpont nem nyers config-értékként, hanem a teljesítés pillanatában kiszámított,
     * frakciónkénti végleges delta formájában kerül a pillanatképbe. A replay ezért nem függ a
     * későbbi season-enabled/source-weight/finale/top2 állapottól.</p>
     */
    private record PendingCompletion(UUID completionId, String goalId, String displayName,
                                     boolean serverWide, FactionType faction,
                                     double treasuryReward,
                                     Map<FactionType, Integer> seasonDeltas,
                                     int buffMinutes) {

        private PendingCompletion {
            seasonDeltas = Map.copyOf(seasonDeltas);
        }

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
    /** Season generation owning the current progress/receipt maps. */
    private int progressSeasonNumber = 1;
    /** Sticky local gate after a post-season-commit reset write failed. */
    private boolean seasonTransitionBlocked;

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
        YamlStore.registerCriticalWrite(storageFile);
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public synchronized void load() {
        progress.clear();
        contributionReceipts.clear();
        seasonTransitionBlocked = false;
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        final Integer storedSeasonNumber = readStoredSeasonNumber(yaml);
        final ConfigurationSection section = yaml.getConfigurationSection("progress");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                final Object raw = section.get(key);
                if (!(raw instanceof Number number) || number.longValue() < 0L) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen community progress: " + key);
                    // A failCorrupt mindig dob, de void: a fordító a minta-kötést csak akkor
                    // látja biztosan hozzárendeltnek, ha ez az ág megszakad.
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
        final Set<UUID> seenCompletionIds = new HashSet<>();
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
                if (!seenCompletionIds.add(completionId)) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Duplikált közösségi completion-id az outboxban: " + completionId);
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

                final Object rawTreasury = outbox.get(key + ".treasury-reward");
                final double treasuryReward = rawTreasury == null ? 0.0D
                        : rawTreasury instanceof Number number ? number.doubleValue() : Double.NaN;
                if (!Double.isFinite(treasuryReward) || treasuryReward < 0.0D) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen treasury jutalom a függő kifizetésben: " + key);
                    return;
                }

                final Object rawBuff = outbox.get(key + ".buff-minutes");
                final double buffValue = rawBuff == null ? 0.0D
                        : rawBuff instanceof Number number ? number.doubleValue() : Double.NaN;
                if (!Double.isFinite(buffValue) || buffValue < 0.0D
                        || buffValue > Integer.MAX_VALUE || buffValue != Math.rint(buffValue)) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Érvénytelen buff-idő a függő kifizetésben: " + key);
                    return;
                }
                final int buffMinutes = (int) buffValue;

                final Map<FactionType, Integer> seasonDeltas = readSeasonDeltas(
                        outbox, key, serverWide, faction);
                pendingCompletions.add(new PendingCompletion(completionId, goalId,
                        outbox.getString(key + ".display-name", "Közösségi cél"),
                        serverWide, faction, treasuryReward, seasonDeltas, buffMinutes));
            }
        }
        final int activeSeason = seasonManager.getSeasonNumber();
        final CommunitySeasonState.LoadAction loadAction;
        try {
            loadAction = CommunitySeasonState.reconcileOnLoad(
                    storedSeasonNumber, activeSeason, !pendingCompletions.isEmpty());
        } catch (final IllegalArgumentException | IllegalStateException invalid) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(), invalid.getMessage());
            return;
        }

        progressSeasonNumber = activeSeason;
        if (loadAction == CommunitySeasonState.LoadAction.RESET_TO_CURRENT) {
            // season.yml was already committed, but the process stopped before community-goals.yml
            // could acknowledge the reset. Replaying this clear is idempotent and deliberately
            // happens before any old outbox can be applied to the newly opened season.
            progress.clear();
            contributionReceipts.clear();
            persistSeasonAlignmentOrThrow("crash recovery after season commit");
        } else if (loadAction == CommunitySeasonState.LoadAction.INITIALISE_CURRENT) {
            // Migration from the pre-marker format preserves all progress and only adds the owner
            // generation. It must itself be durable before gameplay resumes.
            persistSeasonAlignmentOrThrow("legacy community season marker migration");
        }

        if (!pendingCompletions.isEmpty()) {
            plugin.getLogger().warning("Közösségi cél: " + pendingCompletions.size()
                    + " függő kifizetés a legutóbbi leállásból — újrajátszás.");
            flushPendingCompletions();
        }
    }

    private Integer readStoredSeasonNumber(final YamlConfiguration yaml) {
        if (!yaml.contains("season.number")) {
            return null;
        }
        final Object raw = yaml.get("season.number");
        if (!(raw instanceof Number number)) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "A community season marker nem szám");
            return null;
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 1.0D || value > Integer.MAX_VALUE
                || value != Math.rint(value)) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Érvénytelen community season marker");
            return null;
        }
        return (int) value;
    }

    private Map<FactionType, Integer> readSeasonDeltas(final ConfigurationSection outbox,
                                                       final String key,
                                                       final boolean serverWide,
                                                       final FactionType targetFaction) {
        final EnumMap<FactionType, Integer> deltas = new EnumMap<>(FactionType.class);
        final ConfigurationSection section = outbox.getConfigurationSection(key + ".season-deltas");
        if (section == null) {
            // A régi nyers season-points rekordot nem szabad live configgal újraszámolni: az
            // pontosan azt a replay-driftet hozná vissza, amelyet az immutable snapshot lezár.
            if (outbox.getInt(key + ".season-points", 0) > 0) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Legacy, nem immutable season-points outbox-bejegyzés: " + key);
            }
            return Map.of();
        }

        for (final String factionKey : section.getKeys(false)) {
            final FactionType faction = FactionType.fromInput(factionKey);
            final Object raw = section.get(factionKey);
            final double value = raw instanceof Number number
                    ? number.doubleValue() : Double.NaN;
            if (faction == null || !Double.isFinite(value) || value <= 0.0D
                    || value > Integer.MAX_VALUE || value != Math.rint(value)) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Érvénytelen immutable szezonpont-delta: " + key + "/" + factionKey);
                continue;
            }
            if (!serverWide && faction != targetFaction) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Más frakcióra mutató szezonpont-delta frakció-célzott outboxban: " + key);
                continue;
            }
            deltas.put(faction, (int) value);
        }
        return Map.copyOf(deltas);
    }

    @Override
    public synchronized void save() {
        if (!saveStrict()) {
            // A koordinátor hibagyűjtése csak dobásból lát — a néma false autosave/shutdown
            // alatt észrevétlen adatvesztés lenne.
            throw new IllegalStateException("community-goals.yml mentése sikertelen — részletek a logban");
        }
    }

    /** Writes receipts and counters in the same atomic file image. */
    private boolean saveStrict() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("season.number", progressSeasonNumber);
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
            for (final Map.Entry<FactionType, Integer> delta : pending.seasonDeltas().entrySet()) {
                yaml.set(base + ".season-deltas." + delta.getKey().name(), delta.getValue());
            }
            yaml.set(base + ".buff-minutes", pending.buffMinutes());
        }
        try {
            YamlStore.saveAtomic(storageFile, yaml);
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save community-goals.yml: "
                    + exception.getMessage());
            return false;
        } catch (final hu.taliann.icesmp.storage.CriticalPersistenceWriteError fatal) {
            // A kritikus write-circuit már beállt (minden további írás tiltva) — itt false-t
            // adunk, hogy a hívó rollback-ága lefusson; a koordinátort a void save() wrapper
            // dobása értesíti. A fatal elnyelése nélkül a rollback kimaradna (Error != IOException).
            plugin.getLogger().severe(fatal.getMessage() == null ? fatal.toString() : fatal.getMessage());
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

    /**
     * Serializes the old community generation, the durable season commit and the community reset.
     * Every contribution method uses this manager monitor, so no Folia contribution can land in
     * the gap between the closing standings snapshot and the new community generation.
     *
     * <p>The callback MUST commit season.yml before returning {@code true}. Only then is the
     * community marker advanced and its progress cleared. A crash in between is recovered during
     * {@link #load()} from the one-generation marker lag.</p>
     */
    public synchronized boolean commitSeasonTransition(final int closingSeason,
                                                        final int openedSeason,
                                                        final BooleanSupplier seasonCommit) {
        if (seasonTransitionBlocked) {
            throw new IllegalStateException(
                    "Community season transition is blocked by an earlier persistence failure");
        }
        if (seasonCommit == null) {
            throw new IllegalArgumentException("seasonCommit callback is required");
        }

        // Read-only/durable payout gate: unlike the old preflight, this does NOT clear progress.
        flushPendingCompletions();
        try {
            CommunitySeasonState.validateTransition(progressSeasonNumber, closingSeason,
                    openedSeason, !pendingCompletions.isEmpty());
        } catch (final IllegalArgumentException | IllegalStateException blocked) {
            plugin.getLogger().severe("A szezonzárás elhalasztva: " + blocked.getMessage());
            throw blocked;
        }

        // This callback performs the season snapshot, side effects and durable season.yml commit
        // while the community monitor is still held. Contributions therefore block instead of
        // crossing from reset community progress into the old league standings.
        if (!seasonCommit.getAsBoolean()) {
            return false;
        }

        progress.clear();
        contributionReceipts.clear();
        progressSeasonNumber = openedSeason;
        persistSeasonAlignmentOrThrow("post-commit community season reset");
        return true;
    }

    private void persistSeasonAlignmentOrThrow(final String operation) {
        try {
            if (!saveStrict()) {
                seasonTransitionBlocked = true;
                throw new IllegalStateException(operation + " persistence failed");
            }
        } catch (final RuntimeException | Error failure) {
            seasonTransitionBlocked = true;
            throw failure;
        }
    }

    private void ensureContributionsEnabled() {
        if (seasonTransitionBlocked) {
            throw new IllegalStateException(
                    "Community contributions are blocked until season-state recovery");
        }
    }

    public long getProgress(final String goalId) {
        return progress.getOrDefault(goalId.toLowerCase(Locale.ROOT), 0L);
    }

    /** Ordinary non-economic event contribution; completion is durable before rewards run. */
    public synchronized void contribute(final Player player, final String objectiveType,
                                        final String materialOrEntity, final int amount) {
        ensureContributionsEnabled();
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
        ensureContributionsEnabled();
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
     * újrajátssza — a tartós jutalom nem veszhet el.
     */
    private void enqueueCompletions(final List<Completion> completions) {
        for (final Completion completion : completions) {
            final ConfigurationSection goal = completion.goal();
            final List<FactionType> targets = completion.serverWide()
                    ? List.of(FactionType.values()) : List.of(completion.goalFaction());
            final String source = completion.serverWide() ? "community-server" : "community";
            final int rawSeasonPoints = Math.max(0,
                    configManager.getInt("community-goals.season-points", 8));

            // A jutalom PILLANATKÉPE kerül a naplóba, nem csak a cél azonosítója: egy közben
            // átírt/törölt config, eltelt fináléablak vagy megváltozott top2 különben eltérő
            // újrajátszást adna. Egy teljesítés = egy bejegyzés, ezért a count-ot itt bontjuk szét.
            for (int index = 0; index < completion.count(); index++) {
                final Map<FactionType, Integer> seasonDeltas =
                        seasonManager.calculatePointsDeltas(targets, rawSeasonPoints, source);
                pendingCompletions.add(new PendingCompletion(UUID.randomUUID(),
                        goal.getName(),
                        goal.getString("display-name", "Közösségi cél"),
                        completion.serverWide(), completion.goalFaction(),
                        Math.max(0.0D, goal.getDouble("reward-treasury", 0.0D)),
                        seasonDeltas,
                        Math.max(0, goal.getInt("reward-buff-minutes", 0))));
            }
        }
    }

    /**
     * Kifizeti és tartósan eltünteti a függő teljesítéseket. Minden gazdasági hatás a cél-store
     * SAJÁT idempotens útján megy (a nyugta az egyenleggel/pontokkal egyetlen atomi fájl-képbe
     * kerül), ezért a bejegyzés CSAK akkor törölhető, ha minden cél visszaigazolta magát.
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
            // A tartós hatások idempotensek (grant-id), ezért a visszakerülő bejegyzés
            // újrajátszása nem duplázza a jutalmat.
            pendingCompletions.addAll(settled);
            plugin.getLogger().severe("A közösségi kifizetés nyugtázása nem sikerült — a függő "
                    + "bejegyzések maradnak; az újrajátszás idempotens, nem fizet kétszer.");
        }
    }

    /**
     * Egy teljesítés kifizetése. A kincstár és a szezonpont tartós, pontosan egyszeri hatás.
     * A hirdetés és az online játékosokra ütemezett, átmeneti buff tudatosan NEM része ennek a
     * durability-szerződésnek: crash az ütemezés és a region-task futása között elveszítheti őket,
     * cserébe nem tarthatják nyitva az economic outboxot és nem replayelhetők korlátlanul.
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
        for (final Map.Entry<FactionType, Integer> delta : pending.seasonDeltas().entrySet()) {
            if (!seasonManager.addExactPointsOnce(pending.grantId("season", delta.getKey()),
                    delta.getKey(), delta.getValue())) {
                allAcked = false;
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

    /** Buff a pillanatképből: átmeneti, best-effort hatás, ezért nem kap grant-nyugtát. */
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
