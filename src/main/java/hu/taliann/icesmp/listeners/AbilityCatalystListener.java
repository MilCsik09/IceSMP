package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.gui.SpellbookGUI;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.managers.SpellFavoritesManager;
import hu.taliann.icesmp.managers.SpellMasteryManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interaction handler for the class-themed Ability Catalyst item:
 * right click casts the selected spell, sneak + arm swing cycles spells with a
 * class-specific sound. Owns the cast pipeline (cost check, cooldowns with PDC
 * persistence for cooldowns >= 60s) and the per-session spell state cleanup.
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
    private final NamespacedKey selectedSpellIndexKey;
    private final Map<String, NamespacedKey> longCooldownKeys = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> spellCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cycleDebounce = new ConcurrentHashMap<>();
    private final Map<UUID, Long> castDebounce = new ConcurrentHashMap<>();
    // Combo tracking: the last spell each player cast and when.
    private final Map<UUID, String> lastCastSpell = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCastTime = new ConcurrentHashMap<>();
    // Az utolsó ELŐTTI cast is kell a 3 lépéses kombó-láncok felismeréséhez.
    private final Map<UUID, String> secondLastCastSpell = new ConcurrentHashMap<>();
    private final Map<UUID, Long> secondLastCastTime = new ConcurrentHashMap<>();
    // Cast-számláló — a StatsManager a kézi DI-sorrendben később épül, ezért setter.
    private volatile hu.taliann.icesmp.managers.StatsManager statsManager;
    // Kombó/lánc-befejező után rövid ideig kiemelt sebzés-szám (DamageIndicatorListener olvassa).
    private final Map<UUID, Long> comboBoostUntil = new ConcurrentHashMap<>();
    // Az aktív kombó-ablak hint-lánc indítási időbélyege — az újabb cast érvényteleníti.
    private final Map<UUID, Long> hintStartedAt = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;
    private volatile hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway profileGateway;

    public void setProfileGateway(
            final hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway profileGateway) {
        this.profileGateway = java.util.Objects.requireNonNull(profileGateway, "profileGateway");
    }

    public AbilityCatalystListener(final JavaPlugin plugin, final JobManager jobManager,
                                   final SpellRegistry spellRegistry, final CatalystItemFactory catalystItemFactory,
                                   final ConfigManager configManager, final SpellMasteryManager masteryManager,
                                   final SpecializationManager specializationManager,
                                   final hu.taliann.icesmp.managers.ResourceManager resourceManager,
                                   final hu.taliann.icesmp.managers.TalentManager talentManager,
                                   final MessageManager messageManager, final SpellFavoritesManager spellFavoritesManager) {
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
        this.plugin = plugin;
        this.selectedSpellIndexKey = new NamespacedKey(plugin, "selected_spell_index");
    }

    /**
     * Whether the held item works as a spell catalyst for this player: the class-themed
     * catalyst item always does; for the configured melee classes (spells.melee-catalyst)
     * their held sword/axe does too, so a frontliner can cast without swapping items.
     */
    private boolean isUsableCatalyst(final Player player, final ItemStack item) {
        if (catalystItemFactory.isCatalyst(item)) {
            return true;
        }
        if (item == null || !configManager.getBoolean("spells.melee-catalyst.enabled", true)) {
            return false;
        }
        if (!configManager.getStringList("spells.melee-catalyst.materials").contains(item.getType().name())) {
            return false;
        }
        final var job = jobManager.getPrimaryJob(player);
        return job != null && configManager.getStringList("spells.melee-catalyst.classes").contains(job.getId());
    }

    /**
     * Dynamic power scaling on top of the mastery multiplier: the class level and the
     * 'spell-power' talent effect add a capped percentage bonus (spells.dynamic-scaling).
     */
    private double dynamicPowerMultiplier(final Player player) {
        if (!configManager.getBoolean("spells.dynamic-scaling.enabled", true)) {
            return 1.0D;
        }
        final double perLevel = Math.max(0.0D, configManager.getDouble("spells.dynamic-scaling.per-level-percent", 0.5D));
        final double talentBonus = talentManager == null ? 0.0D : talentManager.getEffectTotal(player, "spell-power");
        final double cap = Math.max(0.0D, configManager.getDouble("spells.dynamic-scaling.max-bonus-percent", 50.0D));
        // Varázserő gear-affix: a viselt páncél + a főkéz PDC-jéből összegezve — a caster-gear
        // így sebzést is ad (a közös plafon alatt); Ócska-roll negatív is lehet.
        double gearBonus = 0.0D;
        final hu.taliann.icesmp.managers.ItemRarityService rarity = this.itemRarityServiceRef;
        if (rarity != null) {
            for (final org.bukkit.inventory.ItemStack armor : player.getInventory().getArmorContents()) {
                gearBonus += rarity.spellPowerOf(armor);
            }
            gearBonus += rarity.spellPowerOf(player.getInventory().getItemInMainHand());
        }
        final double bonusPercent = Math.max(-50.0D,
                Math.min(cap, jobManager.getPrimaryLevel(player) * perLevel + Math.max(0.0D, talentBonus) + gearBonus));
        return 1.0D + bonusPercent / 100.0D;
    }

    private volatile hu.taliann.icesmp.managers.ItemRarityService itemRarityServiceRef;

    public void setItemRarityService(final hu.taliann.icesmp.managers.ItemRarityService itemRarityService) {
        this.itemRarityServiceRef = itemRarityService;
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        final Player player = event.getPlayer();
        final ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isUsableCatalyst(player, mainHand)) {
            return;
        }

        final Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Block vanilla item/block behavior (e.g. the goat horn blast) but keep event flow for reliable cast handling.
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        if (!profileRuntimeReady(player)) {
            return;
        }

        if (player.isSneaking()) {
            openSpellbook(player);
            return;
        }

        final long now = System.currentTimeMillis();
        final long lastCastInteract = castDebounce.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastCastInteract < 120L) {
            return;
        }
        castDebounce.put(player.getUniqueId(), now);

        castSelectedSpell(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerAnimation(final PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        final Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (!isUsableCatalyst(player, player.getInventory().getItemInMainHand())) {
            return;
        }
        if (!profileRuntimeReady(player)) {
            return;
        }

        final long now = System.currentTimeMillis();
        final long lastCycle = cycleDebounce.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastCycle < 120L) {
            return;
        }

        cycleDebounce.put(player.getUniqueId(), now);
        cycleSpell(player, 1);
    }

    /**
     * Shift + görgetés a katalizátorral a kézben: gyors spell-váltás hotbar-váltás helyett.
     * Lefelé görgetés = következő, felfelé = előző képesség; az event cancel-je megtartja
     * az aktív hotbar-slotot. A kiválasztott spell neve az item nevére íródik.
     */
    @EventHandler(ignoreCancelled = true)
    public void onHotbarScroll(final PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        if (!player.isSneaking() || !isUsableCatalyst(player, player.getInventory().getItemInMainHand())) {
            return;
        }
        if (!profileRuntimeReady(player)) {
            return;
        }
        event.setCancelled(true);
        // Kerekes irány a slot-lépésből (wrap-pel): 0->1..4 = előre, egyébként hátra.
        final int step = ((event.getNewSlot() - event.getPreviousSlot() + 9) % 9) <= 4 ? 1 : -1;
        cycleSpell(player, step);
    }

    private void cycleSpell(final Player player, final int step) {
        final List<String> unlocked = resolveUnlockedSpellIds(player);
        if (unlocked.isEmpty()) {
            player.sendActionBar(messageManager.getMessage("catalyst.no-spells", "<red>Nincs elérhető képesség.</red>"));
            return;
        }

        // Ha a játékos jelölt kedvenceket a spellkönyvben, a görgetés CSAK azokat
        // lépkedi (üres kedvenc-lista = a teljes feloldott lista). A kiválasztás-index továbbra
        // is a TELJES feloldott listába mutat, így a kedvencek ki-be kapcsolása nem borítja el.
        final java.util.Set<String> favorites = spellFavoritesManager.favorites(player);
        List<String> cycle = unlocked;
        if (!favorites.isEmpty()) {
            final List<String> onlyFavorites = unlocked.stream().filter(favorites::contains).toList();
            if (!onlyFavorites.isEmpty()) {
                cycle = onlyFavorites;
            }
        }
        final boolean favoritesOnly = cycle != unlocked;

        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        final int currentIndex = pdc.getOrDefault(selectedSpellIndexKey, PersistentDataType.INTEGER, -1);
        final String currentId = currentIndex >= 0 && currentIndex < unlocked.size() ? unlocked.get(currentIndex) : null;
        final int cyclePos = currentId == null ? -1 : cycle.indexOf(currentId);
        final int nextCyclePos = ((cyclePos + step) % cycle.size() + cycle.size()) % cycle.size();
        final int nextIndex = unlocked.indexOf(cycle.get(nextCyclePos));
        pdc.set(selectedSpellIndexKey, PersistentDataType.INTEGER, nextIndex);

        final Spell selected = spellRegistry.getById(cycle.get(nextCyclePos));
        if (selected == null) {
            player.sendActionBar(messageManager.getMessage("catalyst.current-spell-unknown", "<gray>Aktuális képesség: Ismeretlen</gray>"));
            return;
        }

        final int rank = masteryManager.getRank(player, selected.getId());
        final String mastery = rank > 0 ? " <aqua>★" + rank + "</aqua>" : "";
        player.sendActionBar(messageManager.getMessage(
                "catalyst.current-spell",
                "<gray>[{position}] <gold>{spell}</gold>{mastery} <dark_gray>({cost} {resource})</dark_gray> <dark_gray>— /spellbook</dark_gray></gray>",
                Map.of(
                        "spell", selected.getName(),
                        "mastery", mastery,
                        "cost", String.valueOf(displayedCost(selected)),
                        "resource", resolveResourceName(player, selected),
                        "position", (favoritesOnly ? "★" : "") + (nextCyclePos + 1) + "/" + cycle.size()
                )
        ));
        catalystItemFactory.playCycleSound(player, jobManager.getPrimaryJob(player));
        renameHeldCatalyst(player, selected);
    }

    /** A kézben tartott katalizátor neve a kiválasztott spellt mutatja (playtest-kérés). */
    private void renameHeldCatalyst(final Player player, final Spell selected) {
        final ItemStack held = player.getInventory().getItemInMainHand();
        if (!catalystItemFactory.isCatalyst(held)) {
            return;
        }
        held.editMeta(meta -> meta.displayName(
                net.kyori.adventure.text.Component.text("⚡ " + selected.getName(),
                                net.kyori.adventure.text.format.NamedTextColor.GOLD)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
    }

    private void castSelectedSpell(final Player player) {
        final List<String> unlocked = resolveUnlockedSpellIds(player);
        if (unlocked.isEmpty()) {
            player.sendActionBar(messageManager.getMessage("catalyst.no-unlocked", "<red>Még nem oldottál fel egyetlen képességet sem.</red>"));
            return;
        }

        final Spell selected = resolveSelectedSpell(player, unlocked);
        if (selected == null) {
            player.sendActionBar(messageManager.getMessage("catalyst.invalid-selection", "<red>A kiválasztott képesség nem érhető el.</red>"));
            return;
        }

        final long now = System.currentTimeMillis();
        final long remainingMs = getRemainingCooldown(player, selected, now);
        if (remainingMs > 0L) {
            final long remainingSeconds = (long) Math.ceil(remainingMs / 1000.0D);
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.cooldown",
                    "<red>Várj még {seconds} mp-et!</red>",
                    Map.of("seconds", String.valueOf(remainingSeconds))
            ));
            return;
        }

        if (!selected.canCast(player)) {
            player.sendActionBar(messageManager.getMessage("catalyst.not-ready", "<red>Most nem tudod használni ezt a képességet.</red>"));
            return;
        }

        final boolean useResource = resourceManager.usesResource(selected);
        final boolean canAfford = useResource ? resourceManager.canAfford(player, selected) : selected.hasRequiredCost(player);
        if (!canAfford) {
            player.sendActionBar(messageManager.getMessage(
                    "catalyst.no-cost",
                    "<red>Nincs elég {resource}! Szükséges: {amount}</red>",
                    Map.of(
                            "resource", resolveResourceName(player, selected),
                            "amount", String.valueOf(displayedCost(selected))
                    )
            ));
            return;
        }

        // HEALTH costs clamp to a 0.5 floor on consume, so a naive full refund could end up ABOVE
        // the pre-cast health. Snapshot it so a no-op cast restores exactly the pre-cast value.
        final double preCastHealth = player.getHealth();
        if (useResource) {
            resourceManager.consume(player, selected);
        } else {
            selected.consumeCost(player);
        }
        // Spell-mastery power scales the offensive output (damage, self-heal, effect duration);
        // the dynamic layer adds a capped class-level + 'spell-power' talent bonus on top.
        final double chainBonusPercent = chainFinisherPercent(player, selected.getId(), now);
        // A három réteg (mastery × szint/talent × lánc-finisher) szorzódva sapka nélkül
        // 2.8× fölé futna (nem-ultimate spellek is ~20 sebzésig) — közös felső plafon fékez.
        final double powerCap = Math.max(1.0D,
                configManager.getDouble("spells.total-power-cap", 1.75D));
        final double power = Math.min(powerCap,
                masteryManager.getPowerMultiplier(player, selected.getId())
                        * dynamicPowerMultiplier(player)
                        * (1.0D + chainBonusPercent / 100.0D));
        if (!selected.executeSpell(player, power)) {
            // No effect fired (no target, no companions, …) — refund the cost and skip the
            // cooldown so a missed cast costs the player nothing.
            if (useResource) {
                resourceManager.refund(player, selected);
            } else if (selected.getCostType() == hu.taliann.icesmp.spells.SpellCostType.HEALTH) {
                player.setHealth(preCastHealth);
            } else {
                selected.refundCost(player);
            }
            return;
        }

        // A combo (configured spell pair cast in quick succession) flows faster (cooldown refund) + flair.
        // A lánc-befejező is kombónak számít (visszatérítés), és külön üzenetet kap;
        // kombó után a HUD a lánc következő lépését, sima cast után a nyíló kombó-ablakot jelzi.
        final boolean chainFinisher = chainBonusPercent > 0.0D;
        final boolean combo = chainFinisher || isComboMatch(player, selected.getId(), now);
        // Az új cast érvényteleníti az előző kombó-hint láncot (a hint-ág újraindítja).
        hintStartedAt.remove(player.getUniqueId());
        if (combo) {
            comboBoostUntil.put(player.getUniqueId(), now + 3000L);
        }
        putCooldown(player, selected, combo ? now - comboRefundMillis(player, selected) : now);
        applyCooldownOverlay(player, selected, combo ? comboRefundMillis(player, selected) : 0L);
        playCastFlourish(player, combo);
        if (chainFinisher) {
            player.sendActionBar(messageManager.getMessage("catalyst.combo-finisher",
                    "<gold>⚡ Kombó-lánc befejező! +{bonus}% erő és gyorsabb felépülés.</gold>",
                    Map.of("bonus", String.valueOf((int) Math.round(chainBonusPercent)))));
        } else if (combo) {
            final String nextInChain = nextComboStep(player, selected.getId());
            final Spell nextSpell = nextInChain == null ? null : spellRegistry.getById(nextInChain);
            if (nextSpell != null) {
                player.sendActionBar(messageManager.getMessage("catalyst.combo-next",
                        "<gold>⚡ Kombó! Köv. a láncban: {next}</gold>", Map.of("next", nextSpell.getName())));
            } else {
                player.sendActionBar(messageManager.getMessage("catalyst.combo", "<gold>⚡ Kombó! Gyorsabb felépülés.</gold>"));
            }
        } else {
            sendComboWindowHint(player, selected.getId());
        }

        secondLastCastSpell.put(player.getUniqueId(), lastCastSpell.getOrDefault(player.getUniqueId(), ""));
        secondLastCastTime.put(player.getUniqueId(), lastCastTime.getOrDefault(player.getUniqueId(), 0L));
        lastCastSpell.put(player.getUniqueId(), selected.getId());
        lastCastTime.put(player.getUniqueId(), now);

        final hu.taliann.icesmp.managers.StatsManager stats = statsManager;
        if (stats != null) {
            stats.recordSpellCast(player.getUniqueId());
        }
    }

    private boolean profileRuntimeReady(final Player player) {
        final var gateway = profileGateway;
        if (gateway == null || !gateway.enabled() || gateway.isSessionReady(player.getUniqueId())) {
            return true;
        }
        player.sendActionBar(messageManager.getMessage(
                "profile-v2.runtime-blocked",
                "<red>A kaszt/specializáció profilod biztonsági ellenőrzést igényel. /spec info</red>"));
        return false;
    }

    /**
     * Cast után a kézben tartott katalizátor anyagán elindítja a vanília szürke
     * item-cooldown overlay-t — a hotbaron azonnal látszik, mikor castolhatsz újra.
     * A vizuális réteg NEM kapuz semmit (a tényleges cooldownt a putCooldown-pipeline őrzi);
     * a melee-katalizátoros kasztoknál a kard/balta ütés-képességét sem érinti.
     */
    private void applyCooldownOverlay(final Player player, final Spell spell, final long comboRefundMs) {
        if (!configManager.getBoolean("spells.cooldown-overlay.enabled", true)) {
            return;
        }
        final long cooldownTicks = (spell.getCooldown() * 20L) - (comboRefundMs / 50L);
        if (cooldownTicks <= 0L) {
            return;
        }
        final ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return;
        }
        // ItemStack-overload: a pálca-katalizátor SAJÁT cooldown-csoportját (USE_COOLDOWN) használja,
        // a melee-kard (nincs komponens) marad a Material-alapú overlayen — így nincs vanília-item bleed.
        player.setCooldown(held, (int) Math.min(Integer.MAX_VALUE, cooldownTicks));
    }

    /** Whether the player's previous cast forms a configured combo with this one. */
    private boolean isComboMatch(final Player player, final String currentSpellId, final long now) {
        if (!configManager.getBoolean("spells.combos.enabled", true)) {
            return false;
        }
        final String previous = lastCastSpell.get(player.getUniqueId());
        if (previous == null) {
            return false;
        }
        final long windowMs = Math.max(1L, configManager.getLong("spells.combos.window-seconds", 4L)) * 1000L;
        if (now - lastCastTime.getOrDefault(player.getUniqueId(), 0L) > windowMs) {
            return false;
        }

        final ConfigurationSection combos = configManager.getConfiguration() == null
                ? null : configManager.getConfiguration().getConfigurationSection("spells.combos.pairs");
        if (combos == null) {
            return false;
        }
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

    /**
     * 3 lépéses kombó-lánc befejezőjének erő-bónusza. Ha az utolsó két cast (minden
     * lépésköz a kombó-ablakon belül) egy konfigurált lánc ({@code spells.combos.chains}) első
     * két lépése, és a mostani spell a harmadik, a finisher +X% erővel sül el.
     *
     * @return a bónusz százalék, vagy 0, ha ez a cast nem lánc-befejező
     */
    private double chainFinisherPercent(final Player player, final String currentSpellId, final long now) {
        if (!configManager.getBoolean("spells.combos.enabled", true)) {
            return 0.0D;
        }
        final UUID id = player.getUniqueId();
        final String prev1 = lastCastSpell.get(id);
        final String prev2 = secondLastCastSpell.get(id);
        if (prev1 == null || prev2 == null || prev2.isEmpty()) {
            return 0.0D;
        }
        final long windowMs = Math.max(1L, configManager.getLong("spells.combos.window-seconds", 4L)) * 1000L;
        final long t1 = lastCastTime.getOrDefault(id, 0L);
        final long t2 = secondLastCastTime.getOrDefault(id, 0L);
        if (now - t1 > windowMs || t1 - t2 > windowMs) {
            return 0.0D;
        }
        final ConfigurationSection chains = configManager.getConfiguration() == null
                ? null : configManager.getConfiguration().getConfigurationSection("spells.combos.chains");
        if (chains == null) {
            return 0.0D;
        }
        for (final String key : chains.getKeys(false)) {
            final ConfigurationSection chain = chains.getConfigurationSection(key);
            if (chain == null) {
                continue;
            }
            final List<String> steps = chain.getStringList("steps");
            if (steps.size() == 3
                    && prev2.equalsIgnoreCase(steps.get(0))
                    && prev1.equalsIgnoreCase(steps.get(1))
                    && currentSpellId.equalsIgnoreCase(steps.get(2))) {
                return Math.max(0.0D, chain.getDouble("finisher-power-bonus-percent",
                        configManager.getDouble("spells.combos.finisher-power-bonus-percent", 25.0D)));
            }
        }
        return 0.0D;
    }

    /**
     * Sima (nem kombós) cast után kiírja az action barra, melyik spell
     * nyitna kombót az időablakon belül — így a játékos tanulja a láncokat.
     */
    private void sendComboWindowHint(final Player player, final String justCastId) {
        if (!configManager.getBoolean("spells.combos.enabled", true)) {
            return;
        }
        final String nextId = nextComboStep(player, justCastId);
        final Spell next = nextId == null ? null : spellRegistry.getById(nextId);
        if (next == null) {
            return;
        }
        // Nem egyszeri üzenet, hanem 500 ms-onként frissülő FOGYÓ CSÍK a hátralévő
        // kombó-ablakról — az időbélyeg az "aktív hint" azonosítója, egy újabb cast (új put vagy
        // remove) némán leállítja a régi láncot.
        final long windowMillis = Math.max(1L, configManager.getLong("spells.combos.window-seconds", 4L)) * 1000L;
        final long startedAt = System.currentTimeMillis();
        hintStartedAt.put(player.getUniqueId(), startedAt);
        renderComboHint(player, next.getName(), startedAt, windowMillis);
    }

    /** Egy hint-lánc lépése — csak addig fut, amíg az időbélyege az aktuális. */
    private void renderComboHint(final Player player, final String nextName, final long startedAt,
                                 final long windowMillis) {
        final Long current = hintStartedAt.get(player.getUniqueId());
        if (current == null || current.longValue() != startedAt) {
            return; // Újabb cast érvénytelenítette — a lánc némán leáll.
        }
        final long elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed >= windowMillis) {
            hintStartedAt.remove(player.getUniqueId(), current);
            return; // Lejárt — csend, nincs "lejárt" üzenet.
        }
        final int segments = 8;
        final int filled = segments - (int) Math.min(segments, elapsed * segments / windowMillis);
        player.sendActionBar(messageManager.getMessage("catalyst.combo-window",
                "<gray>⏳ <gold>{bar}</gold><dark_gray>{empty}</dark_gray> Kombó: <gold>{next}</gold></gray>",
                Map.of("bar", "▰".repeat(filled), "empty", "▱".repeat(segments - filled), "next", nextName)));
        player.getScheduler().runDelayed(plugin,
                task -> renderComboHint(player, nextName, startedAt, windowMillis), null, 10L);
    }

    /**
     * A most elsütött spell utáni következő kombó-lépés (lánc-folytatás előnyben a párokkal
     * szemben), vagy null. A hívás a cast-történet FRISSÍTÉSE ELŐTT történik, így a
     * {@code lastCastSpell} még az eggyel korábbi castot tartalmazza.
     */
    private String nextComboStep(final Player player, final String justCastId) {
        final var configuration = configManager.getConfiguration();
        if (configuration == null) {
            return null;
        }
        final ConfigurationSection chains = configuration.getConfigurationSection("spells.combos.chains");
        if (chains != null) {
            final String prev1 = lastCastSpell.get(player.getUniqueId());
            for (final String key : chains.getKeys(false)) {
                final ConfigurationSection chain = chains.getConfigurationSection(key);
                if (chain == null) {
                    continue;
                }
                final List<String> steps = chain.getStringList("steps");
                if (steps.size() != 3) {
                    continue;
                }
                if (prev1 != null && prev1.equalsIgnoreCase(steps.get(0)) && justCastId.equalsIgnoreCase(steps.get(1))) {
                    return steps.get(2);
                }
                if (justCastId.equalsIgnoreCase(steps.get(0))) {
                    return steps.get(1);
                }
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
        // Cap the configured refund at 80% so a combo can never fully erase a cooldown.
        final double percent = Math.max(0.0D, Math.min(80.0D,
                configManager.getDouble("spells.combos.bonus-cooldown-refund-percent", 40.0D)));
        final long baseCooldownMs = Math.max(0L, spell.getCooldown()) * 1000L;
        final long refund = (long) (baseCooldownMs * (percent / 100.0D));

        // Hard floor: even stacked with spell-mastery cooldown reduction, the combo must leave
        // a minimum residual cooldown (the larger of 1s or 15% of the base cooldown).
        final long effectiveCooldownMs = (long) (baseCooldownMs * masteryManager.getCooldownMultiplier(player, spell.getId()));
        final long floorMs = Math.max(1000L, (long) (baseCooldownMs * 0.15D));
        final long maxRefund = Math.max(0L, effectiveCooldownMs - floorMs);
        return Math.min(refund, maxRefund);
    }

    private void playCastFlourish(final Player player, final boolean combo) {
        player.getWorld().spawnParticle(combo ? Particle.TOTEM_OF_UNDYING : Particle.ENCHANT,
                player.getLocation().add(0.0D, 1.1D, 0.0D), combo ? 14 : 10, 0.4D, 0.5D, 0.4D, combo ? 0.3D : 0.15D);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, combo ? 1.6F : 1.2F);
    }

    /** The cost shown to the player: the class-resource cost for resource spells, else the thematic cost. */
    private int displayedCost(final Spell spell) {
        return resourceManager.usesResource(spell) ? resourceManager.costOf(spell) : spell.getCostAmount();
    }

    /** The resource name shown to the player: the class pool (Mana/Düh…) for resource spells, else the type. */
    private String resolveResourceName(final Player player, final Spell spell) {
        if (resourceManager.usesResource(spell)) {
            return resourceManager.resourceName(player);
        }
        return switch (spell.getCostType()) {
            case HUNGER -> messageManager.get("system.resources.hunger", "éhség");
            case XP -> messageManager.get("system.resources.xp", "XP");
            case HEALTH -> messageManager.get("system.resources.health", "élet");
        };
    }

    private Spell resolveSelectedSpell(final Player player, final List<String> unlocked) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        int index = pdc.getOrDefault(selectedSpellIndexKey, PersistentDataType.INTEGER, 0);
        if (index < 0 || index >= unlocked.size()) {
            index = 0;
            pdc.set(selectedSpellIndexKey, PersistentDataType.INTEGER, 0);
        }

        return spellRegistry.getById(unlocked.get(index));
    }

    private long getRemainingCooldown(final Player player, final Spell spell, final long now) {
        final Long lastCast = getLastCast(player, spell);
        if (lastCast == null) {
            return 0L;
        }

        final long cooldownMs = (long) (Math.max(0, spell.getCooldown()) * 1000L
                * masteryManager.getCooldownMultiplier(player, spell.getId()));
        final long delayMs = Math.max(0, spell.getCooldownDelay()) * 1000L;
        return Math.max(0L, (lastCast + delayMs + cooldownMs) - now);
    }

    private void putCooldown(final Player player, final Spell spell, final long now) {
        if (isPersistentCooldown(spell)) {
            player.getPersistentDataContainer().set(resolveLongCooldownKey(spell.getId()), PersistentDataType.LONG, now);
            return;
        }

        spellCooldowns.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>()).put(spell.getId(), now);
    }

    private List<String> resolveUnlockedSpellIds(final Player player) {
        return jobManager.getUnlockedSpellIds(player).stream()
                .filter(spellId -> spellRegistry.getById(spellId) != null)
                .toList();
    }

    public void openSpellbook(final Player player) {
        openSpellbook(player, 0, false);
    }

    public void openSpellbook(final Player player, final int page) {
        openSpellbook(player, page, false);
    }

    /** Opens the spellbook GUI at the given page with the "only unlocked" filter applied or not. */
    public void openSpellbook(final Player player, final int page, final boolean onlyUnlocked) {
        SpellbookGUI.open(player, this, jobManager, specializationManager, spellRegistry,
                masteryManager, configManager, messageManager, resourceManager, spellFavoritesManager, page, onlyUnlocked);
    }

    /** The player's currently unlocked, castable spell ids, in selection order. */
    public List<String> getUnlockedSpellIds(final Player player) {
        return resolveUnlockedSpellIds(player);
    }

    /** The id of the spell currently selected on the catalyst, or null if none. */
    public String getSelectedSpellId(final Player player) {
        final List<String> unlocked = resolveUnlockedSpellIds(player);
        if (unlocked.isEmpty()) {
            return null;
        }
        int index = player.getPersistentDataContainer().getOrDefault(selectedSpellIndexKey, PersistentDataType.INTEGER, 0);
        if (index < 0 || index >= unlocked.size()) {
            index = 0;
        }
        return unlocked.get(index);
    }

    /**
     * Selects the given spell on the catalyst (by setting the stored index to its
     * position in the unlocked list).
     *
     * @return true if the spell is unlocked and was selected
     */
    public boolean selectSpell(final Player player, final String spellId) {
        if (spellId == null) {
            return false;
        }
        final int index = resolveUnlockedSpellIds(player).indexOf(spellId.toLowerCase(Locale.ROOT));
        if (index < 0) {
            return false;
        }
        player.getPersistentDataContainer().set(selectedSpellIndexKey, PersistentDataType.INTEGER, index);
        return true;
    }

    /** Remaining cooldown in ms for the spell (0 if ready). */
    public long getRemainingCooldownMs(final Player player, final Spell spell) {
        return getRemainingCooldown(player, spell, System.currentTimeMillis());
    }

    public int getMasteryRank(final Player player, final String spellId) {
        return masteryManager.getRank(player, spellId);
    }

    /**
     * Read-only view of the player's currently active SHORT (&lt;60s, in-memory) spell
     * cooldowns, spell id → remaining milliseconds, expired entries filtered out.
     * UUID-based (no {@link Player} needed) so it is safe to call from any
     * thread — it only reads the {@link #spellCooldowns} map, never touches an entity.
     * Persistent (&gt;=60s, PDC-backed) cooldowns are NOT included here, since those require
     * reading the target's own PDC on the target's region thread; callers that already hold
     * the {@link Player} on its own thread should prefer {@link #getRemainingCooldownMs}.
     *
     * @param playerId the player to check
     * @return spell id → remaining cooldown millis, only the not-yet-expired ones
     */
    public Map<String, Long> activeCooldowns(final UUID playerId) {
        final Map<String, Long> bySpell = spellCooldowns.get(playerId);
        if (bySpell == null || bySpell.isEmpty()) {
            return Map.of();
        }
        final long now = System.currentTimeMillis();
        final Map<String, Long> result = new java.util.LinkedHashMap<>();
        for (final Map.Entry<String, Long> entry : bySpell.entrySet()) {
            final Spell spell = spellRegistry.getById(entry.getKey());
            if (spell == null) {
                continue;
            }
            final long cooldownMs = Math.max(0, spell.getCooldown()) * 1000L;
            final long delayMs = Math.max(0, spell.getCooldownDelay()) * 1000L;
            final long remaining = (entry.getValue() + delayMs + cooldownMs) - now;
            if (remaining > 0L) {
                result.put(entry.getKey(), remaining);
            }
        }
        return result;
    }

    /** Cast-számláló bekötése (a StatsManager a DI-sorrendben később épül). */
    public void setStatsManager(final hu.taliann.icesmp.managers.StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    public void cleanup(final UUID playerId) {
        if (playerId == null) {
            return;
        }

        spellCooldowns.remove(playerId);
        cycleDebounce.remove(playerId);
        castDebounce.remove(playerId);
        lastCastSpell.remove(playerId);
        lastCastTime.remove(playerId);
        secondLastCastSpell.remove(playerId);
        secondLastCastTime.remove(playerId);
        comboBoostUntil.remove(playerId);
        hintStartedAt.remove(playerId);
    }

    /**
     * Igaz, ha a játékos legutóbbi castja kombó/lánc-befejező volt és a 3 mp-es
     * kiemelés-ablak még nem járt le. Lejárt bejegyzést lustán eltávolítja.
     */
    public boolean hasComboBoost(final UUID playerId) {
        if (playerId == null) {
            return false;
        }
        final Long until = comboBoostUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            comboBoostUntil.remove(playerId);
            return false;
        }
        return true;
    }

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }

    public void resetCooldowns(final Player player) {
        if (player == null) {
            return;
        }

        spellCooldowns.remove(player.getUniqueId());

        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        for (final NamespacedKey key : pdc.getKeys()) {
            if (!"icesmp".equalsIgnoreCase(key.getNamespace())) {
                continue;
            }
            if (key.getKey().startsWith("cd_")) {
                pdc.remove(key);
            }
        }
    }

    public void resetAllSpellState(final Player player) {
        if (player == null) {
            return;
        }

        resetCooldowns(player);
        player.getPersistentDataContainer().remove(selectedSpellIndexKey);
    }

    private Long getLastCast(final Player player, final Spell spell) {
        if (isPersistentCooldown(spell)) {
            return player.getPersistentDataContainer().get(resolveLongCooldownKey(spell.getId()), PersistentDataType.LONG);
        }

        final Map<String, Long> bySpell = spellCooldowns.get(player.getUniqueId());
        if (bySpell == null) {
            return null;
        }

        return bySpell.get(spell.getId());
    }

    private boolean isPersistentCooldown(final Spell spell) {
        return spell.getCooldown() >= 60;
    }

    private NamespacedKey resolveLongCooldownKey(final String spellId) {
        final String normalized = spellId == null
                ? "unknown"
                : spellId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return longCooldownKeys.computeIfAbsent(normalized, id -> new NamespacedKey("icesmp", "cd_" + id));
    }
}
