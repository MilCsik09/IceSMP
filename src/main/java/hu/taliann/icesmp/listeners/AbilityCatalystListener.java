package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.archer.ArcherGameplayService;
import hu.taliann.icesmp.classspec.application.GameplayV2ClassPolicy;
import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.evoker.EvokerGameplayService;
import hu.taliann.icesmp.monk.MonkGameplayService;
import hu.taliann.icesmp.paladin.PaladinGameplayService;
import hu.taliann.icesmp.shaman.ShamanGameplayService;
import hu.taliann.icesmp.gui.SpellbookGUI;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellFavoritesManager;
import hu.taliann.icesmp.managers.SpellMasteryManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileSpellbookStateStore;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.warrior.WarriorGameplayService;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lélekkapocs/catalyst spellcasting pipeline.
 * Selected spell and durable long cooldowns remain PlayerProfile-backed; runtime maps are projections.
 */
public final class AbilityCatalystListener implements Listener, PlayerStateCleanup {

    private final JobManager jobManager;
    private final SpellRegistry spellRegistry;
    private final CatalystItemFactory catalystItemFactory;
    private final ConfigManager configManager;
    private final SpellMasteryManager masteryManager;
    private final SpecializationManager specializationManager;
    private final hu.taliann.icesmp.managers.ResourceManager resourceManager;
    private final hu.taliann.icesmp.managers.TalentManager talentManager;
    private final MessageManager messageManager;
    private final SpellFavoritesManager spellFavoritesManager;
    private final PlayerProfileSpellbookStateStore spellbookStateStore =
            new PlayerProfileSpellbookStateStore();

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

    private volatile hu.taliann.icesmp.managers.StatsManager statsManager;
    private volatile hu.taliann.icesmp.managers.ItemRarityService itemRarityServiceRef;
    private volatile hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway profileGateway;
    private volatile WarriorGameplayService warriorGameplayService;
    private volatile EvokerGameplayService evokerGameplayService;
    private volatile ArcherGameplayService archerGameplayService;
    private volatile ShamanGameplayService shamanGameplayService;
    private volatile MonkGameplayService monkGameplayService;
    private volatile PaladinGameplayService paladinGameplayService;
    private final JavaPlugin plugin;

    public AbilityCatalystListener(final JavaPlugin plugin,
                                   final JobManager jobManager,
                                   final SpellRegistry spellRegistry,
                                   final CatalystItemFactory catalystItemFactory,
                                   final ConfigManager configManager,
                                   final SpellMasteryManager masteryManager,
                                   final SpecializationManager specializationManager,
                                   final hu.taliann.icesmp.managers.ResourceManager resourceManager,
                                   final hu.taliann.icesmp.managers.TalentManager talentManager,
                                   final MessageManager messageManager,
                                   final SpellFavoritesManager spellFavoritesManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.spellRegistry = spellRegistry;
        this.catalystItemFactory = catalystItemFactory;
        this.configManager = configManager;
        this.masteryManager = masteryManager;
        this.specializationManager = specializationManager;
        this.resourceManager = resourceManager;
        this.talentManager = talentManager;
        this.messageManager = messageManager;
        this.spellFavoritesManager = spellFavoritesManager;
    }

    public void setProfileGateway(
            final hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway profileGateway) {
        this.profileGateway = java.util.Objects.requireNonNull(profileGateway, "profileGateway");
    }

    public void setWarriorGameplayService(final WarriorGameplayService service) {
        warriorGameplayService = java.util.Objects.requireNonNull(service, "service");
    }

    public void setEvokerGameplayService(final EvokerGameplayService service) {
        evokerGameplayService = java.util.Objects.requireNonNull(service, "service");
    }

    public void setArcherGameplayService(final ArcherGameplayService service) {
        archerGameplayService = java.util.Objects.requireNonNull(service, "service");
    }

    public void setShamanGameplayService(final ShamanGameplayService service) {
        shamanGameplayService = java.util.Objects.requireNonNull(service, "service");
    }

