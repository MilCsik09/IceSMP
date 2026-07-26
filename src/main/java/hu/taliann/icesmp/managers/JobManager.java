package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.session.PlayerStateCleanup;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class JobManager implements PlayerStateCleanup {

    public static final int MAX_JOB_LEVEL = 50;

    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionManager factionManager;
    private java.util.function.Consumer<Player> xpChangeHook;
    private final NamespacedKey jobPrimaryKey;
    private final NamespacedKey jobPrimaryXpKey;
    private final NamespacedKey unlockedSpellsKey;
    private final NamespacedKey spellGrantsKey;
    // A megszűnt másodlagos-kaszt rendszer PDC-kulcsai: már semmi sem olvassa őket, de a
    // resetClass letakarítja a régi játékosokról maradt bejegyzéseket.
    private final NamespacedKey legacySecondaryKey;
    private final NamespacedKey legacySecondaryXpKey;

    /** Feloldás-forrás: a kaszt szintlépése. */
    public static final String SOURCE_BASE = "BASE";
    /** Feloldás-forrás: admin-parancs. Sosem vonja vissza automatikus reset. */
    public static final String SOURCE_ADMIN = "ADMIN";
    /** Specializációs forrás prefixe — a spec-reset EZT vonja vissza. */
    public static final String SOURCE_SPEC_PREFIX = "SPEC:";
    /** Talent-forrás prefixe — a talent-visszavonás EZT vonja vissza. */
    public static final String SOURCE_TALENT_PREFIX = "TALENT:";
    /** Quest-forrás prefixe. */
    public static final String SOURCE_QUEST_PREFIX = "QUEST:";
    /** Ismeretlen eredetű, még provenancia előtt kapott feloldás — automatikusan nem vonható vissza. */
    public static final String SOURCE_LEGACY = "LEGACY";

    public JobManager(final JavaPlugin plugin, final ConfigManager configManager,
                      final MessageManager messageManager, final FactionManager factionManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
        this.jobPrimaryKey = new NamespacedKey(plugin, "job_primary");
        this.jobPrimaryXpKey = new NamespacedKey(plugin, "job_primary_xp");
        this.unlockedSpellsKey = new NamespacedKey(plugin, "unlocked_spells");
        this.spellGrantsKey = new NamespacedKey(plugin, "spell_grants");
        this.legacySecondaryKey = new NamespacedKey(plugin, "job_secondary");
        this.legacySecondaryXpKey = new NamespacedKey(plugin, "job_secondary_xp");
    }

    public boolean hasPrimaryJob(final Player player) {
        return player.getPersistentDataContainer().has(jobPrimaryKey, PersistentDataType.STRING);
    }

    private volatile hu.taliann.icesmp.managers.FactionManager factionManagerRef;

    public void setFactionManager(final hu.taliann.icesmp.managers.FactionManager factionManager) {
        this.factionManagerRef = factionManager;
    }

    public JobType getPrimaryJob(final Player player) {
        final String rawPrimary = player.getPersistentDataContainer().get(jobPrimaryKey, PersistentDataType.STRING);
        return JobType.fromId(rawPrimary);
    }

    public int getXp(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(jobPrimaryXpKey, PersistentDataType.INTEGER, 0);
    }

    public int getPrimaryLevel(final Player player) {
        return getLevel(getXp(player));
    }

    public boolean canSelectPrimary(final Player player, final JobType job) {
        return job != null && !hasPrimaryJob(player) && meetsFactionRequirement(player, job);
    }

    /**
     * Checks whether the player satisfies the faction requirement of a class
     * (e.g. the Necromancer is only available to the Dark faction).
     *
     * @param player the player to check
     * @param job the class to select
     * @return true if the class has no faction requirement or the player is in the required faction
     */
    public boolean meetsFactionRequirement(final Player player, final JobType job) {
        final FactionType requiredFaction = job == null ? null : job.getRequiredFaction();
        if (requiredFaction == null) {
            return true;
        }

        return factionManager.getFaction(player.getUniqueId()) == requiredFaction;
    }

    public boolean setPrimaryJob(final Player player, final JobType job) {
        // Kapcsolható mód: DK csak Kitaszítottnak — itt (és nem csak a GUI-ban)
        // ellenőrizve, hogy jövőbeli hívási út se kerülhesse meg.
        if (job == JobType.DEATH_KNIGHT
                && configManager.getBoolean("classes.death-knight.dark-only", false)) {
            final hu.taliann.icesmp.managers.FactionManager factions = this.factionManagerRef;
            if (factions != null && factions.getFaction(player.getUniqueId())
                    != hu.taliann.icesmp.data.FactionType.DARK) {
                return false;
            }
        }
        if (!canSelectPrimary(player, job)) {
            return false;
        }

        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(jobPrimaryKey, PersistentDataType.STRING, job.getId());
        pdc.set(jobPrimaryXpKey, PersistentDataType.INTEGER, 0);
        applyAutoUnlocks(player);
        AdvancementService.award(player, "root");
        AdvancementService.award(player, "first_class");
        return true;
    }

    public boolean addXpToJob(final Player player, final int amount) {
        if (amount <= 0 || !hasPrimaryJob(player)) {
            return false;
        }

        return setXp(player, Math.max(0, getXp(player) + amount));
    }

    public boolean setXp(final Player player, final int xp) {
        if (!hasPrimaryJob(player)) {
            return false;
        }

        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(jobPrimaryXpKey, PersistentDataType.INTEGER, Math.max(0, xp));
        applyAutoUnlocks(player);
        if (getPrimaryLevel(player) >= MAX_JOB_LEVEL) {
            AdvancementService.award(player, "class_max");
        }
        if (xpChangeHook != null) {
            xpChangeHook.accept(player);
        }
        return true;
    }

    /**
     * Registers a hook invoked after every XP change (setter injection breaks the
     * JobManager &lt;-&gt; SpecializationManager dependency cycle). Used for
     * specialization spell unlocks.
     *
     * @param hook the callback receiving the affected player
     */
    public void setXpChangeHook(final java.util.function.Consumer<Player> hook) {
        this.xpChangeHook = hook;
    }

    /**
     * Unlocks every config-mapped spell of the (primary) class whose required level
     * has been reached. Mapping lives under 'classes.&lt;jobId&gt;.spell-unlocks' in config.yml.
     *
     * @param player the player to check
     */
    public void applyAutoUnlocks(final Player player) {
        final JobType job = getPrimaryJob(player);
        if (job == null || configManager.getConfiguration() == null) {
            return;
        }

        final ConfigurationSection unlockSection = configManager.getConfiguration()
                .getConfigurationSection("classes." + job.getId() + ".spell-unlocks");
        if (unlockSection == null) {
            return;
        }

        final int level = getPrimaryLevel(player);
        for (final String spellId : unlockSection.getKeys(false)) {
            final int requiredLevel = unlockSection.getInt(spellId, Integer.MAX_VALUE);
            if (level < requiredLevel) {
                continue;
            }
            // A forrás akkor is rögzül, ha a spellt már más adta (az unlockSpell ilyenkor csak
            // a forrást írja be és false-t ad): így a talent/spec visszavonás nem viszi el a
            // kaszt-szintből IS járó képességet.
            if (unlockSpell(player, spellId, SOURCE_BASE)) {
                player.sendMessage(messageManager.getMessage(
                        "job-spell-auto-unlocked",
                        "&aÚj képesség feloldva: &e{spell} &7(szint {level})",
                        Map.of("spell", messageManager.get("spell." + spellId.toLowerCase(Locale.ROOT) + ".name",
                                spellId.toLowerCase(Locale.ROOT)), "level", String.valueOf(requiredLevel))
                ));
            }
        }
    }


    public List<String> getUnlockedSpellIds(final Player player) {
        final String raw = player.getPersistentDataContainer().get(unlockedSpellsKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        final Set<String> uniqueIds = new LinkedHashSet<>();
        for (final String token : raw.split(",")) {
            final String id = token.trim().toLowerCase();
            if (!id.isEmpty()) {
                uniqueIds.add(id);
            }
        }

        return List.copyOf(uniqueIds);
    }

    public boolean hasUnlockedSpell(final Player player, final String spellId) {
        if (spellId == null || spellId.isBlank()) {
            return false;
        }

        return getUnlockedSpellIds(player).contains(spellId.toLowerCase());
    }

    public void setUnlockedSpellIds(final Player player, final List<String> spellIds) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (spellIds == null || spellIds.isEmpty()) {
            pdc.remove(unlockedSpellsKey);
            return;
        }

        final Set<String> uniqueIds = new LinkedHashSet<>();
        for (final String spellId : spellIds) {
            if (spellId == null || spellId.isBlank()) {
                continue;
            }
            uniqueIds.add(spellId.trim().toLowerCase());
        }

        if (uniqueIds.isEmpty()) {
            pdc.remove(unlockedSpellsKey);
            return;
        }

        pdc.set(unlockedSpellsKey, PersistentDataType.STRING, String.join(",", uniqueIds));
    }

    public boolean unlockSpell(final Player player, final String spellId) {
        return unlockSpell(player, spellId, SOURCE_LEGACY);
    }

    /**
     * Unlocks a spell and RECORDS which system granted it. Without provenance a spec/talent
     * reset could only guess: the spec reset left its own spells behind (so specs stacked
     * without limit), while the talent revoke stripped a spell the class level had also
     * granted. Every automatic revoke path keys off this record.
     *
     * @param spellId the spell to unlock
     * @param source  {@link #SOURCE_BASE}/{@link #SOURCE_ADMIN}, or a prefixed source
     *                ({@code SPEC:<id>}, {@code TALENT:<id>}, {@code QUEST:<id>})
     * @return true if the spell was newly unlocked (false = already had it; the source is
     *         still recorded, so a second grantor keeps it alive when the first is revoked)
     */
    public boolean unlockSpell(final Player player, final String spellId, final String source) {
        if (spellId == null || spellId.isBlank()) {
            return false;
        }

        final String normalized = spellId.trim().toLowerCase();
        addGrantSource(player, normalized, source);

        final List<String> unlocked = new ArrayList<>(getUnlockedSpellIds(player));
        if (unlocked.contains(normalized)) {
            return false;
        }

        unlocked.add(normalized);
        setUnlockedSpellIds(player, unlocked);
        return true;
    }

    /**
     * Drops one grantor's claim on a spell and removes the spell only when no other
     * grantor is left. A spell the class level ALSO granted survives a talent respec.
     *
     * @return true when the spell itself was removed from the unlocked list
     */
    public boolean revokeGrant(final Player player, final String spellId, final String source) {
        if (spellId == null || spellId.isBlank()) {
            return false;
        }
        final String normalized = spellId.trim().toLowerCase();
        final Map<String, Set<String>> grants = readGrants(player);
        final Set<String> sources = grants.get(normalized);
        if (sources != null) {
            sources.remove(source);
            if (sources.isEmpty()) {
                grants.remove(normalized);
            }
            writeGrants(player, grants);
            if (sources.isEmpty() && !grants.containsKey(normalized)) {
                return removeUnlockedSpell(player, normalized);
            }
            return false;
        }
        // Provenancia előtti feloldás: nincs mit forrásonként mérlegelni, a hívó akarata dönt.
        return removeUnlockedSpell(player, normalized);
    }

    /**
     * Revokes every grant whose source matches the predicate (e.g. every {@code SPEC:*}
     * source except the specialization still held). A spell kept alive by another source
     * stays unlocked.
     *
     * @return the spell ids that actually lost their last source and were removed
     */
    public List<String> revokeGrantsFrom(final Player player, final java.util.function.Predicate<String> sourceMatches) {
        final Map<String, Set<String>> grants = readGrants(player);
        final List<String> removed = new ArrayList<>();
        boolean changed = false;
        for (final Map.Entry<String, Set<String>> entry : new ArrayList<>(grants.entrySet())) {
            if (!entry.getValue().removeIf(sourceMatches)) {
                continue;
            }
            changed = true;
            if (entry.getValue().isEmpty()) {
                grants.remove(entry.getKey());
                removed.add(entry.getKey());
            }
        }
        if (changed) {
            writeGrants(player, grants);
        }
        for (final String spellId : removed) {
            removeUnlockedSpell(player, spellId);
        }
        return List.copyOf(removed);
    }

    /** Drops the whole provenance record (admin spell-wipe: nothing is granted any more). */
    public void clearSpellGrants(final Player player) {
        player.getPersistentDataContainer().remove(spellGrantsKey);
    }

    /** The recorded grant sources of a spell (empty when the grant predates provenance). */
    public Set<String> getGrantSources(final Player player, final String spellId) {
        if (spellId == null || spellId.isBlank()) {
            return Set.of();
        }
        final Set<String> sources = readGrants(player).get(spellId.trim().toLowerCase());
        return sources == null ? Set.of() : Set.copyOf(sources);
    }

    private boolean removeUnlockedSpell(final Player player, final String normalized) {
        final List<String> unlocked = new ArrayList<>(getUnlockedSpellIds(player));
        if (!unlocked.removeIf(id -> id.equalsIgnoreCase(normalized))) {
            return false;
        }
        setUnlockedSpellIds(player, unlocked);
        return true;
    }

    private void addGrantSource(final Player player, final String normalized, final String source) {
        final String cleanSource = source == null || source.isBlank() ? SOURCE_LEGACY : source.trim();
        final Map<String, Set<String>> grants = readGrants(player);
        if (grants.computeIfAbsent(normalized, key -> new LinkedHashSet<>()).add(cleanSource)) {
            writeGrants(player, grants);
        }
    }

    /** PDC-formátum: {@code spellId=SRC1|SRC2;spellId2=SRC}. */
    private Map<String, Set<String>> readGrants(final Player player) {
        final Map<String, Set<String>> grants = new java.util.LinkedHashMap<>();
        final String raw = player.getPersistentDataContainer().get(spellGrantsKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return grants;
        }
        for (final String entry : raw.split(";")) {
            final int split = entry.indexOf('=');
            if (split <= 0) {
                continue;
            }
            final String spellId = entry.substring(0, split).trim().toLowerCase();
            if (spellId.isEmpty()) {
                continue;
            }
            final Set<String> sources = grants.computeIfAbsent(spellId, key -> new LinkedHashSet<>());
            for (final String source : entry.substring(split + 1).split("\\|")) {
                if (!source.isBlank()) {
                    sources.add(source.trim());
                }
            }
        }
        return grants;
    }

    private void writeGrants(final Player player, final Map<String, Set<String>> grants) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (grants.isEmpty()) {
            pdc.remove(spellGrantsKey);
            return;
        }
        final StringBuilder builder = new StringBuilder();
        for (final Map.Entry<String, Set<String>> entry : grants.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(entry.getKey()).append('=').append(String.join("|", entry.getValue()));
        }
        if (builder.length() == 0) {
            pdc.remove(spellGrantsKey);
            return;
        }
        pdc.set(spellGrantsKey, PersistentDataType.STRING, builder.toString());
    }

    /**
     * One-time provenance backfill for players who unlocked spells before the grant record
     * existed: the class' own {@code spell-unlocks} become {@link #SOURCE_BASE}, a spell listed
     * by some specialization's unlock table becomes that {@code SPEC:<id>} source (so a reset
     * can finally take back what the spec gave), everything else stays
     * {@link #SOURCE_LEGACY} and is never auto-revoked. Writes the player's PDC — call on the
     * player's own region thread.
     */
    public void backfillSpellGrants(final Player player) {
        final List<String> unlocked = getUnlockedSpellIds(player);
        if (unlocked.isEmpty() || configManager.getConfiguration() == null) {
            return;
        }
        final Map<String, Set<String>> grants = readGrants(player);
        final JobType job = getPrimaryJob(player);
        final Set<String> baseSpells = new LinkedHashSet<>();
        if (job != null) {
            final ConfigurationSection baseSection = configManager.getConfiguration()
                    .getConfigurationSection("classes." + job.getId() + ".spell-unlocks");
            if (baseSection != null) {
                for (final String spellId : baseSection.getKeys(false)) {
                    baseSpells.add(spellId.toLowerCase(Locale.ROOT));
                }
            }
        }
        final Map<String, String> specSpells = new java.util.LinkedHashMap<>();
        final ConfigurationSection specs = configManager.getConfiguration()
                .getConfigurationSection("specializations");
        if (specs != null) {
            for (final String specId : specs.getKeys(false)) {
                final ConfigurationSection unlockSection = specs.getConfigurationSection(specId + ".spell-unlocks");
                if (unlockSection == null) {
                    continue;
                }
                for (final String spellId : unlockSection.getKeys(false)) {
                    specSpells.putIfAbsent(spellId.toLowerCase(Locale.ROOT), specId);
                }
            }
        }

        boolean changed = false;
        for (final String spellId : unlocked) {
            if (grants.containsKey(spellId)) {
                continue;
            }
            final String source;
            if (baseSpells.contains(spellId)) {
                source = SOURCE_BASE;
            } else if (specSpells.containsKey(spellId)) {
                source = SOURCE_SPEC_PREFIX + specSpells.get(spellId);
            } else {
                source = SOURCE_LEGACY;
            }
            grants.computeIfAbsent(spellId, key -> new LinkedHashSet<>()).add(source);
            changed = true;
        }
        if (changed) {
            writeGrants(player, grants);
        }
    }

    /**
     * Admin reset: wipes the player's class choice entirely — the class, its XP/level and all
     * unlocked spells — putting them back to the "no class chosen" state so a fresh class can be picked.
     * (The class specialization is stored separately; the caller should also reset it.) Writes the
     * player's PDC, so it must run on the player's own region thread (Folia).
     *
     * @param player the player whose class to reset
     */
    public void resetClass(final Player player) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(jobPrimaryKey);
        pdc.remove(jobPrimaryXpKey);
        pdc.remove(unlockedSpellsKey);
        pdc.remove(spellGrantsKey);
        // Legacy tisztítás: a megszűnt másodlagos-kaszt bejegyzések eltávolítása régi játékosokról.
        pdc.remove(legacySecondaryKey);
        pdc.remove(legacySecondaryXpKey);
    }

    public void cleanup(final java.util.UUID playerId) {
        // Intentionally empty: Job data is persisted in PDC and must survive reconnects.
    }

    public void clearPlayerState(final java.util.UUID playerId) {
        cleanup(playerId);
    }

    private int getLevel(final int xp) {
        final int baseXp = Math.max(1, configManager.getInt("classes.leveling.base-xp", 100));
        final int increment = Math.max(0, configManager.getInt("classes.leveling.increment-per-level", 20));

        int level = 1;
        int remaining = Math.max(0, xp);
        while (level < MAX_JOB_LEVEL) {
            // Progressive curve: level n -> n+1 costs base-xp + (n-1) * increment XP.
            final int levelCost = baseXp + ((level - 1) * increment);
            if (remaining < levelCost) {
                break;
            }
            remaining -= levelCost;
            level++;
        }

        return level;
    }
}

