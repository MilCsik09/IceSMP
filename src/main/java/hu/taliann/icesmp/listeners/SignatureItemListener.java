package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileCooldownStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Perk behaviour for the crafted signature items (K2 — Cryghaliris; extensible for K3). Items are
 * recognised by the {@code signature_item} PDC id stamped by the recipe engine:
 * <ul>
 *   <li><b>kallan_szeletelo</b> (íj): gyorsabb nyíl + „páncéltörő" bónusz-sebzés a lövedéken.</li>
 *   <li><b>glatziendorfi_jegvert</b> (mellvért): viselve sebzés-csökkentés (Resistance I-jellegű).</li>
 *   <li><b>jegsarkany_kantar</b>: jobb katt egy hátason → tartós sebesség-bónusz (elfogy).</li>
 * </ul>
 * Folia: a lövés a lövő szálán fut (a friss lövedék régió-lokális); a sebzés-események az áldozat
 * szálán, ahol a nyíl/mellvért lokális; a kantár a hátas schedulerére hopol, majd vissza a
 * játékoséra az item-fogyasztáshoz.
 */
public final class SignatureItemListener implements Listener {

    public static final String KALLAN_BOW = "kallan_szeletelo";
    public static final String JEGVERT = "glatziendorfi_jegvert";
    public static final String KANTAR = "jegsarkany_kantar";
    public static final String TUZKOPO = "pyralingradi_tuzkopo";
    public static final String AGYAR = "verszavanna_agyara";
    public static final String TOLLKOPENY = "fonix_tollkopeny";
    public static final String CSAKANY = "vasmuvek_csakanya";
    public static final String HORGASZBOT = "bokic_horgaszbot";
    public static final String BANKBETET = "smaragdko_bankbetet";
    public static final String SZARVASBUBAJ = "szellemszarvas_bubaj";
    public static final String JEGTORO = "glatziendorfi_jegtoro";
    public static final String MIINUS_KARD = "miinus_haragja";
    public static final String SARKANYCSONT_IJ = "sarkanycsont_ij";
    public static final String LANGNYELV = "zhoris_langnyelve";
    public static final String NAPFOGYATKOZAS = "napfogyatkozas";

    private static final String SPIRIT_STAG_COOLDOWN = "signature.spirit-stag";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final hu.taliann.icesmp.managers.GatheringBuffManager gatheringBuffManager;
    private final hu.taliann.icesmp.managers.CurrencyManager currencyManager;
    private final hu.taliann.icesmp.managers.TerritoryManager territoryManager;
    private final hu.taliann.icesmp.itemization.EquipmentProficiencyService proficiency;
    private final hu.taliann.icesmp.itemization.ItemIdentityService identities;
    private final PlayerProfileCooldownStore cooldownStore = new PlayerProfileCooldownStore();
    private final Set<UUID> spiritStagStarts = ConcurrentHashMap.newKeySet();
    private final NamespacedKey signatureKey;
    private final NamespacedKey pierceKey;
    private final NamespacedKey kantarAppliedKey;
    private final NamespacedKey kantarSpeedKey;
    private final NamespacedKey signatureTierKey;

