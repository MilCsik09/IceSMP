package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.archer.ArcherGameplayService;
import hu.taliann.icesmp.assassin.AssassinGameplayService;
import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.GameplayV2ClassPolicy;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.deathknight.DeathKnightGameplayService;
import hu.taliann.icesmp.demonhunter.DemonHunterGameplayService;
import hu.taliann.icesmp.druid.DruidGameplayService;
import hu.taliann.icesmp.evoker.EvokerGameplayService;
import hu.taliann.icesmp.gui.SpellbookGUI;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellFavoritesManager;
import hu.taliann.icesmp.managers.SpellMasteryManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.managers.TalentManager;
import hu.taliann.icesmp.monk.MonkGameplayService;
import hu.taliann.icesmp.paladin.PaladinGameplayService;
import hu.taliann.icesmp.priest.PriestGameplayService;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileSpellbookStateStore;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.shaman.ShamanGameplayService;
import hu.taliann.icesmp.spells.CastModifiers;
import hu.taliann.icesmp.spells.CastOutcome;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.spells.SpellCostType;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.warlock.WarlockGameplayService;
import hu.taliann.icesmp.warrior.WarriorGameplayService;
import hu.taliann.icesmp.wizard.WizardGameplayService;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Input adapter and transactional cast coordinator for the personal Lélekkapocs.
 *
 * <p>The listener owns input/debounce/feedback only. Per-class behavior is
 * registered as one lifecycle hook for the player's authoritative class, so a
 * cast does not walk thirteen independent if-blocks. Validation/preparation is
 * state-separated from execution; only a successful typed {@link CastOutcome}
 * commits class state, cost, cooldown, combo history and statistics.</p>
 */
public final class AbilityCatalystListener implements Listener, PlayerStateCleanup {

    private enum PreparationResult { READY, PREPARING, REJECTED }

    @FunctionalInterface
    private interface PreparationHook {
        PreparationResult prepare(Player player, Spell spell);
    }

    @FunctionalInterface
    private interface PowerHook {
        double bonusPercent(Player player, Spell spell);
    }

    @FunctionalInterface
    private interface CommitHook {
        void commit(Player player, Spell spell, boolean resourceSpent, int spentAmount);
    }

    @FunctionalInterface
    private interface ActiveKitHook {
        List<String> resolve(Player player, List<String> unlocked, Set<String> favorites);
    }

    private record ClassCastHook(
            PreparationHook preparation,
            PowerHook power,
            CommitHook commit,
            ActiveKitHook activeKit
    ) {
    }

    private static final PowerHook NO_POWER = (player, spell) -> 0.0D;

    private final JavaPlugin plugin;
    private final CatalystItemFactory catalystItemFactory;
    private final SpellRegistry spellRegistry;
    private final JobManager jobManager;
    private final SpecializationManager specializationManager;
    private final SpellMasteryManager masteryManager;
    private volatile hu.taliann.icesmp.managers.QuestManager questManager;
    private final PlayerProfileSpellbookStateStore spellbookStateStore =
            new PlayerProfileSpellbookStateStore();
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final TalentManager talentManager;
    private final ResourceManager resourceManager;
    private final SpellFavoritesManager spellFavoritesManager;
    private volatile ClassSpecProfileGateway profileGateway;
    private final Map<JobType, ClassCastHook> classHooks = new ConcurrentHashMap<>();
    private volatile hu.taliann.icesmp.managers.ItemRarityService itemRarityServiceRef;
    private volatile hu.taliann.icesmp.managers.StatsManager statsManager;

    private final Map<UUID, Map<String, Long>> spellCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedSpellProjection = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cycleDebounce = new ConcurrentHashMap<>();
    private final Map<UUID, Long> castDebounce = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastCastSpell = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCastTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> secondLastCastSpell = new ConcurrentHashMap<>();
    private final Map<UUID, Long> secondLastCastTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> comboBoostUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hintStartedAt = new ConcurrentHashMap<>();