    public void setMonkGameplayService(final MonkGameplayService service) {
        monkGameplayService = java.util.Objects.requireNonNull(service, "service");
    }

    public void setPaladinGameplayService(final PaladinGameplayService service) {
        paladinGameplayService = java.util.Objects.requireNonNull(service, "service");
    }

    public void setItemRarityService(
            final hu.taliann.icesmp.managers.ItemRarityService itemRarityService) {
        this.itemRarityServiceRef = itemRarityService;
    }

    public void setStatsManager(final hu.taliann.icesmp.managers.StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    private boolean isUsableCatalyst(final Player player, final ItemStack item) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (catalystItemFactory.isCatalyst(item)) {
            return catalystItemFactory.isUsableBy(item, player.getUniqueId(), job);
        }
        // Gameplay-v2 classnál a személyes Lélekkapocs a kötelező spellbook/fókusz:
        // se fegyver, se generikus melee-catalyst nem kerülheti meg.
        if (job != null && GameplayV2ClassPolicy.isEnabled(job.getId())) return false;
        if (item == null || !configManager.getBoolean("spells.melee-catalyst.enabled", true)) {
            return false;
        }
        if (!configManager.getStringList("spells.melee-catalyst.materials")
                .contains(item.getType().name())) {
            return false;
        }
        return job != null && configManager.getStringList("spells.melee-catalyst.classes")
                .contains(job.getId());
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
        final Player player = event.getPlayer();
        if (!isUsableCatalyst(player, player.getInventory().getItemInMainHand())) return;
        final Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (!profileRuntimeReady(player)) return;
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
        final boolean favoritesOnly = !favorites.isEmpty()
                && cycle.stream().allMatch(favorites::contains);
        final String currentId = selectedSpellId(player.getUniqueId());
        final int cyclePos = currentId.isBlank() ? -1 : cycle.indexOf(currentId);
        final int nextCyclePos = Math.floorMod(cyclePos + step, cycle.size());
        final String selectedId = cycle.get(nextCyclePos);
        persistSelectedSpell(player, selectedId);

        final Spell selected = spellRegistry.getById(selectedId);
        if (selected == null) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.current-spell-unknown",
                    "<gray>Aktuális képesség: Ismeretlen</gray>"));
            return;
        }
        final int rank = masteryManager.getRank(player, selected.getId());
        final String mastery = rank > 0 ? " <aqua>★" + rank + "</aqua>" : "";
        player.sendActionBar(messageManager.getMessage(
                "catalyst.current-spell",
                "<gray>[{position}] <gold>{spell}</gold>{mastery} <dark_gray>({cost} {resource})</dark_gray> <dark_gray>— Shift+jobb katt: spellbook</dark_gray></gray>",
                Map.of(
                        "spell", selected.getName(),
                        "mastery", mastery,
                        "cost", String.valueOf(displayedCost(selected)),
                        "resource", resolveResourceName(player, selected),
                        "position", (favoritesOnly ? "★" : "")
                                + (nextCyclePos + 1) + "/" + cycle.size())));
        catalystItemFactory.playCycleSound(player, jobManager.getPrimaryJob(player));
    }

    private void castSelectedSpell(final Player player) {
        final List<String> active = resolveActiveSpellIds(player);
        if (active.isEmpty()) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.no-unlocked",
                    "<red>Még nincs használható aktív képességed.</red>"));
            return;
        }
        final Spell selected = resolveSelectedSpell(player, active);
        if (selected == null) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.invalid-selection",
                    "<red>A kiválasztott képesség nem érhető el az aktív specializationben.</red>"));
            return;
        }
        final long now = System.currentTimeMillis();
        final long remainingMs = getRemainingCooldown(player, selected, now);
        if (remainingMs > 0L) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.cooldown",
                    "<red>Várj még {seconds} mp-et!</red>",
                    Map.of("seconds", String.valueOf((long) Math.ceil(remainingMs / 1000.0D)))));
            return;
        }
        if (!selected.canCast(player)) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.not-ready", "<red>Most nem tudod használni ezt a képességet.</red>"));
            return;
        }
        final WarriorGameplayService warrior = warriorGameplayService;
        if (warrior != null && !warrior.beforeCast(player, selected)) return;
        final EvokerGameplayService evoker = evokerGameplayService;
        if (evoker != null && !evoker.beforeCast(player, selected)) return;
        final ArcherGameplayService archer = archerGameplayService;
        if (archer != null && !archer.beforeCast(player, selected)) return;
        final ShamanGameplayService shaman = shamanGameplayService;
        if (shaman != null && !shaman.beforeCast(player, selected)) return;
        final MonkGameplayService monk = monkGameplayService;
        if (monk != null && !monk.beforeCast(player, selected)) return;
        final PaladinGameplayService paladin = paladinGameplayService;
        if (paladin != null && !paladin.beforeCast(player, selected)) return;

        final boolean useResource = resourceManager.usesResource(selected);
        final boolean canAfford = useResource
                ? resourceManager.canAfford(player, selected)
                : selected.hasRequiredCost(player);
        if (!canAfford) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.no-cost",
                    "<red>Nincs elég {resource}! Szükséges: {amount}</red>",
                    Map.of("resource", resolveResourceName(player, selected),
                            "amount", String.valueOf(displayedCost(selected)))));
            return;
        }

        final double preCastHealth = player.getHealth();
        final int spentAmount = displayedCost(selected);
        if (useResource) resourceManager.consume(player, selected);
        else selected.consumeCost(player);

        final double chainBonusPercent = chainFinisherPercent(player, selected.getId(), now);
        final double classBonusPercent = (evoker == null
                ? 0.0D : evoker.castPowerBonusPercent(player, selected))
                + (shaman == null ? 0.0D : shaman.castPowerBonusPercent(player, selected))
                + (monk == null ? 0.0D : monk.castPowerBonusPercent(player, selected))
                + (paladin == null ? 0.0D : paladin.castPowerBonusPercent(player, selected));
        final double powerCap = Math.max(1.0D,
                configManager.getDouble("spells.total-power-cap", 1.75D));
        final double power = Math.min(powerCap,
                masteryManager.getPowerMultiplier(player, selected.getId())
                        * dynamicPowerMultiplier(player)
                        * (1.0D + chainBonusPercent / 100.0D)
                        * (1.0D + classBonusPercent / 100.0D));
        if (!selected.executeSpell(player, power)) {
            if (useResource) resourceManager.refund(player, selected);
            else if (selected.getCostType() == hu.taliann.icesmp.spells.SpellCostType.HEALTH) {
                player.setHealth(preCastHealth);
            } else selected.refundCost(player);
            return;
        }

        if (warrior != null) {
            warrior.afterCast(player, selected, useResource, useResource ? spentAmount : 0);
        }
        if (evoker != null) {
            evoker.afterCast(player, selected, useResource, useResource ? spentAmount : 0);
        }
        if (archer != null) {
            archer.afterCast(player, selected, useResource, useResource ? spentAmount : 0);
        }
        if (shaman != null) {
            shaman.afterCast(player, selected, useResource, useResource ? spentAmount : 0);
        }
        if (monk != null) {
            monk.afterCast(player, selected, useResource, useResource ? spentAmount : 0);
        }
        if (paladin != null) {
            paladin.afterCast(player, selected, useResource, useResource ? spentAmount : 0);
        }

        final boolean chainFinisher = chainBonusPercent > 0.0D;
        final boolean combo = chainFinisher || isComboMatch(player, selected.getId(), now);
        hintStartedAt.remove(player.getUniqueId());
        if (combo) comboBoostUntil.put(player.getUniqueId(), now + 3000L);
        final long refundMillis = combo ? comboRefundMillis(player, selected) : 0L;
        putCooldown(player, selected, now - refundMillis);
        applyCooldownOverlay(player, selected, refundMillis);
        playCastFlourish(player, combo);
        if (chainFinisher) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.combo-finisher",
                    "<gold>⚡ Kombó-lánc befejező! +{bonus}% erő és gyorsabb felépülés.</gold>",
                    Map.of("bonus", String.valueOf((int) Math.round(chainBonusPercent)))));
        } else if (combo) {
            final String nextInChain = nextComboStep(player, selected.getId());
            final Spell nextSpell = nextInChain == null ? null : spellRegistry.getById(nextInChain);
            if (nextSpell != null) {
                player.sendActionBar(messageManager.getMessage(
                        "catalyst.combo-next",
                        "<gold>⚡ Kombó! Köv. a láncban: {next}</gold>",
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
    }

    private boolean profileRuntimeReady(final Player player) {
        final var gateway = profileGateway;
        if (gateway != null && gateway.isSessionReady(player.getUniqueId())) return true;
        player.sendActionBar(messageManager.getMessage(
                "profile-v2.runtime-blocked",
                "<red>A kaszt/specializáció profilod biztonsági ellenőrzést igényel. /spec info</red>"));
        return false;
    }

    /**
     * Rebuilds/refreshes the one physical personal Lélekkapocs mirror. Duplicate copies are removed;
     * missing artifact is regenerated only into a free inventory slot, never dropped to the world.
     */
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
        if (player == null) return false;
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) return false;
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (catalystItemFactory.isPersonalCopyFor(stack, player.getUniqueId(), job)) return true;
        }
        return false;
    }

    private void applyCooldownOverlay(final Player player,
                                      final Spell spell,
                                      final long comboRefundMs) {
        if (!configManager.getBoolean("spells.cooldown-overlay.enabled", true)) return;
        final long cooldownTicks = spell.getCooldown() * 20L - comboRefundMs / 50L;
        if (cooldownTicks <= 0L) return;
        final ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) return;
        player.setCooldown(held, (int) Math.min(Integer.MAX_VALUE, cooldownTicks));
    }

    private boolean isComboMatch(final Player player,
                                 final String currentSpellId,
                                 final long now) {
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
            if (pair != null
                    && previous.equalsIgnoreCase(pair.getString("first"))
                    && currentSpellId.equalsIgnoreCase(pair.getString("second"))) {
                return true;
            }
        }
        return false;
    }

    private double chainFinisherPercent(final Player player,
                                        final String currentSpellId,
                                        final long now) {
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
            if (steps.size() == 3
                    && prev2.equalsIgnoreCase(steps.get(0))
                    && prev1.equalsIgnoreCase(steps.get(1))
                    && currentSpellId.equalsIgnoreCase(steps.get(2))) {
                return Math.max(0.0D, chain.getDouble(
                        "finisher-power-bonus-percent",
                        configManager.getDouble(
                                "spells.combos.finisher-power-bonus-percent", 25.0D)));
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
        final int filled = segments
                - (int) Math.min(segments, elapsed * segments / windowMillis);
        player.sendActionBar(messageManager.getMessage(
                "catalyst.combo-window",
                "<gray>⏳ <gold>{bar}</gold><dark_gray>{empty}</dark_gray> Kombó: <gold>{next}</gold></gray>",
                Map.of("bar", "▰".repeat(filled),
                        "empty", "▱".repeat(segments - filled),
                        "next", nextName)));
        player.getScheduler().runDelayed(plugin,
                task -> renderComboHint(player, nextName, startedAt, windowMillis), null, 10L);
    }

    private String nextComboStep(final Player player, final String justCastId) {
        final var configuration = configManager.getConfiguration();
        if (configuration == null) return null;
        final ConfigurationSection chains =
                configuration.getConfigurationSection("spells.combos.chains");
        if (chains != null) {
            final String previous = lastCastSpell.get(player.getUniqueId());
            for (final String key : chains.getKeys(false)) {
                final ConfigurationSection chain = chains.getConfigurationSection(key);
                if (chain == null) continue;
                final List<String> steps = chain.getStringList("steps");
                if (steps.size() != 3) continue;
                if (previous != null
                        && previous.equalsIgnoreCase(steps.get(0))
                        && justCastId.equalsIgnoreCase(steps.get(1))) {
                    return steps.get(2);
                }
                if (justCastId.equalsIgnoreCase(steps.get(0))) return steps.get(1);
            }
        }
        final ConfigurationSection pairs =
                configuration.getConfigurationSection("spells.combos.pairs");
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
                configManager.getDouble(
                        "spells.combos.bonus-cooldown-refund-percent", 40.0D)));
        final long baseCooldownMs = Math.max(0L, spell.getCooldown()) * 1000L;
        final long refund = (long) (baseCooldownMs * percent / 100.0D);
        final long effectiveCooldownMs = (long) (baseCooldownMs
                * masteryManager.getCooldownMultiplier(player, spell.getId()));
        final long floorMs = Math.max(1000L, (long) (baseCooldownMs * 0.15D));
        return Math.min(refund, Math.max(0L, effectiveCooldownMs - floorMs));
    }

    private void playCastFlourish(final Player player, final boolean combo) {
        player.getWorld().spawnParticle(
                combo ? Particle.TOTEM_OF_UNDYING : Particle.ENCHANT,
                player.getLocation().add(0.0D, 1.1D, 0.0D),
                combo ? 14 : 10, 0.4D, 0.5D, 0.4D,
                combo ? 0.3D : 0.15D);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                0.5F, combo ? 1.6F : 1.2F);
    }

    private int displayedCost(final Spell spell) {
        return resourceManager.usesResource(spell)
                ? resourceManager.costOf(spell) : spell.getCostAmount();
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

    private long getRemainingCooldown(final Player player,
                                      final Spell spell,
                                      final long now) {
        final Long lastCast = getLastCast(player, spell);
        if (lastCast == null) return 0L;
        final long cooldownMs = (long) (Math.max(0, spell.getCooldown()) * 1000L
                * masteryManager.getCooldownMultiplier(player, spell.getId()));
        final long delayMs = Math.max(0, spell.getCooldownDelay()) * 1000L;
        return Math.max(0L, lastCast + delayMs + cooldownMs - now);
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

    /** Full currently-authorized library (base + ACTIVE spec provenance). */
    private List<String> resolveUnlockedSpellIds(final Player player) {
        return jobManager.getUnlockedSpellIds(player).stream()
                .map(id -> id.toLowerCase(Locale.ROOT))
                .filter(spellId -> spellRegistry.getById(spellId) != null)
                .toList();
    }

    /** Actual fast-combat cycle/cast set; gameplay-v2 classes cap it via favorites/default kit. */
    private List<String> resolveActiveSpellIds(final Player player) {
        List<String> active = resolveUnlockedSpellIds(player);
        final WarriorGameplayService warrior = warriorGameplayService;
        if (warrior != null) {
            active = warrior.activeSpellIds(player, active, spellFavoritesManager.favorites(player));
        }
        final EvokerGameplayService evoker = evokerGameplayService;
        if (evoker != null) {
            active = evoker.activeSpellIds(player, active, spellFavoritesManager.favorites(player));
        }
        final ArcherGameplayService archer = archerGameplayService;
        if (archer != null) {
            active = archer.activeSpellIds(player, active, spellFavoritesManager.favorites(player));
        }
        final ShamanGameplayService shaman = shamanGameplayService;
        if (shaman != null) {
            active = shaman.activeSpellIds(player, active, spellFavoritesManager.favorites(player));
        }
        final MonkGameplayService monk = monkGameplayService;
        if (monk != null) {
            active = monk.activeSpellIds(player, active, spellFavoritesManager.favorites(player));
        }
        final PaladinGameplayService paladin = paladinGameplayService;
        if (paladin != null) {
            active = paladin.activeSpellIds(player, active, spellFavoritesManager.favorites(player));
        }
        return active;
    }

    public void openSpellbook(final Player player) {
        openSpellbook(player, 0, false);
    }

    public void openSpellbook(final Player player, final int page) {
        openSpellbook(player, page, false);
    }

    public void openSpellbook(final Player player,
                              final int page,
                              final boolean onlyUnlocked) {
        SpellbookGUI.open(player, this, jobManager, specializationManager, spellRegistry,
                masteryManager, configManager, messageManager, resourceManager,
                spellFavoritesManager, page, onlyUnlocked);
    }

    public List<String> getUnlockedSpellIds(final Player player) {
        return resolveUnlockedSpellIds(player);
    }

    public List<String> getActiveSpellIds(final Player player) {
        return resolveActiveSpellIds(player);
    }

    /** Favorite/active-kit cap for gameplay-v2 classes; other classes stay uncapped. */
    public int activeKitLimit(final Player player) {
        final JobType job = player == null ? null : jobManager.getPrimaryJob(player);
        if (job == null || !GameplayV2ClassPolicy.isEnabled(job.getId())) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, Math.min(7, configManager.getInt(
                "classes." + job.getId() + ".active-kit.maximum", 7)));
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

    public int getMasteryRank(final Player player, final String spellId) {
        return masteryManager.getRank(player, spellId);
    }

    public Map<String, Long> activeCooldowns(final UUID playerId) {
        final Map<String, Long> bySpell = spellCooldowns.get(playerId);
        if (bySpell == null || bySpell.isEmpty()) return Map.of();
        final long now = System.currentTimeMillis();
        final Map<String, Long> result = new java.util.LinkedHashMap<>();
        for (final Map.Entry<String, Long> entry : bySpell.entrySet()) {
            final Spell spell = spellRegistry.getById(entry.getKey());
            if (spell == null) continue;
            final long cooldownMs = Math.max(0, spell.getCooldown()) * 1000L;
            final long delayMs = Math.max(0, spell.getCooldownDelay()) * 1000L;
            final long remaining = entry.getValue() + delayMs + cooldownMs - now;
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

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }

    public void resetCooldowns(final Player player) {
        if (player == null) return;
        final UUID playerId = player.getUniqueId();
        spellCooldowns.remove(playerId);
        spellbookStateStore.clearCooldowns(playerId)
                .exceptionally(failure -> {
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
        spellbookStateStore.reset(playerId)
                .exceptionally(failure -> {
                    plugin.getLogger().severe("PlayerProfile spell state reset failed for "
                            + playerId + ": " + rootMessage(failure));
                    return null;
                });
    }

    private Long getLastCast(final Player player, final Spell spell) {
        final Map<String, Long> runtime = spellCooldowns.get(player.getUniqueId());
        if (runtime != null && runtime.containsKey(spell.getId())) {
            return runtime.get(spell.getId());
        }
        if (!isPersistentCooldown(spell)) return null;
        try {
            final long persisted = spellbookStateStore.lastCast(
                    player.getUniqueId(), spell.getId());
            if (persisted <= 0L) return null;
            spellCooldowns.computeIfAbsent(player.getUniqueId(),
                            ignored -> new ConcurrentHashMap<>())
                    .put(spell.getId(), persisted);
            return persisted;
        } catch (final PlayerProfileAuthority.ProfileNotReadyException notReady) {
            return null;
        }
    }

    private boolean isPersistentCooldown(final Spell spell) {
        return spell.getCooldown() >= 60;
    }

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
        spellbookStateStore.select(playerId, spellId)
                .whenComplete((selected, failure) -> {
                    if (failure == null) return;
                    selectedSpellProjection.remove(playerId, spellId);
                    plugin.getLogger().severe("PlayerProfile selected-spell commit failed for "
                            + playerId + '/' + spellId + ": " + rootMessage(failure));
                    player.getScheduler().run(plugin, task -> player.sendActionBar(
                            messageManager.getMessage(
                                    "catalyst.selection-persistence-failed",
                                    "<red>A spellválasztás mentése meghiúsult; válassz újra.</red>")), null);
                });
    }

    private static String rootMessage(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName() : current.getMessage();
    }
}
