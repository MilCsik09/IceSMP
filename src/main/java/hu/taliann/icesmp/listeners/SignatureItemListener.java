package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
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
    // K4 — Ryanora & Caldestera (NEUTRAL) gyűjtő/gazdaság itemek:
    public static final String CSAKANY = "vasmuvek_csakanya";
    public static final String HORGASZBOT = "bokic_horgaszbot";
    public static final String BANKBETET = "smaragdko_bankbetet";
    public static final String SZARVASBUBAJ = "szellemszarvas_bubaj";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final hu.taliann.icesmp.managers.GatheringBuffManager gatheringBuffManager;
    private final hu.taliann.icesmp.managers.CurrencyManager currencyManager;
    private final NamespacedKey signatureKey;
    private final NamespacedKey pierceKey;
    private final NamespacedKey kantarAppliedKey;
    private final NamespacedKey kantarSpeedKey;
    private final NamespacedKey szarvasCooldownKey;

    public SignatureItemListener(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager,
                                 final hu.taliann.icesmp.managers.GatheringBuffManager gatheringBuffManager,
                                 final hu.taliann.icesmp.managers.CurrencyManager currencyManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.gatheringBuffManager = gatheringBuffManager;
        this.currencyManager = currencyManager;
        this.signatureKey = new NamespacedKey(plugin, "signature_item");
        this.pierceKey = new NamespacedKey(plugin, "sig_pierce");
        this.kantarAppliedKey = new NamespacedKey(plugin, "sig_kantar");
        this.kantarSpeedKey = new NamespacedKey(plugin, "sig_kantar_speed");
        this.szarvasCooldownKey = new NamespacedKey(plugin, "sig_szarvas_cd");
    }

    /** The signature id of an item, or null. */
    private String idOf(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(signatureKey, PersistentDataType.STRING);
    }

    // ==================== Kallan Szeletelője ====================

    @EventHandler(ignoreCancelled = true)
    public void onShoot(final EntityShootBowEvent event) {
        final String sig = idOf(event.getBow());
        if (KALLAN_BOW.equals(sig)) {
            final double mult = Math.max(1.0D, configManager.getDouble("signature.kallan.arrow-velocity-mult", 1.5D));
            final Entity projectile = event.getProjectile();
            projectile.setVelocity(projectile.getVelocity().multiply(mult));
            final double pierce = Math.max(0.0D, configManager.getDouble("signature.kallan.armor-pierce", 0.15D));
            if (pierce > 0.0D) {
                projectile.getPersistentDataContainer().set(pierceKey, PersistentDataType.DOUBLE, pierce);
            }
        } else if (TUZKOPO.equals(sig)) {
            // A Tűzköpő lövedéke a „sivatagi vihar sebességével" repül; a felhúzási gyorsítást a
            // craftkor kapott Quick Charge adja (ProfessionRecipeBookListener).
            final double mult = Math.max(1.0D, configManager.getDouble("signature.tuzkopo.arrow-velocity-mult", 1.5D));
            final Entity projectile = event.getProjectile();
            projectile.setVelocity(projectile.getVelocity().multiply(mult));
        }
    }

    // ==================== A Vérszavanna Agyara ====================

    @EventHandler(ignoreCancelled = true)
    public void onMelee(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        // Közelharc: a támadó és az áldozat ütés-távolságon belül van — Folián ez azonos régiót
        // jelent (a kereszt-régió kockázat a távolsági/projectile esetekre áll), a felszerelés-
        // olvasás itt biztonságos.
        if (!AGYAR.equals(idOf(attacker.getInventory().getItemInMainHand()))) {
            return;
        }
        final boolean offhandAxe = attacker.getInventory().getItemInOffHand().getType().name().endsWith("_AXE");
        final double mult = offhandAxe
                ? Math.max(1.0D, configManager.getDouble("signature.agyar.offhand-axe-mult", 1.3D))
                : Math.max(1.0D, configManager.getDouble("signature.agyar.damage-mult", 1.15D));
        event.setDamage(event.getDamage() * mult);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArrowDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow)) {
            return;
        }
        final Double pierce = arrow.getPersistentDataContainer().get(pierceKey, PersistentDataType.DOUBLE);
        if (pierce == null || pierce <= 0.0D) {
            return;
        }
        // A „páncéltörés" végső-sebzés bónuszként valósul meg — a vanília páncél-formulát nem
        // hackeljük, így a vanília-limiteken belül marad (lásd K2 buktatók).
        event.setDamage(event.getDamage() * (1.0D + pierce));
    }

    // ==================== Glatziendorfi Jégvért ====================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamaged(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        final String chest = idOf(player.getInventory().getChestplate());
        if (JEGVERT.equals(chest)) {
            final double mult = Math.min(1.0D, Math.max(0.0D,
                    configManager.getDouble("signature.jegvert.damage-mult", 0.8D)));
            event.setDamage(event.getDamage() * mult);
            return;
        }
        // Főnix-Tollköpeny: tűz/láva-védelem tárgyként hordozva — bárki viselheti, aki megszerzi
        // (a craft RED-kapus, a köpeny kereskedhető).
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

    // ==================== Jégsárkány-Kantár ====================

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
        // A hátas MÁSIK entitás — minden érintése a saját schedulerén (Folia), majd vissza a
        // játékos szálára az item-fogyasztáshoz és üzenethez.
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
                // A kantár csak akkor fogy el, ha még mindig a kézben van (a hop alatt elrakhatta) —
                // ritka kihagyás elfogadható, dupla-fogyasztás nem történhet.
                final ItemStack hand = player.getInventory().getItemInMainHand();
                if (KANTAR.equals(idOf(hand))) {
                    hand.setAmount(hand.getAmount() - 1);
                }
                player.sendMessage(messageManager.get("signature-kantar-applied",
                        "&b❄ A vad sárkányvér megszelídül — a hátasod léptei felgyorsultak."));
            }, null);
        }, null);
    }

    // ==================== Vasművek Akadémiájának Csákánya (K4) ====================

    @EventHandler(ignoreCancelled = true)
    public void onMine(final org.bukkit.event.block.BlockBreakEvent event) {
        final Player player = event.getPlayer();
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            return;
        }
        final ItemStack tool = player.getInventory().getItemInMainHand();
        if (!CSAKANY.equals(idOf(tool))) {
            return;
        }
        final org.bukkit.block.Block block = event.getBlock();
        if (!(block.getType().name().endsWith("_ORE") || block.getType() == org.bukkit.Material.ANCIENT_DEBRIS)) {
            return;
        }
        // Sapka (K4 buktató): bányász-láz esemény alatt a tárgy-perk alapból NEM stackel.
        if (gatheringBuffManager.getActive() == hu.taliann.icesmp.managers.GatheringBuffManager.GatheringBuff.MINING_RUSH
                && !configManager.getBoolean("signature.csakany.stack-with-event", false)) {
            return;
        }
        final double chance = Math.min(1.0D, Math.max(0.0D,
                configManager.getDouble("signature.csakany.bonus-drop-chance", 0.2D)));
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        // Bónusz: a blokk normál dropjainak egy extra példánya (régió-lokális, a törés szálán).
        for (final ItemStack drop : block.getDrops(tool, player)) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), drop.clone());
        }
    }

    // ==================== Bokic-menti Horgászbot (K4) ====================

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
        // Sapka: halászati láz esemény alatt a tárgy-perk alapból NEM stackel.
        if (gatheringBuffManager.getActive() == hu.taliann.icesmp.managers.GatheringBuffManager.GatheringBuff.FISHING_FRENZY
                && !configManager.getBoolean("signature.horgaszbot.stack-with-event", false)) {
            return;
        }
        if (!(event.getCaught() instanceof org.bukkit.entity.Item caughtItem)
                || !org.bukkit.Bukkit.isOwnedByCurrentRegion(caughtItem)) {
            // Folia: a kifogott item-entitás ritkán szomszéd régióban úszhat — ilyenkor kimarad.
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

    // ==================== Smaragdkő Bankbetét + Szellemszarvas-Bűbáj (K4) ====================

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
            // Atomi beváltás a játékos SAJÁT régió-szálán: előbb fogy a papír, aztán a jóváírás
            // (UUID-kulcsú, szál-biztos) — dupe nem lehetséges, mert a kettő közé nem fér esemény.
            final double value = Math.max(0.0D, configManager.getDouble("signature.bankbetet.value", 25.0D));
            hand.setAmount(hand.getAmount() - 1);
            currencyManager.addToBalance(player.getUniqueId(),
                    hu.taliann.icesmp.data.CurrencyType.fromFactionType(hu.taliann.icesmp.data.FactionType.NEUTRAL), value);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.4F);
            player.sendMessage(messageManager.get("signature-bankbetet-redeemed",
                    "&a💠 A Bankárszövetség beváltotta a betétjegyet: &f+%s Creutzér&a a számládon.",
                    currencyManager.formatBalance(value)));
        } else if (SZARVASBUBAJ.equals(sig)) {
            event.setCancelled(true);
            summonSpiritStag(player);
        }
    }

    /**
     * Szellemszarvas-hívás: cooldown-kapuzott, ideiglenes gyors hátast idéz a játékos mellé.
     * Minden érintett entitás régió-lokális (a hátas a játékos pozícióján spawnol, az ültetés
     * ugyanazon a szálon történik); a hátas élettartamát a saját entity-schedulere zárja le.
     */
    private void summonSpiritStag(final Player player) {
        final long now = System.currentTimeMillis();
        final long cooldownMillis = Math.max(0L, configManager.getLong("signature.szarvas.cooldown-seconds", 120L)) * 1000L;
        final Long lastUse = player.getPersistentDataContainer().get(szarvasCooldownKey, PersistentDataType.LONG);
        if (lastUse != null && now - lastUse < cooldownMillis) {
            final long left = (cooldownMillis - (now - lastUse) + 999L) / 1000L;
            player.sendActionBar(messageManager.getMessage("signature-szarvas-cooldown",
                    "<gray>🦌 A Szellemszarvas még pihen — {seconds} mp múlva hívhatod újra.</gray>",
                    java.util.Map.of("seconds", String.valueOf(left))));
            return;
        }
        player.getPersistentDataContainer().set(szarvasCooldownKey, PersistentDataType.LONG, now);

        final org.bukkit.entity.Horse mount = player.getWorld().spawn(player.getLocation(), org.bukkit.entity.Horse.class);
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
            speed.setBaseValue(Math.max(0.1D, configManager.getDouble("signature.szarvas.speed", 0.3D)));
        }
        final AttributeInstance jump = mount.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.setBaseValue(Math.max(0.4D, configManager.getDouble("signature.szarvas.jump", 0.8D)));
        }
        mount.addPassenger(player);
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_HORSE_ANGRY, 0.8F, 1.5F);
        hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), org.bukkit.Particle.END_ROD,
                player.getLocation().add(0.0D, 1.0D, 0.0D), 24, 0.8D, 0.8D, 0.8D, 0.02D);

        final long durationTicks = Math.max(10L, configManager.getLong("signature.szarvas.duration-seconds", 90L)) * 20L;
        mount.getScheduler().runDelayed(plugin, task -> {
            if (mount.isValid()) {
                hu.taliann.icesmp.utils.ParticleUtil.spawn(mount.getWorld(), org.bukkit.Particle.CLOUD,
                        mount.getLocation().add(0.0D, 1.0D, 0.0D), 16, 0.5D, 0.5D, 0.5D, 0.02D);
                mount.remove();
            }
        }, null, durationTicks);
        player.sendMessage(messageManager.get("signature-szarvas-summoned",
                "&b🦌 A Szellemszarvas előlép a ködből — vidd, ahová a Menedék útjai hívnak."));
    }
}
