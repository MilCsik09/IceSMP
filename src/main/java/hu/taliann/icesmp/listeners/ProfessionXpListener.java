package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.TalentManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Awards profession XP for the WoW-style profession roster:
 * MINER (ores), HERBALIST (ripe crops, flowers, berries), LUMBERJACK (logs),
 * ARMORER (gear crafting + smithing), ENCHANTER (enchanting table),
 * ALCHEMIST (collecting brewed potions), FISHERMAN (catches), COOK (furnace food).
 */
public final class ProfessionXpListener implements Listener {

    /** I16 — setter-injektált: szakma-céh heti közös cél számlálója. */
    private volatile hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyGoal;

    public void setWeeklyGoal(final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyGoal) {
        this.weeklyGoal = weeklyGoal;
    }

    private static final Set<Material> CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.SWEET_BERRY_BUSH, Material.COCOA
    );

    private static final Set<Material> POTIONS = EnumSet.of(
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION
    );

    private final ProfessionManager professionManager;
    private final ConfigManager configManager;
    private final TalentManager talentManager;

    private final hu.taliann.icesmp.managers.AfkManager afkManager;

    public ProfessionXpListener(final ProfessionManager professionManager, final ConfigManager configManager,
                                final TalentManager talentManager,
                                final hu.taliann.icesmp.managers.AfkManager afkManager) {
        this.professionManager = professionManager;
        this.configManager = configManager;
        this.talentManager = talentManager;
        this.afkManager = afkManager;
    }

    /** I7: setterrel kötve — Bőség-idő alatt a Gyógynövényész BETAKARÍTÁS extra XP-t ad. */
    private volatile hu.taliann.icesmp.managers.AbundanceManager abundanceManager;

    public void setAbundanceManager(final hu.taliann.icesmp.managers.AbundanceManager abundanceManager) {
        this.abundanceManager = abundanceManager;
    }

    /**
     * I7 — évszakos termés: a HERBALIST betakarítás-XP a Bőség-idő ablakában
     * config-szorzót kap (professions.seasonal.abundance-multiplier, default 1.5) —
     * CSAK a harvest-utakra, a sima blokk-törés (bányász/favágó) érintetlen.
     */
    private void awardHarvestXp(final Player player, final String configPath, final int fallback) {
        final hu.taliann.icesmp.managers.AbundanceManager abundanceRef = abundanceManager;
        final double mult = abundanceRef != null && abundanceRef.isActive()
                ? Math.max(1.0D, configManager.getDouble("professions.seasonal.abundance-multiplier", 1.5D))
                : 1.0D;
        final int base = Math.max(0, configManager.getInt(configPath, fallback));
        awardXpAmount(player, ProfessionType.HERBALIST, (int) Math.round(base * mult));
    }

    private void awardXp(final Player player, final ProfessionType profession, final String configPath, final int fallback) {
        // AFK-jelölt játékos nem termel szakma-XP-t (auto-farm exploit-fék).
        if (afkManager != null && configManager.getBoolean("afk.block-rewards", true)
                && afkManager.isAfk(player.getUniqueId())) {
            return;
        }
        awardXpAmount(player, profession, Math.max(0, configManager.getInt(configPath, fallback)));
    }

    private void awardXpAmount(final Player player, final ProfessionType profession, final int baseXp) {
        if (afkManager != null && configManager.getBoolean("afk.block-rewards", true)
                && afkManager.isAfk(player.getUniqueId())) {
            return;
        }
        final double bonusPercent = Math.max(0.0D, talentManager.getEffectTotal(player, "profession-xp-bonus"));
        final int totalXp = (int) Math.round(baseXp * (1.0D + (bonusPercent / 100.0D)));
        // Az addXpFor false-t ad, ha a játékos NEM gyakorolja ezt a szakmát (nem kapott XP-t).
        // A heti közös cél csak valódi jóváírásra tölthető, különben egy nem-bányász
        // ércbontása is töltötte a Bányász-céh heti célját (szakma-identitás nélküli farm).
        professionManager.addXpFor(player, profession, totalXp)
                .whenComplete((change, failure) -> {
                    if (failure != null || change == null || !change.changed()) {
                        return;
                    }
                    professionManager.runOnOwnerThread(player, () -> {
                        final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyRef = weeklyGoal;
                        if (weeklyRef != null && player.isOnline()) {
                            weeklyRef.add(player, profession, totalXp);
                        }
                    });
                });
    }

    // MONITOR: a védelmi réteg HIGH/HIGHEST prioritáson cancel-el, ezért NORMAL-on a
    // progresszt még a visszavonás ELŐTT könyveltük volna — a tiltott törés/lerakás így
    // XP-t és quest-haladást adott. MONITOR-on az event végleges állapota már ismert,
    // és az ignoreCancelled valóban kizárja a visszavont akciót. Itt NEM módosítunk eventet.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        // Creative farm-guard (same as GatheringBuffListener): no profession XP for
        // creative/spectator breaks or silk-touch-less no-drop breaks.
        if (!isSurvival(player) || !event.isDropItems()) {
            return;
        }
        final Block block = event.getBlock();
        final Material material = block.getType();

        if (isOre(material)) {
            awardXp(player, ProfessionType.MINER, "professions.xp.mining-ore", 5);
            return;
        }

        if (Tag.LOGS.isTagged(material)) {
            awardXp(player, ProfessionType.LUMBERJACK, "professions.xp.logging", 2);
            return;
        }

        if (Tag.FLOWERS.isTagged(material)) {
            awardHarvestXp(player, "professions.xp.herbalism-harvest", 3);
            return;
        }

        if (CROPS.contains(material) && block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() >= ageable.getMaximumAge()) {
            awardHarvestXp(player, "professions.xp.herbalism-harvest", 3);
        }
    }

    // MONITOR: a védelmi réteg HIGH/HIGHEST prioritáson cancel-el, ezért NORMAL-on a
    // progresszt még a visszavonás ELŐTT könyveltük volna — a tiltott törés/lerakás így
    // XP-t és quest-haladást adott. MONITOR-on az event végleges állapota már ismert,
    // és az ignoreCancelled valóban kizárja a visszavont akciót. Itt NEM módosítunk eventet.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHarvestBlock(final PlayerHarvestBlockEvent event) {
        if (!isSurvival(event.getPlayer())) {
            return;
        }
        awardHarvestXp(event.getPlayer(), "professions.xp.herbalism-harvest", 3);
    }

    private static boolean isSurvival(final Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    // MONITOR: a védelmi réteg HIGH/HIGHEST prioritáson cancel-el, ezért NORMAL-on a
    // progresszt még a visszavonás ELŐTT könyveltük volna — a tiltott törés/lerakás így
    // XP-t és quest-haladást adott. MONITOR-on az event végleges állapota már ismert,
    // és az ignoreCancelled valóban kizárja a visszavont akciót. Itt NEM módosítunk eventet.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(final CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isSurvival(player)) {
            return;
        }

        final ItemStack result = event.getRecipe().getResult();
        if (isArmorerCraft(result.getType())) {
            awardXp(player, ProfessionType.ARMORER, "professions.xp.crafting-gear", 8);
        }
    }

    // MONITOR: a védelmi réteg HIGH/HIGHEST prioritáson cancel-el, ezért NORMAL-on a
    // progresszt még a visszavonás ELŐTT könyveltük volna — a tiltott törés/lerakás így
    // XP-t és quest-haladást adott. MONITOR-on az event végleges állapota már ismert,
    // és az ignoreCancelled valóban kizárja a visszavont akciót. Itt NEM módosítunk eventet.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmithItem(final SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isSurvival(player)) {
            return;
        }

        awardXp(player, ProfessionType.ARMORER, "professions.xp.smithing", 15);
    }

    // MONITOR: a védelmi réteg HIGH/HIGHEST prioritáson cancel-el, ezért NORMAL-on a
    // progresszt még a visszavonás ELŐTT könyveltük volna — a tiltott törés/lerakás így
    // XP-t és quest-haladást adott. MONITOR-on az event végleges állapota már ismert,
    // és az ignoreCancelled valóban kizárja a visszavont akciót. Itt NEM módosítunk eventet.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(final EnchantItemEvent event) {
        if (!isSurvival(event.getEnchanter())) {
            return;
        }
        awardXp(event.getEnchanter(), ProfessionType.ENCHANTER, "professions.xp.enchanting", 10);
    }

    // MONITOR: a védelmi réteg HIGH/HIGHEST prioritáson cancel-el, ezért NORMAL-on a
    // progresszt még a visszavonás ELŐTT könyveltük volna — a tiltott törés/lerakás így
    // XP-t és quest-haladást adott. MONITOR-on az event végleges állapota már ismert,
    // és az ignoreCancelled valóban kizárja a visszavont akciót. Itt NEM módosítunk eventet.
    /**
     * Azok az {@link org.bukkit.event.inventory.InventoryAction} értékek, amelyek a kattintott
     * slotból TÉNYLEGESEN elvisznek tárgyat. A többi (NOTHING, PLACE_*, CLONE_STACK, UNKNOWN)
     * nem kivétel, tehát nem fizethet.
     */
    private static final java.util.Set<org.bukkit.event.inventory.InventoryAction> TAKE_ACTIONS =
            java.util.Set.of(
                    org.bukkit.event.inventory.InventoryAction.PICKUP_ALL,
                    org.bukkit.event.inventory.InventoryAction.PICKUP_HALF,
                    org.bukkit.event.inventory.InventoryAction.PICKUP_SOME,
                    org.bukkit.event.inventory.InventoryAction.PICKUP_ONE,
                    org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY,
                    org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP,
                    org.bukkit.event.inventory.InventoryAction.SWAP_WITH_CURSOR,
                    org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR,
                    org.bukkit.event.inventory.InventoryAction.DROP_ONE_SLOT,
                    org.bukkit.event.inventory.InventoryAction.DROP_ALL_SLOT);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewedPotionPickup(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isSurvival(player)) {
            return;
        }

        if (!(event.getView().getTopInventory() instanceof BrewerInventory)) {
            return;
        }

        // The three result slots of the brewing stand are raw slots 0-2.
        if (event.getRawSlot() < 0 || event.getRawSlot() > 2) {
            return;
        }

        final ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !POTIONS.contains(clicked.getType())) {
            return;
        }
        // CSAK a tényleges kivétel fizet. A puszta „potion van a result-slotban" nem elég: a
        // no-op kattintás (NOTHING, inkompatibilis kurzor), a klónozás és a bent maradó főzetre
        // ismételt kattintás korlátlan Alkimista-XP-t és heti cél-hozzájárulást adott.
        if (!TAKE_ACTIONS.contains(event.getAction())) {
            return;
        }

        awardXp(player, ProfessionType.ALCHEMIST, "professions.xp.brewing", 12);
    }

    // MONITOR: a védelmi réteg HIGH/HIGHEST prioritáson cancel-el, ezért NORMAL-on a
    // progresszt még a visszavonás ELŐTT könyveltük volna — a tiltott törés/lerakás így
    // XP-t és quest-haladást adott. MONITOR-on az event végleges állapota már ismert,
    // és az ignoreCancelled valóban kizárja a visszavont akciót. Itt NEM módosítunk eventet.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        if (!isSurvival(event.getPlayer())) {
            return;
        }
        awardXp(event.getPlayer(), ProfessionType.FISHERMAN, "professions.xp.fishing", 4);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceExtract(final FurnaceExtractEvent event) {
        if (!event.getItemType().isEdible()) {
            return;
        }

        if (!isSurvival(event.getPlayer())) {
            return;
        }
        final int perItem = Math.max(0, configManager.getInt("professions.xp.cooking", 5));
        awardXpAmount(event.getPlayer(), ProfessionType.COOK,
                perItem * Math.max(1, event.getItemAmount()));
    }

    private boolean isOre(final Material material) {
        return material == Material.ANCIENT_DEBRIS
                || material.name().toLowerCase(Locale.ROOT).endsWith("_ore");
    }

    private boolean isArmorerCraft(final Material material) {
        final String name = material.name().toLowerCase(Locale.ROOT);
        return name.endsWith("_helmet")
                || name.endsWith("_chestplate")
                || name.endsWith("_leggings")
                || name.endsWith("_boots")
                || material == Material.SHIELD;
    }
}