    public AbilityCatalystListener(final JavaPlugin plugin,
                                   final JobManager jobManager,
                                   final SpellRegistry spellRegistry,
                                   final CatalystItemFactory catalystItemFactory,
                                   final ConfigManager configManager,
                                   final SpellMasteryManager masteryManager,
                                   final SpecializationManager specializationManager,
                                   final ResourceManager resourceManager,
                                   final TalentManager talentManager,
                                   final MessageManager messageManager,
                                   final SpellFavoritesManager spellFavoritesManager) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.jobManager = java.util.Objects.requireNonNull(jobManager, "jobManager");
        this.spellRegistry = java.util.Objects.requireNonNull(spellRegistry, "spellRegistry");
        this.catalystItemFactory = java.util.Objects.requireNonNull(catalystItemFactory, "catalystItemFactory");
        this.configManager = java.util.Objects.requireNonNull(configManager, "configManager");
        this.masteryManager = java.util.Objects.requireNonNull(masteryManager, "masteryManager");
        this.specializationManager = java.util.Objects.requireNonNull(specializationManager, "specializationManager");
        this.resourceManager = java.util.Objects.requireNonNull(resourceManager, "resourceManager");
        this.talentManager = talentManager;
        this.messageManager = java.util.Objects.requireNonNull(messageManager, "messageManager");
        this.spellFavoritesManager = java.util.Objects.requireNonNull(spellFavoritesManager, "spellFavoritesManager");
    }

    public void setProfileGateway(final ClassSpecProfileGateway gateway) {
        profileGateway = java.util.Objects.requireNonNull(gateway, "gateway");
    }

    public void setQuestManager(final hu.taliann.icesmp.managers.QuestManager manager) {
        questManager = java.util.Objects.requireNonNull(manager, "manager");
    }

    private static ClassCastHook standardHook(final java.util.function.BiPredicate<Player, Spell> gate,
                                              final PowerHook power,
                                              final CommitHook commit,
                                              final ActiveKitHook activeKit) {
        return new ClassCastHook(
                (player, spell) -> gate.test(player, spell)
                        ? PreparationResult.READY : PreparationResult.REJECTED,
                power, commit, activeKit);
    }

    public void setWarriorGameplayService(final WarriorGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.WARRIOR, standardHook(
                service::beforeCast, NO_POWER, service::afterCast, service::activeSpellIds));
    }

    public void setEvokerGameplayService(final EvokerGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.EVOKER, new ClassCastHook(
                (player, spell) -> service.beforeCast(player, spell)
                        ? PreparationResult.READY : PreparationResult.PREPARING,
                service::castPowerBonusPercent, service::afterCast, service::activeSpellIds));
    }

    public void setArcherGameplayService(final ArcherGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.ARCHER, standardHook(
                service::beforeCast, NO_POWER, service::afterCast, service::activeSpellIds));
    }

    public void setShamanGameplayService(final ShamanGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.SHAMAN, standardHook(
                service::beforeCast, service::castPowerBonusPercent, service::afterCast, service::activeSpellIds));
    }

    public void setMonkGameplayService(final MonkGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.MONK, standardHook(
                service::beforeCast, service::castPowerBonusPercent, service::afterCast, service::activeSpellIds));
    }

    public void setPaladinGameplayService(final PaladinGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.PALADIN, standardHook(
                service::beforeCast, service::castPowerBonusPercent, service::afterCast, service::activeSpellIds));
    }

    public void setWizardGameplayService(final WizardGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.WIZARD, standardHook(
                service::beforeCast, service::castPowerBonusPercent, service::afterCast, service::activeSpellIds));
    }

    public void setWarlockGameplayService(final WarlockGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.WARLOCK, standardHook(
                service::beforeCast, service::castPowerBonusPercent, service::afterCast, service::activeSpellIds));
    }

    public void setAssassinGameplayService(final AssassinGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.ASSASSIN, new ClassCastHook(
                (player, spell) -> {
                    if (assassinStealthBlocked(service, player, spell)) {
                        return PreparationResult.REJECTED;
                    }
                    return service.beforeCast(player, spell)
                            ? PreparationResult.READY : PreparationResult.REJECTED;
                },
                service::castPowerBonusPercent, service::afterCast, service::activeSpellIds));
    }

    public void setDeathKnightGameplayService(final DeathKnightGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.DEATH_KNIGHT, standardHook(
                service::beforeCast, service::castPowerBonusPercent, service::afterCast, service::activeSpellIds));
    }

    public void setPriestGameplayService(final PriestGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.PRIEST, standardHook(
                service::beforeCast, service::castPowerBonusPercent, service::afterCast, service::activeSpellIds));
    }

    public void setDruidGameplayService(final DruidGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.DRUID, standardHook(
                service::beforeCast, service::castPowerBonusPercent, service::afterCast, service::activeSpellIds));
    }

    public void setDemonHunterGameplayService(final DemonHunterGameplayService service) {
        java.util.Objects.requireNonNull(service, "service");
        classHooks.put(JobType.DEMON_HUNTER, standardHook(
                service::beforeCast, service::castPowerBonusPercent, service::afterCast, service::activeSpellIds));
    }

    public void setItemRarityService(final hu.taliann.icesmp.managers.ItemRarityService itemRarityService) {
        itemRarityServiceRef = itemRarityService;
    }

    public void setStatsManager(final hu.taliann.icesmp.managers.StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    private ClassCastHook classHook(final Player player) {
        final JobType job = player == null ? null : jobManager.getPrimaryJob(player);
        return job == null ? null : classHooks.get(job);
    }

    private boolean assassinStealthBlocked(final AssassinGameplayService service,
                                           final Player player,
                                           final Spell spell) {
        final String id = spell.getId().toLowerCase(Locale.ROOT);
        if (!configManager.getStringList("classes.assassin.phantom.stealth-spells").stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).toList().contains(id)) {
            return false;
        }
        final var secondary = service.hudState(player).secondary();
        if (!"detection".equals(secondary.id()) || secondary.maximum() <= 0
                || secondary.value() < secondary.maximum()) {
            return false;
        }
        player.sendActionBar(messageManager.getMessage("assassin.stealth.detected",
                "<red>Túl feltűnő vagy — az Észleltség nem enged az árnyékba.</red>"));
        return true;
    }

    private boolean isUsableCatalyst(final Player player, final ItemStack item) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (catalystItemFactory.isCatalyst(item)) {
            return catalystItemFactory.isUsableBy(item, player.getUniqueId(), job);
        }
        if (job == null || item == null
                || !configManager.getBoolean("spells.melee-catalyst.enabled", true)) {
            return false;
        }
        if (!configManager.getStringList("spells.melee-catalyst.materials")
                .contains(item.getType().name())) {
            return false;
        }
        if (!configManager.getStringList("spells.melee-catalyst.classes").contains(job.getId())) {
            return false;
        }
        return !GameplayV2ClassPolicy.isEnabled(job.getId()) || authorizedByPersonalSoulbond(player);
    }

    private boolean authorizedByPersonalSoulbond(final Player player) {
        if (player == null) return false;
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) return false;
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (catalystItemFactory.isPersonalCopyFor(stack, player.getUniqueId(), job)) return true;
        }
        return false;
    }

    private double dynamicPowerMultiplier(final Player player) {
        if (!configManager.getBoolean("spells.dynamic-scaling.enabled", true)) return 1.0D;
        final double perLevel = Math.max(0.0D,
                configManager.getDouble("spells.dynamic-scaling.per-level-percent", 0.5D));
        final double talentBonus = talentManager == null
                ? 0.0D : talentManager.getEffectTotal(player, "spell-power");
        final double cap = Math.max(0.0D,
                configManager.getDouble("spells.dynamic-scaling.max-bonus-percent", 50.0D));
        double gearBonus = 0.0D;
        final hu.taliann.icesmp.managers.ItemRarityService rarity = itemRarityServiceRef;
        if (rarity != null) {
            for (final ItemStack armor : player.getInventory().getArmorContents()) {
                gearBonus += rarity.spellPowerOf(armor);
            }
            gearBonus += rarity.spellPowerOf(player.getInventory().getItemInMainHand());
        }
        final double bonusPercent = Math.max(-50.0D,
                Math.min(cap, jobManager.getPrimaryLevel(player) * perLevel
                        + Math.max(0.0D, talentBonus) + gearBonus));
        return 1.0D + bonusPercent / 100.0D;
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        final Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        final Player player = event.getPlayer();
        final ItemStack held = player.getInventory().getItemInMainHand();
        if (!isUsableCatalyst(player, held)) return;
        if (action == Action.RIGHT_CLICK_BLOCK && !catalystItemFactory.isCatalyst(held)) return;
        if (!profileRuntimeReady(player)) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (player.isSneaking()) {
            openSpellbook(player);
            return;
        }
        final long now = System.currentTimeMillis();
        final long previous = castDebounce.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 120L) return;
        castDebounce.put(player.getUniqueId(), now);
        castSelectedSpell(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerAnimation(final PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        final Player player = event.getPlayer();
        if (!player.isSneaking()
                || !isUsableCatalyst(player, player.getInventory().getItemInMainHand())
                || !profileRuntimeReady(player)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - cycleDebounce.getOrDefault(player.getUniqueId(), 0L) < 120L) return;
        cycleDebounce.put(player.getUniqueId(), now);
        cycleSpell(player, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHotbarScroll(final PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        if (!player.isSneaking()
                || !isUsableCatalyst(player, player.getInventory().getItemInMainHand())
                || !profileRuntimeReady(player)) {
            return;
        }
        event.setCancelled(true);
        final int step = ((event.getNewSlot() - event.getPreviousSlot() + 9) % 9) <= 4 ? 1 : -1;
        cycleSpell(player, step);
    }

    private void cycleSpell(final Player player, final int step) {
        final List<String> cycle = resolveActiveSpellIds(player);
        if (cycle.isEmpty()) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.no-spells", "<red>Nincs elérhető képesség.</red>"));
            return;
        }
        final Set<String> favorites = spellFavoritesManager.favorites(player);
        final boolean favoritesOnly = !favorites.isEmpty() && cycle.stream().allMatch(favorites::contains);
        final String currentId = selectedSpellId(player.getUniqueId());
        final int cyclePos = currentId.isBlank() ? -1 : cycle.indexOf(currentId);
        final int nextCyclePos = Math.floorMod(cyclePos + step, cycle.size());
        final String selectedId = cycle.get(nextCyclePos);
        persistSelectedSpell(player, selectedId);

        final Spell selected = spellRegistry.getById(selectedId);
        if (selected == null) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.current-spell-unknown", "<gray>Aktuális képesség: Ismeretlen</gray>"));
            return;
        }
        final int rank = masteryManager.getRank(player, selected.getId());
        final String mastery = rank > 0 ? " <aqua>★" + rank + "</aqua>" : "";
        player.sendActionBar(messageManager.getMessage(
                "catalyst.current-spell",
                "<gray>[{position}] <gold>{spell}</gold>{mastery} <dark_gray>({cost} {resource})</dark_gray> <dark_gray>— Shift+jobb katt: spellbook</dark_gray></gray>",
                Map.of("spell", selected.getName(), "mastery", mastery,
                        "cost", String.valueOf(displayedCost(selected)),
                        "resource", resolveResourceName(player, selected),
                        "position", (favoritesOnly ? "★" : "") + (nextCyclePos + 1) + "/" + cycle.size())));
        catalystItemFactory.playCycleSound(player, jobManager.getPrimaryJob(player));
    }

    private PreparationResult prepareClassCast(final Player player,
                                               final Spell spell,
                                               final ClassCastHook hook) {
        return hook == null ? PreparationResult.READY : hook.preparation().prepare(player, spell);
    }

    /** A kliens-eredetű slot-cast gépi kimenete; a játékos-üzeneteket a közös mag küldi. */
    public enum SlotCastStatus {
        SUCCESS, NO_CATALYST, PROFILE_NOT_READY, EMPTY_KIT, INVALID_SLOT, DEBOUNCED,
        ON_COOLDOWN, NOT_READY, CLASS_GATE_BLOCKED, INSUFFICIENT_COST, EXECUTION_FAILED,
        NOT_COMMITTED
    }

    public record SlotCastOutcome(SlotCastStatus status, String spellId) {
    }

    /**
     * A Client Bridge CAST_SLOT belépője: UGYANAZT a tranzakciós magot futtatja, mint a
     * katalizátor-input — nem keletkezik második cast-útvonal. A vanilla parity kapui
     * (használható katalizátor a főkézben, profil-session-készenlét, közös debounce) itt
     * is kötelezőek, különben a kliensmod item-követelmény nélküli castolást kapna. A
     * slot a futásidőben számolt aktív kit 1-alapú indexe; a hívás NEM módosítja a
     * katalizátorral kiválasztott spellt. A hívó felelőssége a játékos régió-szálán futni.
     */
    public SlotCastOutcome castActiveKitSlot(final Player player, final int slotIndex) {
        if (!isUsableCatalyst(player, player.getInventory().getItemInMainHand())) {
            return new SlotCastOutcome(SlotCastStatus.NO_CATALYST, "");
        }
        if (!profileRuntimeReady(player)) {
            return new SlotCastOutcome(SlotCastStatus.PROFILE_NOT_READY, "");
        }
        final List<String> active = resolveActiveSpellIds(player);
        if (active.isEmpty()) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.no-unlocked", "<red>Még nincs használható aktív képességed.</red>"));
            return new SlotCastOutcome(SlotCastStatus.EMPTY_KIT, "");
        }
        if (slotIndex < 1 || slotIndex > active.size()) {
            return new SlotCastOutcome(SlotCastStatus.INVALID_SLOT, "");
        }
        final Spell spell = spellRegistry.getById(active.get(slotIndex - 1));
        if (spell == null) {
            return new SlotCastOutcome(SlotCastStatus.INVALID_SLOT, "");
        }
        final long now = System.currentTimeMillis();
        final long previous = castDebounce.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 120L) {
            return new SlotCastOutcome(SlotCastStatus.DEBOUNCED, spell.getId());
        }
        castDebounce.put(player.getUniqueId(), now);
        return new SlotCastOutcome(castResolvedSpell(player, spell), spell.getId());
    }

    private void castSelectedSpell(final Player player) {
        final List<String> active = resolveActiveSpellIds(player);
        if (active.isEmpty()) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.no-unlocked", "<red>Még nincs használható aktív képességed.</red>"));
            return;
        }
        final Spell selected = resolveSelectedSpell(player, active);
        if (selected == null) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.invalid-selection",
                    "<red>A kiválasztott képesség nem érhető el az aktív specializationben.</red>"));
            return;
        }
        castResolvedSpell(player, selected);
    }

    /**
     * A megosztott tranzakciós cast-mag (validáció → költség-rezerválás → végrehajtás →
     * commit); a katalizátor-út és a kliens-eredetű slot-cast is ide fut be. A
     * visszatérési státusz csak gépi jelentés a kliens-útnak — minden játékos-üzenet
     * itt, mindkét útvonalra azonosan megy ki.
     */
    private SlotCastStatus castResolvedSpell(final Player player, final Spell selected) {
        final long now = System.currentTimeMillis();
        final long remainingMs = getRemainingCooldown(player, selected, now);
        if (remainingMs > 0L) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.cooldown", "<red>Várj még {seconds} mp-et!</red>",
                    Map.of("seconds", String.valueOf((long) Math.ceil(remainingMs / 1000.0D)))));
            return SlotCastStatus.ON_COOLDOWN;
        }
        if (!selected.canCast(player)) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.not-ready", "<red>Most nem tudod használni ezt a képességet.</red>"));
            return SlotCastStatus.NOT_READY;
        }

        final ClassCastHook hook = classHook(player);
        final PreparationResult preparation = prepareClassCast(player, selected, hook);
        if (preparation != PreparationResult.READY) return SlotCastStatus.CLASS_GATE_BLOCKED;

        final boolean useResource = resourceManager.usesResource(selected);
        final boolean canAfford = useResource
                ? resourceManager.canAfford(player, selected) : selected.hasRequiredCost(player);
        if (!canAfford) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.no-cost", "<red>Nincs elég {resource}! Szükséges: {amount}</red>",
                    Map.of("resource", resolveResourceName(player, selected),
                            "amount", String.valueOf(displayedCost(selected)))));
            return SlotCastStatus.INSUFFICIENT_COST;
        }

        final double chainBonusPercent = chainFinisherPercent(player, selected.getId(), now);
        final double classBonusPercent = hook == null ? 0.0D : hook.power().bonusPercent(player, selected);
        final double powerCap = Math.max(1.0D,
                configManager.getDouble("spells.total-power-cap", 1.75D));
        final double power = Math.min(powerCap,
                masteryManager.getPowerMultiplier(player, selected.getId())
                        * dynamicPowerMultiplier(player)
                        * (1.0D + chainBonusPercent / 100.0D)
                        * (1.0D + classBonusPercent / 100.0D));

        final CostReservation reservation = reserveCost(player, selected, useResource);
        final CastOutcome outcome;
        try {
            outcome = selected.cast(player, CastModifiers.standardPower(power));
        } catch (final RuntimeException failure) {
            reservation.rollback();
            plugin.getLogger().severe("Spell execution failed for " + player.getUniqueId() + '/'
                    + selected.getId() + ": " + rootMessage(failure));
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.execution-failed", "<red>A képesség végrehajtása meghiúsult; a költség visszajárt.</red>"));
            return SlotCastStatus.EXECUTION_FAILED;
        }
        if (outcome == null || !outcome.commitsCast()) {
            reservation.rollback();
            return SlotCastStatus.NOT_COMMITTED;
        }
        reservation.commit();

        if (hook != null) {
            try {
                hook.commit().commit(player, selected, useResource,
                        useResource ? reservation.spentAmount() : 0);
            } catch (final RuntimeException failure) {
                plugin.getLogger().severe("Class cast-state commit failed for "
                        + player.getUniqueId() + '/' + selected.getId() + ": " + rootMessage(failure));
            }
        }

        final boolean chainFinisher = chainBonusPercent > 0.0D;
        final boolean combo = chainFinisher || isComboMatch(player, selected.getId(), now);
        hintStartedAt.remove(player.getUniqueId());
        if (combo) comboBoostUntil.put(player.getUniqueId(), now + 3000L);
        final long refundMillis = combo ? comboRefundMillis(player, selected) : 0L;
        putCooldown(player, selected, now - refundMillis);
        applyCooldownOverlay(player, selected, refundMillis);
        playCastFlourish(player, combo);
        // A kaszt-bónusz ugyanúgy a varázslat erejét emeli, mint a lánc-befejező: ha
        // egyszerre él a kettő, a kiírt százalék a ténylegesen alkalmazott összeg legyen.
        final int classBonusShown = (int) Math.round(classBonusPercent);
        if (chainFinisher) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.combo-finisher",
                    "<gold>⚡ Kombó-lánc befejező! +{bonus}% erő és gyorsabb felépülés.</gold>",
                    Map.of("bonus", String.valueOf(
                            (int) Math.round(chainBonusPercent) + classBonusShown))));
        } else if (classBonusShown > 0) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.class-power",
                    "<aqua>✦ Kaszt-erő: +{bonus}% ezen a varázslaton.</aqua>",
                    Map.of("bonus", String.valueOf(classBonusShown))));
        } else if (combo) {
            final String nextInChain = nextComboStep(player, selected.getId());
            final Spell nextSpell = nextInChain == null ? null : spellRegistry.getById(nextInChain);
            if (nextSpell != null) {
                player.sendActionBar(messageManager.getMessage(
                        "catalyst.combo-next", "<gold>⚡ Kombó! Köv. a láncban: {next}</gold>",
                        Map.of("next", nextSpell.getName())));
            } else {
                player.sendActionBar(messageManager.getMessage(
                        "catalyst.combo", "<gold>⚡ Kombó! Gyorsabb felépülés.</gold>"));
            }
        } else {
            sendComboWindowHint(player, selected.getId());
        }

        final UUID playerId = player.getUniqueId();
        secondLastCastSpell.put(playerId, lastCastSpell.getOrDefault(playerId, ""));
        secondLastCastTime.put(playerId, lastCastTime.getOrDefault(playerId, 0L));
        lastCastSpell.put(playerId, selected.getId());
        lastCastTime.put(playerId, now);
        final hu.taliann.icesmp.managers.StatsManager stats = statsManager;
        if (stats != null) stats.recordSpellCast(playerId);
        final hu.taliann.icesmp.managers.QuestManager quests = questManager;
        if (quests != null) quests.handleSpellCast(player, selected.getId());
        return SlotCastStatus.SUCCESS;
    }

    private final class CostReservation {
        private final Player player;
        private final Spell spell;
        private final boolean resource;
        private final double preCastHealth;
        private final int spentAmount;
        private boolean open = true;

        private CostReservation(final Player player, final Spell spell, final boolean resource) {
            this.player = player;
            this.spell = spell;
            this.resource = resource;
            this.preCastHealth = player.getHealth();
            this.spentAmount = displayedCost(spell);
            if (resource) resourceManager.consume(player, spell);
            else spell.consumeCost(player);
        }

        private int spentAmount() { return spentAmount; }
        private void commit() { open = false; }
        private void rollback() {
            if (!open) return;
            open = false;
            if (resource) resourceManager.refund(player, spell);
            else if (spell.getCostType() == SpellCostType.HEALTH) player.setHealth(preCastHealth);
            else spell.refundCost(player);
        }
    }

    private CostReservation reserveCost(final Player player, final Spell spell, final boolean useResource) {
        return new CostReservation(player, spell, useResource);
    }

    private boolean profileRuntimeReady(final Player player) {
        final var gateway = profileGateway;
        if (gateway != null && gateway.isSessionReady(player.getUniqueId())) return true;
        player.sendActionBar(messageManager.getMessage(
                "profile-v2.runtime-blocked",
                "<red>A kaszt/specializáció profilod biztonsági ellenőrzést igényel. /spec info</red>"));
        return false;
    }

    public boolean refreshSoulbond(final Player player) {
        if (player == null || !profileRuntimeReady(player)) return false;
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) return false;
        ItemStack personal = null;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (!catalystItemFactory.isPersonalCopyFor(stack, player.getUniqueId(), job)) continue;
            if (personal == null) personal = stack;
            else player.getInventory().setItem(slot, null);
        }
        if (personal == null) {
            if (player.getInventory().firstEmpty() < 0) {
                player.sendMessage(messageManager.get("soulbond.inventory-full",
                        "&cA Lélekkapocs visszaállításához szabadíts fel egy inventory helyet, majd nyisd meg újra a kasztmenüt."));
                return false;
            }
            personal = catalystItemFactory.createCatalyst(job, player.getUniqueId());
            player.getInventory().addItem(personal);
        }
        final var profile = profileGateway.currentProfile(player.getUniqueId()).orElse(null);
        String activeSpec = "";
        int masteryRank = 0;
        Map<String, String> doctrines = Map.of();
        if (profile != null && profile.activeSlot() != null) {
            final ClassLoadout loadout = profile.loadout(profile.activeSlot());
            activeSpec = loadout.specializationId();
            masteryRank = loadout.mastery().rank();
            doctrines = loadout.doctrineChoices();
        }
        catalystItemFactory.refreshPresentation(personal, player.getUniqueId(), job,
                activeSpec, jobManager.getPrimaryLevel(player), masteryRank, doctrines);
        return true;
    }

    public boolean hasPersonalSoulbond(final Player player) {
        return authorizedByPersonalSoulbond(player);
    }

    private long effectiveCooldownMillis(final Player player, final Spell spell) {
        final long base = Math.max(0L, spell.getCooldown()) * 1000L;
        if (player == null) return base;
        return Math.max(0L, (long) (base
                * masteryManager.getCooldownMultiplier(player, spell.getId())));
    }

    private void applyCooldownOverlay(final Player player,
                                      final Spell spell,
                                      final long comboRefundMs) {
        if (!configManager.getBoolean("spells.cooldown-overlay.enabled", true)) return;
        final long cooldownTicks = Math.max(0L,
                (effectiveCooldownMillis(player, spell) - comboRefundMs) / 50L);
        if (cooldownTicks <= 0L) return;
        final ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) return;
        player.setCooldown(held, (int) Math.min(Integer.MAX_VALUE, cooldownTicks));
    }

    private boolean isComboMatch(final Player player, final String currentSpellId, final long now) {
        if (!configManager.getBoolean("spells.combos.enabled", true)) return false;
        final String previous = lastCastSpell.get(player.getUniqueId());
        if (previous == null) return false;
        final long windowMs = Math.max(1L,
                configManager.getLong("spells.combos.window-seconds", 4L)) * 1000L;
        if (now - lastCastTime.getOrDefault(player.getUniqueId(), 0L) > windowMs) return false;
        final ConfigurationSection combos = configManager.getConfiguration() == null
                ? null : configManager.getConfiguration().getConfigurationSection("spells.combos.pairs");
        if (combos == null) return false;
        for (final String key : combos.getKeys(false)) {
            final ConfigurationSection pair = combos.getConfigurationSection(key);
            if (pair != null && previous.equalsIgnoreCase(pair.getString("first"))
                    && currentSpellId.equalsIgnoreCase(pair.getString("second"))) return true;
        }
        return false;
    }

    private double chainFinisherPercent(final Player player, final String currentSpellId, final long now) {
        if (!configManager.getBoolean("spells.combos.enabled", true)) return 0.0D;
        final UUID id = player.getUniqueId();
        final String prev1 = lastCastSpell.get(id);
        final String prev2 = secondLastCastSpell.get(id);
        if (prev1 == null || prev2 == null || prev2.isEmpty()) return 0.0D;
        final long windowMs = Math.max(1L,
                configManager.getLong("spells.combos.window-seconds", 4L)) * 1000L;
        final long t1 = lastCastTime.getOrDefault(id, 0L);
        final long t2 = secondLastCastTime.getOrDefault(id, 0L);
        if (now - t1 > windowMs || t1 - t2 > windowMs) return 0.0D;
        final ConfigurationSection chains = configManager.getConfiguration() == null
                ? null : configManager.getConfiguration().getConfigurationSection("spells.combos.chains");
        if (chains == null) return 0.0D;
        for (final String key : chains.getKeys(false)) {
            final ConfigurationSection chain = chains.getConfigurationSection(key);
            if (chain == null) continue;
            final List<String> steps = chain.getStringList("steps");
            if (steps.size() == 3 && prev2.equalsIgnoreCase(steps.get(0))
                    && prev1.equalsIgnoreCase(steps.get(1))
                    && currentSpellId.equalsIgnoreCase(steps.get(2))) {
                return Math.max(0.0D, chain.getDouble("finisher-power-bonus-percent",
                        configManager.getDouble("spells.combos.finisher-power-bonus-percent", 25.0D)));
            }
        }
        return 0.0D;
    }

    private void sendComboWindowHint(final Player player, final String justCastId) {
        if (!configManager.getBoolean("spells.combos.enabled", true)) return;
        final String nextId = nextComboStep(player, justCastId);
        final Spell next = nextId == null ? null : spellRegistry.getById(nextId);
        if (next == null) return;
        final long windowMillis = Math.max(1L,
                configManager.getLong("spells.combos.window-seconds", 4L)) * 1000L;
        final long startedAt = System.currentTimeMillis();
        hintStartedAt.put(player.getUniqueId(), startedAt);
        renderComboHint(player, next.getName(), startedAt, windowMillis);
    }

    private void renderComboHint(final Player player,
                                 final String nextName,
                                 final long startedAt,
                                 final long windowMillis) {
        final Long current = hintStartedAt.get(player.getUniqueId());
        if (current == null || current.longValue() != startedAt) return;
        final long elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed >= windowMillis) {
            hintStartedAt.remove(player.getUniqueId(), current);
            return;
        }
        final int segments = 8;
        final int filled = segments - (int) Math.min(segments, elapsed * segments / windowMillis);
        player.sendActionBar(messageManager.getMessage(
                "catalyst.combo-window",
                "<gray>⏳ <gold>{bar}</gold><dark_gray>{empty}</dark_gray> Kombó: <gold>{next}</gold></gray>",
                Map.of("bar", "▰".repeat(filled), "empty", "▱".repeat(segments - filled), "next", nextName)));
        player.getScheduler().runDelayed(plugin,
                task -> renderComboHint(player, nextName, startedAt, windowMillis), null, 10L);
    }

    private String nextComboStep(final Player player, final String justCastId) {
        final var configuration = configManager.getConfiguration();
        if (configuration == null) return null;
        final ConfigurationSection chains = configuration.getConfigurationSection("spells.combos.chains");
        if (chains != null) {
            final String previous = lastCastSpell.get(player.getUniqueId());
            for (final String key : chains.getKeys(false)) {
                final ConfigurationSection chain = chains.getConfigurationSection(key);
                if (chain == null) continue;
                final List<String> steps = chain.getStringList("steps");
                if (steps.size() != 3) continue;
                if (previous != null && previous.equalsIgnoreCase(steps.get(0))
                        && justCastId.equalsIgnoreCase(steps.get(1))) return steps.get(2);
                if (justCastId.equalsIgnoreCase(steps.get(0))) return steps.get(1);
            }
        }
        final ConfigurationSection pairs = configuration.getConfigurationSection("spells.combos.pairs");
        if (pairs != null) {
            for (final String key : pairs.getKeys(false)) {
                final ConfigurationSection pair = pairs.getConfigurationSection(key);
                if (pair != null && justCastId.equalsIgnoreCase(pair.getString("first"))) {
                    return pair.getString("second");
                }
            }
        }
        return null;
    }

    private long comboRefundMillis(final Player player, final Spell spell) {
        final double percent = Math.max(0.0D, Math.min(80.0D,
                configManager.getDouble("spells.combos.bonus-cooldown-refund-percent", 40.0D)));
        final long effectiveCooldownMs = effectiveCooldownMillis(player, spell);
        final long refund = (long) (effectiveCooldownMs * percent / 100.0D);
        final long floorMs = Math.max(1000L, (long) (effectiveCooldownMs * 0.15D));
        return Math.min(refund, Math.max(0L, effectiveCooldownMs - floorMs));
    }

    private void playCastFlourish(final Player player, final boolean combo) {
        player.getWorld().spawnParticle(combo ? Particle.TOTEM_OF_UNDYING : Particle.ENCHANT,
                player.getLocation().add(0.0D, 1.1D, 0.0D), combo ? 14 : 10,
                0.4D, 0.5D, 0.4D, combo ? 0.3D : 0.15D);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                0.5F, combo ? 1.6F : 1.2F);
    }

    private int displayedCost(final Spell spell) {
        return resourceManager.usesResource(spell) ? resourceManager.costOf(spell) : spell.getCostAmount();
    }

    private String resolveResourceName(final Player player, final Spell spell) {
        if (resourceManager.usesResource(spell)) return resourceManager.resourceName(player);
        return switch (spell.getCostType()) {
            case HUNGER -> messageManager.get("system.resources.hunger", "éhség");
            case XP -> messageManager.get("system.resources.xp", "XP");
            case HEALTH -> messageManager.get("system.resources.health", "élet");
        };
    }

    private Spell resolveSelectedSpell(final Player player, final List<String> active) {
        String selected = selectedSpellId(player.getUniqueId());
        if (!active.contains(selected)) {
            selected = active.getFirst();
            persistSelectedSpell(player, selected);
        }
        return spellRegistry.getById(selected);
    }

    private long getRemainingCooldown(final Player player, final Spell spell, final long now) {
        final Long lastCast = getLastCast(player, spell);
        if (lastCast == null) return 0L;
        final long delayMs = Math.max(0, spell.getCooldownDelay()) * 1000L;
        return Math.max(0L, lastCast + delayMs + effectiveCooldownMillis(player, spell) - now);
    }

    private void putCooldown(final Player player, final Spell spell, final long timestamp) {
        final UUID playerId = player.getUniqueId();
        spellCooldowns.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .put(spell.getId(), timestamp);
        if (!isPersistentCooldown(spell)) return;
        spellbookStateStore.recordLastCast(playerId, spell.getId(), timestamp)
                .exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile spell cooldown commit failed for "
                            + playerId + '/' + spell.getId() + ": " + rootMessage(failure)
                            + "; runtime cooldown remains fail-closed until logout.");
                    return null;
                });
    }

    private List<String> resolveUnlockedSpellIds(final Player player) {
        return jobManager.getUnlockedSpellIds(player).stream()
                .map(id -> id.toLowerCase(Locale.ROOT))
                .filter(spellId -> spellRegistry.getById(spellId) != null)
                .toList();
    }

    private List<String> resolveActiveSpellIds(final Player player) {
        final List<String> unlocked = resolveUnlockedSpellIds(player);
        final ClassCastHook hook = classHook(player);
        if (hook == null) return unlocked;
        final List<String> active = hook.activeKit().resolve(
                player, unlocked, spellFavoritesManager.favorites(player));
        if (active == null || active.isEmpty()) return List.of();
        final int limit = activeKitLimit(player);
        final LinkedHashSet<String> valid = new LinkedHashSet<>();
        for (final String raw : active) {
            if (raw == null) continue;
            final String id = raw.toLowerCase(Locale.ROOT);
            if (unlocked.contains(id) && spellRegistry.getById(id) != null) valid.add(id);
            if (valid.size() >= limit) break;
        }
        return List.copyOf(valid);
    }

    public void openSpellbook(final Player player) { openSpellbook(player, 0, false); }
    public void openSpellbook(final Player player, final int page) { openSpellbook(player, page, false); }
    public void openSpellbook(final Player player, final int page, final boolean onlyUnlocked) {
        SpellbookGUI.open(player, this, jobManager, specializationManager, spellRegistry,
                masteryManager, configManager, messageManager, resourceManager,
                spellFavoritesManager, page, onlyUnlocked);
    }

    public List<String> getUnlockedSpellIds(final Player player) { return resolveUnlockedSpellIds(player); }
    public List<String> getActiveSpellIds(final Player player) { return resolveActiveSpellIds(player); }

    public int activeKitLimit(final Player player) {
        final JobType job = player == null ? null : jobManager.getPrimaryJob(player);
        if (job == null || !GameplayV2ClassPolicy.isEnabled(job.getId())) return Integer.MAX_VALUE;
        return Math.max(1, Math.min(7,
                configManager.getInt("classes." + job.getId() + ".active-kit.maximum", 7)));
    }

    public String getSelectedSpellId(final Player player) {
        final List<String> active = resolveActiveSpellIds(player);
        if (active.isEmpty()) return null;
        String selected = selectedSpellId(player.getUniqueId());
        if (!active.contains(selected)) {
            selected = active.getFirst();
            persistSelectedSpell(player, selected);
        }
        return selected;
    }

    public boolean selectSpell(final Player player, final String spellId) {
        if (player == null || spellId == null) return false;
        final String normalized = spellId.toLowerCase(Locale.ROOT);
        if (!resolveActiveSpellIds(player).contains(normalized)) {
            player.sendActionBar(messageManager.getMessage("catalyst.not-in-active-kit",
                    "<red>Ez a spell nincs az aktív készletedben. Shift-kattal jelöld kedvencnek a spellbookban.</red>"));
            return false;
        }
        persistSelectedSpell(player, normalized);
        return true;
    }

    public long getRemainingCooldownMs(final Player player, final Spell spell) {
        return getRemainingCooldown(player, spell, System.currentTimeMillis());
    }

    /** Mastery-vel skálázott teljes cooldown-hossz — kit-projekcióhoz (sáv-arányhoz). */
    public long getEffectiveCooldownMs(final Player player, final Spell spell) {
        return effectiveCooldownMillis(player, spell);
    }

    /** A vanilla spellbook-GUI-val azonos, rendezett kaszt+spec spell-lista — projekcióhoz. */
    public List<SpellbookGUI.Entry> spellbookEntries(final Player player) {
        return SpellbookGUI.collectEntries(player, jobManager, specializationManager,
                spellRegistry, configManager);
    }

    public Set<String> getFavoriteSpellIds(final Player player) {
        return spellFavoritesManager.favorites(player);
    }

    /** Kedvenc-váltás az aktív-kit limitre cappelve — a vanilla spellbook shift-katt párja. */
    public java.util.concurrent.CompletionStage<SpellFavoritesManager.ToggleResult> toggleFavorite(
            final Player player, final String spellId) {
        return spellFavoritesManager.toggleCapped(player, spellId, activeKitLimit(player));
    }

    /** Megjelenítési költség-szöveg a resource-rendszer figyelembevételével; üres, ha ingyenes. */
    public String getDisplayedCostText(final Player player, final Spell spell) {
        final int cost = displayedCost(spell);
        return cost <= 0 ? "" : cost + " " + resolveResourceName(player, spell);
    }

    public int getMasteryRank(final Player player, final String spellId) {
        return masteryManager.getRank(player, spellId);
    }

    public Map<String, Long> activeCooldowns(final UUID playerId) {
        final Map<String, Long> bySpell = spellCooldowns.get(playerId);
        if (bySpell == null || bySpell.isEmpty()) return Map.of();
        final long now = System.currentTimeMillis();
        final Player online = plugin.getServer().getPlayer(playerId);
        final Map<String, Long> result = new java.util.LinkedHashMap<>();
        for (final Map.Entry<String, Long> entry : bySpell.entrySet()) {
            final Spell spell = spellRegistry.getById(entry.getKey());
            if (spell == null) continue;
            final long delayMs = Math.max(0, spell.getCooldownDelay()) * 1000L;
            final long remaining = entry.getValue() + delayMs
                    + effectiveCooldownMillis(online, spell) - now;
            if (remaining > 0L) result.put(entry.getKey(), remaining);
        }
        return Map.copyOf(result);
    }

    public boolean hasComboBoost(final UUID playerId) {
        if (playerId == null) return false;
        final Long until = comboBoostUntil.get(playerId);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            comboBoostUntil.remove(playerId);
            return false;
        }
        return true;
    }

    @Override
    public void cleanup(final UUID playerId) {
        if (playerId == null) return;
        spellCooldowns.remove(playerId);
        selectedSpellProjection.remove(playerId);
        cycleDebounce.remove(playerId);
        castDebounce.remove(playerId);
        lastCastSpell.remove(playerId);
        lastCastTime.remove(playerId);
        secondLastCastSpell.remove(playerId);
        secondLastCastTime.remove(playerId);
        comboBoostUntil.remove(playerId);
        hintStartedAt.remove(playerId);
    }

    public void clearPlayerState(final UUID playerId) { cleanup(playerId); }

    public void resetCooldowns(final Player player) {
        if (player == null) return;
        final UUID playerId = player.getUniqueId();
        spellCooldowns.remove(playerId);
        spellbookStateStore.clearCooldowns(playerId).exceptionally(failure -> {
            plugin.getLogger().severe("PlayerProfile spell cooldown reset failed for "
                    + playerId + ": " + rootMessage(failure));
            return null;
        });
    }

    public void resetAllSpellState(final Player player) {
        if (player == null) return;
        final UUID playerId = player.getUniqueId();
        spellCooldowns.remove(playerId);
        selectedSpellProjection.remove(playerId);
        spellbookStateStore.reset(playerId).exceptionally(failure -> {
            plugin.getLogger().severe("PlayerProfile spell state reset failed for "
                    + playerId + ": " + rootMessage(failure));
            return null;
        });
    }

    private Long getLastCast(final Player player, final Spell spell) {
        final Map<String, Long> runtime = spellCooldowns.get(player.getUniqueId());
        if (runtime != null && runtime.containsKey(spell.getId())) return runtime.get(spell.getId());
        if (!isPersistentCooldown(spell)) return null;
        try {
            final long persisted = spellbookStateStore.lastCast(player.getUniqueId(), spell.getId());
            if (persisted <= 0L) return null;
            spellCooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                    .put(spell.getId(), persisted);
            return persisted;
        } catch (final PlayerProfileAuthority.ProfileNotReadyException notReady) {
            return null;
        }
    }

    private boolean isPersistentCooldown(final Spell spell) { return spell.getCooldown() >= 60; }

    private String selectedSpellId(final UUID playerId) {
        final String projected = selectedSpellProjection.get(playerId);
        if (projected != null) return projected;
        try {
            final String selected = spellbookStateStore.selectedSpell(playerId);
            if (!selected.isBlank()) selectedSpellProjection.put(playerId, selected);
            return selected;
        } catch (final PlayerProfileAuthority.ProfileNotReadyException notReady) {
            return "";
        }
    }

    private void persistSelectedSpell(final Player player, final String spellId) {
        final UUID playerId = player.getUniqueId();
        selectedSpellProjection.put(playerId, spellId);
        spellbookStateStore.select(playerId, spellId).whenComplete((selected, failure) -> {
            if (failure == null) return;
            selectedSpellProjection.remove(playerId, spellId);
            plugin.getLogger().severe("PlayerProfile selected-spell commit failed for "
                    + playerId + '/' + spellId + ": " + rootMessage(failure));
            player.getScheduler().run(plugin, task -> player.sendActionBar(
                    messageManager.getMessage("catalyst.selection-persistence-failed",
                            "<red>A spellválasztás mentése meghiúsult; válassz újra.</red>")), null);
        });
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