    public SignatureItemListener(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager,
                                 final hu.taliann.icesmp.managers.GatheringBuffManager gatheringBuffManager,
                                 final hu.taliann.icesmp.managers.CurrencyManager currencyManager,
                                 final hu.taliann.icesmp.managers.TerritoryManager territoryManager,
                                 final hu.taliann.icesmp.itemization.EquipmentProficiencyService proficiency,
                                 final hu.taliann.icesmp.itemization.ItemIdentityService identities) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.gatheringBuffManager = gatheringBuffManager;
        this.currencyManager = currencyManager;
        this.territoryManager = territoryManager;
        this.proficiency = proficiency;
        this.identities = identities;
        this.signatureKey = new NamespacedKey(plugin, "signature_item");
        this.pierceKey = new NamespacedKey(plugin, "sig_pierce");
        this.kantarAppliedKey = new NamespacedKey(plugin, "sig_kantar");
        this.kantarSpeedKey = new NamespacedKey(plugin, "sig_kantar_speed");
        this.signatureTierKey = new NamespacedKey(plugin, "signature_tier");
        this.slowArrowKey = new NamespacedKey(plugin, "sig_jegfog");
        this.igniteArrowKey = new NamespacedKey(plugin, "sig_vihartuz");
        this.eclipseArrowKey = new NamespacedKey(plugin, "sig_napfogyatkozas");
    }

    private final NamespacedKey slowArrowKey;
    private final NamespacedKey igniteArrowKey;
    private final NamespacedKey eclipseArrowKey;

    public static final NamespacedKey SIGNATURE_PDC_KEY = NamespacedKey.fromString("icesmp:signature_item");

    private static final java.util.concurrent.ConcurrentHashMap<String, org.bukkit.enchantments.Enchantment> ENCHANT_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static org.bukkit.enchantments.Enchantment enchant(final String id) {
        final org.bukkit.enchantments.Enchantment cached = ENCHANT_CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        try {
            final org.bukkit.enchantments.Enchantment found = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                    .get(org.bukkit.NamespacedKey.fromString("icesmp:" + id));
            if (found != null) {
                ENCHANT_CACHE.put(id, found);
            }
            return found;
        } catch (final Exception exception) {
            return null;
        }
    }

    private static boolean hasEnchant(final ItemStack item, final String id) {
        final org.bukkit.enchantments.Enchantment ench = enchant(id);
        return ench != null && item != null && item.containsEnchantment(ench);
    }

    private String idOf(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(signatureKey, PersistentDataType.STRING);
    }

    private String activeId(final Player player, final ItemStack item,
                            final hu.taliann.icesmp.itemization.ItemTemplate.Slot slot) {
        final String signature = idOf(item);
        if (signature == null) return null;
        final var inspection = identities.inspect(item);
        if (inspection.status()
                == hu.taliann.icesmp.itemization.ItemIdentityService.Status.NOT_MANAGED) {
            return signature;
        }
        return proficiency.isActive(player, item, slot) ? signature : null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(final EntityShootBowEvent event) {
        final String sig = event.getEntity() instanceof Player player
                ? activeId(player, event.getBow(),
                hu.taliann.icesmp.itemization.ItemTemplate.Slot.MAIN_HAND)
                : idOf(event.getBow());
        if (KALLAN_BOW.equals(sig)) {
            final double mult = Math.max(1.0D, configManager.getDouble("signature.kallan.arrow-velocity-mult", 1.5D));
            final Entity projectile = event.getProjectile();
            projectile.setVelocity(projectile.getVelocity().multiply(mult));
            final double pierce = Math.max(0.0D, configManager.getDouble("signature.kallan.armor-pierce", 0.15D));
            if (pierce > 0.0D) {
                projectile.getPersistentDataContainer().set(pierceKey, PersistentDataType.DOUBLE, pierce);
            }
            if (hasEnchant(event.getBow(), "jegfog")) {
                projectile.getPersistentDataContainer().set(slowArrowKey, PersistentDataType.BYTE, (byte) 1);
            }
        } else if (SARKANYCSONT_IJ.equals(sig)) {
            if (event.getProjectile() instanceof AbstractArrow arrow) {
                arrow.setPierceLevel(Math.min(127, arrow.getPierceLevel()
                        + Math.max(0, configManager.getInt("signature.sarkanycsont.pierce-add", 2))));
            }
        } else if (NAPFOGYATKOZAS.equals(sig)) {
            final org.bukkit.World world = event.getEntity().getWorld();
            final boolean night = world.getEnvironment() != org.bukkit.World.Environment.NORMAL || !world.isDayTime();
            if (night) {
                final double mult = Math.max(1.0D, configManager.getDouble("signature.napfogyatkozas.night-velocity-mult", 1.15D));
                event.getProjectile().setVelocity(event.getProjectile().getVelocity().multiply(mult));
                event.getProjectile().getPersistentDataContainer().set(eclipseArrowKey, PersistentDataType.BYTE, (byte) 1);
            }
        } else if (TUZKOPO.equals(sig)) {
            final double mult = Math.max(1.0D, configManager.getDouble("signature.tuzkopo.arrow-velocity-mult", 1.5D));
            final Entity projectile = event.getProjectile();
            projectile.setVelocity(projectile.getVelocity().multiply(mult));
            if (hasEnchant(event.getBow(), "vihartuz")) {
                projectile.getPersistentDataContainer().set(igniteArrowKey, PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMelee(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        final String meleeSig = activeId(attacker, attacker.getInventory().getItemInMainHand(),
                hu.taliann.icesmp.itemization.ItemTemplate.Slot.MAIN_HAND);
        if (hu.taliann.icesmp.listeners.CapitalLawListener.SETAPALCA.equals(meleeSig)) {
            if (configManager.getBoolean("signature.setapalca.capital-only", true)
                    && !isNeutralCapital(attacker.getLocation())) {
                return;
            }
            final double bonus = Math.max(0.0D, configManager.getDouble("signature.setapalca.bonus-damage", 5.0D));
            event.setDamage(event.getDamage() + bonus);
            return;
        }
        if (JEGTORO.equals(meleeSig)) {
            if (event.getEntity() instanceof org.bukkit.entity.LivingEntity struck
                    && struck.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)) {
                final int tier = signatureTier(attacker.getInventory().getItemInMainHand());
                final double bonus = Math.max(0.0D,
                        configManager.getDouble("signature.jegtoro.slowed-bonus", 0.25D)
                                + (tier - 1) * Math.max(0.0D, configManager.getDouble(
                                "signature.itemization-tier-bonus", 0.03D)));
                event.setDamage(event.getDamage() * (1.0D + bonus));
            }
            return;
        }
        if (MIINUS_KARD.equals(meleeSig)) {
            final org.bukkit.attribute.AttributeInstance max = attacker.getAttribute(Attribute.MAX_HEALTH);
            final double threshold = Math.max(0.05D, Math.min(1.0D,
                    configManager.getDouble("signature.miinus.low-health-threshold", 0.35D)));
            if (max != null && attacker.getHealth() / max.getValue() <= threshold) {
                final double bonus = Math.max(0.0D, configManager.getDouble("signature.miinus.low-health-bonus", 0.2D));
                event.setDamage(event.getDamage() * (1.0D + bonus));
            }
            return;
        }
        if (LANGNYELV.equals(meleeSig)) {
            if (event.getEntity() instanceof org.bukkit.entity.LivingEntity struck) {
                if (struck.getFireTicks() > 0) {
                    final double bonus = Math.max(0.0D, configManager.getDouble("signature.langnyelv.burning-bonus", 0.15D));
                    event.setDamage(event.getDamage() * (1.0D + bonus));
                } else if (java.util.concurrent.ThreadLocalRandom.current().nextDouble()
                        < Math.max(0.0D, Math.min(1.0D, configManager.getDouble("signature.langnyelv.ignite-chance", 0.2D)))) {
                    struck.setFireTicks(Math.max(struck.getFireTicks(),
                            Math.max(1, configManager.getInt("signature.langnyelv.ignite-ticks", 40))));
                }
            }
            return;
        }
        if (!AGYAR.equals(meleeSig)) {
            return;
        }
        final boolean offhandAxe = attacker.getInventory().getItemInOffHand().getType().name().endsWith("_AXE");
        final double mult = offhandAxe
                ? Math.max(1.0D, configManager.getDouble("signature.agyar.offhand-axe-mult", 1.3D))
                : Math.max(1.0D, configManager.getDouble("signature.agyar.damage-mult", 1.15D));
        event.setDamage(event.getDamage() * mult);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeLifesteal(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        final ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!AGYAR.equals(activeId(attacker, weapon,
                hu.taliann.icesmp.itemization.ItemTemplate.Slot.MAIN_HAND))
                || !hasEnchant(weapon, "verszomj")) {
            return;
        }
        final double ratio = Math.max(0.0D, configManager.getDouble("signature.enchant-riders.verszomj-lifesteal", 0.1D));
        final double cap = Math.max(0.0D, configManager.getDouble("signature.enchant-riders.verszomj-heal-cap", 2.0D));
        final double heal = Math.min(cap, event.getFinalDamage() * ratio);
        final AttributeInstance maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH);
        if (heal > 0.0D && maxHealth != null) {
            attacker.setHealth(Math.min(maxHealth.getValue(), attacker.getHealth() + heal));
        }
    }

    private boolean isNeutralCapital(final org.bukkit.Location location) {
        final hu.taliann.icesmp.data.Territory zone = territoryManager.getTerritoryAt(location);
        return zone != null && zone.type() == hu.taliann.icesmp.data.TerritoryType.CAPITAL
                && zone.faction() == hu.taliann.icesmp.data.FactionType.NEUTRAL;
    }

    @EventHandler(ignoreCancelled = true)
    public void onArrowDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow)) {
            return;
        }
        if (event.getEntity() instanceof org.bukkit.entity.LivingEntity struck) {
            if (arrow.getPersistentDataContainer().has(slowArrowKey, PersistentDataType.BYTE)) {
                final int seconds = Math.max(1, configManager.getInt("signature.enchant-riders.jegfog-slow-seconds", 2));
                struck.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, seconds * 20, 0, true, true, true));
            }
            if (arrow.getPersistentDataContainer().has(igniteArrowKey, PersistentDataType.BYTE)) {
                final int fireTicks = Math.max(1, configManager.getInt("signature.enchant-riders.vihartuz-fire-ticks", 40));
                struck.setFireTicks(Math.max(struck.getFireTicks(), fireTicks));
            }
        }
        if (arrow.getPersistentDataContainer().has(eclipseArrowKey, PersistentDataType.BYTE)) {
            final double bonus = Math.max(0.0D, configManager.getDouble("signature.napfogyatkozas.night-damage-bonus", 0.25D));
            event.setDamage(event.getDamage() * (1.0D + bonus));
        }
        final Double pierce = arrow.getPersistentDataContainer().get(pierceKey, PersistentDataType.DOUBLE);
        if (pierce == null || pierce <= 0.0D) {
            return;
        }
        event.setDamage(event.getDamage() * (1.0D + pierce));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamaged(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        final String chest = activeId(player, player.getInventory().getChestplate(),
                hu.taliann.icesmp.itemization.ItemTemplate.Slot.CHEST);
        if (JEGVERT.equals(chest)) {
            final int tier = signatureTier(player.getInventory().getChestplate());
            final double mult = Math.min(1.0D, Math.max(0.0D,
                    configManager.getDouble("signature.jegvert.damage-mult", 0.8D)
                            - (tier - 1) * Math.max(0.0D, configManager.getDouble(
                            "signature.itemization-tier-bonus", 0.03D))));
            event.setDamage(event.getDamage() * mult);
            return;
        }
        if (TOLLKOPENY.equals(chest) && isFireCause(event.getCause())
                && configManager.getBoolean("signature.tollkopeny.fire-immunity", true)) {
            event.setCancelled(true);
        }
    }

    private static boolean isFireCause(final EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }

    private int signatureTier(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;
        final Integer tier = item.getItemMeta().getPersistentDataContainer().get(
                signatureTierKey, PersistentDataType.INTEGER);
        return tier == null ? 1 : Math.max(1, Math.min(16, tier));
    }

    @EventHandler(ignoreCancelled = true)
    public void onKantarUse(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Player player = event.getPlayer();
        if (!KANTAR.equals(idOf(player.getInventory().getItemInMainHand()))) {
            return;
        }
        if (!(event.getRightClicked() instanceof AbstractHorse horse)) {
            return;
        }
        event.setCancelled(true);
        final double add = Math.max(0.0D, configManager.getDouble("signature.kantar.speed-add", 0.05D));
        horse.getScheduler().run(plugin, task -> {
            if (horse.getPersistentDataContainer().has(kantarAppliedKey, PersistentDataType.BYTE)) {
                player.getScheduler().run(plugin, t2 -> player.sendMessage(messageManager.get(
                        "signature-kantar-already", "&7Ez a hátas már felkantározott.")), null);
                return;
            }
            final AttributeInstance speed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed == null) {
                return;
            }
            speed.addModifier(new AttributeModifier(kantarSpeedKey, add, AttributeModifier.Operation.ADD_NUMBER));
            horse.getPersistentDataContainer().set(kantarAppliedKey, PersistentDataType.BYTE, (byte) 1);
            player.getScheduler().run(plugin, t2 -> {
                final ItemStack hand = player.getInventory().getItemInMainHand();
                if (KANTAR.equals(idOf(hand))) {
                    hand.setAmount(hand.getAmount() - 1);
                }
                player.sendMessage(messageManager.get("signature-kantar-applied",
                        "&b❄ A vad sárkányvér megszelídül — a hátasod léptei felgyorsultak."));
            }, null);
        }, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMine(final org.bukkit.event.block.BlockBreakEvent event) {
        final Player player = event.getPlayer();
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            return;
        }
        final ItemStack tool = player.getInventory().getItemInMainHand();
        if (!CSAKANY.equals(activeId(player, tool,
                hu.taliann.icesmp.itemization.ItemTemplate.Slot.MAIN_HAND))) {
            return;
        }
        final org.bukkit.block.Block block = event.getBlock();
        if (!(block.getType().name().endsWith("_ORE") || block.getType() == org.bukkit.Material.ANCIENT_DEBRIS)) {
            return;
        }
        if (gatheringBuffManager.getActive() == hu.taliann.icesmp.managers.GatheringBuffManager.GatheringBuff.MINING_RUSH
                && !configManager.getBoolean("signature.csakany.stack-with-event", false)) {
            return;
        }
        final double chance = Math.min(1.0D, Math.max(0.0D,
                configManager.getDouble("signature.csakany.bonus-drop-chance", 0.2D)));
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        for (final ItemStack drop : block.getDrops(tool, player)) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), drop.clone());
        }
        if (hasEnchant(tool, "erc_erzek")) {
            final int xp = Math.max(0, configManager.getInt("signature.enchant-riders.erc-erzek-xp", 2));
            if (xp > 0) {
                block.getWorld().spawn(block.getLocation().add(0.5D, 0.5D, 0.5D),
                        org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(xp));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(final org.bukkit.event.player.PlayerFishEvent event) {
        if (event.getState() != org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        final Player player = event.getPlayer();
        final String main = idOf(player.getInventory().getItemInMainHand());
        final String off = idOf(player.getInventory().getItemInOffHand());
        if (!HORGASZBOT.equals(main) && !HORGASZBOT.equals(off)) {
            return;
        }
        if (gatheringBuffManager.getActive() == hu.taliann.icesmp.managers.GatheringBuffManager.GatheringBuff.FISHING_FRENZY
                && !configManager.getBoolean("signature.horgaszbot.stack-with-event", false)) {
            return;
        }
        if (!(event.getCaught() instanceof org.bukkit.entity.Item caughtItem)
                || !org.bukkit.Bukkit.isOwnedByCurrentRegion(caughtItem)) {
            return;
        }
        final double chance = Math.min(1.0D, Math.max(0.0D,
                configManager.getDouble("signature.horgaszbot.bonus-drop-chance", 0.2D)));
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), caughtItem.getItemStack().clone());
        player.sendActionBar(messageManager.getMessage("signature-horgaszbot-bonus",
                "<aqua>🎣 A Bokic bősége — dupla fogás!</aqua>"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFishLuck(final org.bukkit.event.player.PlayerFishEvent event) {
        if (event.getState() != org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        final Player player = event.getPlayer();
        if (!hasEnchant(player.getInventory().getItemInMainHand(), "bokic_kegye")
                && !hasEnchant(player.getInventory().getItemInOffHand(), "bokic_kegye")) {
            return;
        }
        final int seconds = Math.max(1, configManager.getInt("signature.enchant-riders.bokic-luck-seconds", 30));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.LUCK, seconds * 20, 0, true, true, true));
    }

    @EventHandler
    public void onUse(final org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        final Player player = event.getPlayer();
        final ItemStack hand = player.getInventory().getItemInMainHand();
        final String sig = idOf(hand);
        if (BANKBETET.equals(sig)) {
            event.setCancelled(true);
            final double value = Math.max(0.0D, configManager.getDouble("signature.bankbetet.value", 25.0D));
            hand.setAmount(hand.getAmount() - 1);
            currencyManager.payOutTokens(player,
                    hu.taliann.icesmp.data.CurrencyType.fromFactionType(hu.taliann.icesmp.data.FactionType.NEUTRAL), Math.round(value));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.4F);
            player.sendMessage(messageManager.get("signature-bankbetet-redeemed",
                    "&a💠 A Bankárszövetség beváltotta a betétjegyet: &f+%s Creutzér&a a kezedbe számolva.",
                    currencyManager.formatBalance(value)));
        } else if (SZARVASBUBAJ.equals(sig)) {
            event.setCancelled(true);
            summonSpiritStag(player);
        }
    }

    private void summonSpiritStag(final Player player) {
        final UUID playerId = player.getUniqueId();
        if (!spiritStagStarts.add(playerId)) {
            player.sendActionBar(messageManager.getMessage(
                    "signature-szarvas-pending",
                    "<gray>🦌 A köd még gyülekezik — az idézés folyamatban van.</gray>"));
            return;
        }
        final long now = System.currentTimeMillis();
        final long cooldownMillis = secondsToMillis(configManager.getLong(
                "signature.szarvas.cooldown-seconds", 120L));
        final long readyAt;
        try {
            readyAt = cooldownStore.read(playerId,
                    PlayerProfileCooldownStore.Domain.FACTION, SPIRIT_STAG_COOLDOWN);
        } catch (final PlayerProfileAuthority.ProfileNotReadyException notReady) {
            spiritStagStarts.remove(playerId);
            player.sendActionBar(messageManager.getMessage(
                    "signature-szarvas-profile-not-ready",
                    "<red>🦌 A PlayerProfile még nem áll készen; az idézés nem indult el.</red>"));
            return;
        }
        if (now < readyAt) {
            spiritStagStarts.remove(playerId);
            showSpiritStagCooldown(player, readyAt - now);
            return;
        }
        final long next = saturatingAdd(now, cooldownMillis);
        cooldownStore.reserve(playerId, PlayerProfileCooldownStore.Domain.FACTION,
                        SPIRIT_STAG_COOLDOWN, now, next)
                .whenComplete((reserved, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure != null) {
                        spiritStagStarts.remove(playerId);
                        plugin.getLogger().severe("Spirit stag cooldown commit failed for "
                                + playerId + ": " + failure.getMessage());
                        player.sendActionBar(messageManager.getMessage(
                                "signature-szarvas-persistence-failed",
                                "<red>🦌 A tartós cooldown mentése meghiúsult; az idézés nem indult el.</red>"));
                        return;
                    }
                    if (!Boolean.TRUE.equals(reserved)) {
                        spiritStagStarts.remove(playerId);
                        final long liveReady;
                        try {
                            liveReady = cooldownStore.read(playerId,
                                    PlayerProfileCooldownStore.Domain.FACTION,
                                    SPIRIT_STAG_COOLDOWN);
                        } catch (final RuntimeException unavailable) {
                            player.sendActionBar(messageManager.getMessage(
                                    "signature-szarvas-persistence-failed",
                                    "<red>🦌 A tartós cooldown nem olvasható; az idézés nem indult el.</red>"));
                            return;
                        }
                        showSpiritStagCooldown(player,
                                Math.max(0L, liveReady - System.currentTimeMillis()));
                        return;
                    }
                    completeSpiritStagSummon(player, next);
                }, () -> compensateSpiritStagCooldown(playerId, next,
                        "player scheduler rejected spirit stag activation")));
    }

    private void completeSpiritStagSummon(final Player player, final long reservedUntil) {
        final UUID playerId = player.getUniqueId();
        if (!player.isOnline()) {
            compensateSpiritStagCooldown(playerId, reservedUntil,
                    "player went offline before spirit stag spawn");
            return;
        }
        try {
            spawnSpiritStag(player);
            spiritStagStarts.remove(playerId);
        } catch (final RuntimeException | Error failure) {
            compensateSpiritStagCooldown(playerId, reservedUntil,
                    "spirit stag runtime activation failed: " + failure.getMessage());
        }
    }

    private void spawnSpiritStag(final Player player) {
        final org.bukkit.entity.Horse mount = player.getWorld().spawn(
                player.getLocation(), org.bukkit.entity.Horse.class);
        mount.setTamed(true);
        mount.setOwner(player);
        mount.setColor(org.bukkit.entity.Horse.Color.WHITE);
        mount.setStyle(org.bukkit.entity.Horse.Style.WHITE_DOTS);
        mount.getInventory().setSaddle(new ItemStack(org.bukkit.Material.SADDLE));
        mount.setGlowing(true);
        mount.setPersistent(false);
        mount.setRemoveWhenFarAway(false);
        mount.customName(net.kyori.adventure.text.Component.text("Szellemszarvas",
                net.kyori.adventure.text.format.NamedTextColor.AQUA));
        mount.setCustomNameVisible(true);
        final AttributeInstance speed = mount.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(Math.max(0.1D,
                    configManager.getDouble("signature.szarvas.speed", 0.3D)));
        }
        final AttributeInstance jump = mount.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.setBaseValue(Math.max(0.4D,
                    configManager.getDouble("signature.szarvas.jump", 0.8D)));
        }
        mount.addPassenger(player);
        player.getWorld().playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_HORSE_ANGRY, 0.8F, 1.5F);
        hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(),
                org.bukkit.Particle.END_ROD,
                player.getLocation().add(0.0D, 1.0D, 0.0D),
                24, 0.8D, 0.8D, 0.8D, 0.02D);

        final long durationTicks = secondsToTicks(Math.max(10L,
                configManager.getLong("signature.szarvas.duration-seconds", 90L)));
        mount.getScheduler().runDelayed(plugin, task -> {
            if (mount.isValid()) {
                hu.taliann.icesmp.utils.ParticleUtil.spawn(mount.getWorld(),
                        org.bukkit.Particle.CLOUD,
                        mount.getLocation().add(0.0D, 1.0D, 0.0D),
                        16, 0.5D, 0.5D, 0.5D, 0.02D);
                mount.remove();
            }
        }, null, durationTicks);
        player.sendMessage(messageManager.get("signature-szarvas-summoned",
                "&b🦌 A Szellemszarvas előlép a ködből — vidd, ahová a Menedék útjai hívnak."));
    }

    private void compensateSpiritStagCooldown(final UUID playerId,
                                              final long reservedUntil,
                                              final String reason) {
        cooldownStore.reserve(playerId, PlayerProfileCooldownStore.Domain.FACTION,
                        SPIRIT_STAG_COOLDOWN, reservedUntil, 0L)
                .whenComplete((rolledBack, failure) -> {
                    spiritStagStarts.remove(playerId);
                    if (failure != null || !Boolean.TRUE.equals(rolledBack)) {
                        plugin.getLogger().severe("Spirit stag cooldown compensation failed for "
                                + playerId + " after " + reason + "; admin audit required.");
                    } else {
                        plugin.getLogger().warning("Spirit stag cooldown compensated for "
                                + playerId + " after " + reason + '.');
                    }
                });
    }

    private void showSpiritStagCooldown(final Player player, final long leftMillis) {
        final long left = Math.max(0L, saturatingAdd(leftMillis, 999L) / 1000L);
        player.sendActionBar(messageManager.getMessage("signature-szarvas-cooldown",
                "<gray>🦌 A Szellemszarvas még pihen — {seconds} mp múlva hívhatod újra.</gray>",
                java.util.Map.of("seconds", String.valueOf(left))));
    }

    private static long secondsToMillis(final long seconds) {
        if (seconds <= 0L) return 0L;
        return seconds > Long.MAX_VALUE / 1000L
                ? Long.MAX_VALUE : seconds * 1000L;
    }

    private static long secondsToTicks(final long seconds) {
        if (seconds <= 0L) return 1L;
        return seconds > Long.MAX_VALUE / 20L
                ? Long.MAX_VALUE : seconds * 20L;
    }

    private static long saturatingAdd(final long first, final long second) {
        if (second <= 0L) return first;
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
}
