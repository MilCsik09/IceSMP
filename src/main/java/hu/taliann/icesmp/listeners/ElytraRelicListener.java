package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;

/**
 * The four faction elytra relics (ideas.md "4 frakció – 4 elytra relikvia"):
 * - phoenix_wing (RED): fire/lava immunity while worn; falls end in a flame burst
 * - frost_wing (BLUE): freeze immunity; taking flight freezes nearby enemies
 * - wander_wind (NEUTRAL): no fall damage; taking flight grants a wind boost
 * - bone_wing (DARK): wither immunity; night flight turns the wearer into a shade
 * Effects only apply to the relic's owner wearing it in the chest slot AND
 * belonging to the wing's faction.
 */
public final class ElytraRelicListener implements Listener {

    private static final Map<String, FactionType> WING_FACTIONS = Map.of(
            "phoenix_wing", FactionType.RED,
            "frost_wing", FactionType.BLUE,
            "wander_wind", FactionType.NEUTRAL,
            "bone_wing", FactionType.DARK
    );

    private static final Set<EntityDamageEvent.DamageCause> FIRE_CAUSES = Set.of(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.HOT_FLOOR
    );

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final RelicManager relicManager;
    private final FactionManager factionManager;
    private final hu.taliann.icesmp.managers.ConfigManager configManager;
    private final MessageManager messageManager;

    public ElytraRelicListener(final org.bukkit.plugin.java.JavaPlugin plugin,
                               final RelicManager relicManager, final FactionManager factionManager,
                               final hu.taliann.icesmp.managers.ConfigManager configManager,
                               final MessageManager messageManager) {
        this.plugin = plugin;
        this.relicManager = relicManager;
        this.factionManager = factionManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(final EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding()) {
            return;
        }

        final String wingId = resolveWornWing(player, true);
        if (wingId == null) {
            return;
        }

        switch (wingId) {
            case "phoenix_wing" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60 * 20, 0, false, false, true));
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 25, 0.4D, 0.4D, 0.4D, 0.02D);
            }
            case "frost_wing" -> {
                // Folia: a közeli entitás szomszéd régióé is lehet — csak birtokolt entitást
                // érintünk közvetlenül, a többihez scheduler-hop (audit-javítás).
                for (final Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), 6.0D, 6.0D, 6.0D)) {
                    if (nearby instanceof LivingEntity living && nearby != player) {
                        if (org.bukkit.Bukkit.isOwnedByCurrentRegion(living)) {
                            freezeTarget(living);
                        } else {
                            living.getScheduler().run(plugin, task -> freezeTarget(living), null);
                        }
                    }
                }
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation(), 24, 2.5D, 0.8D, 2.5D, 0.02D);
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8F, 0.6F);
            }
            case "wander_wind" -> {
                player.setVelocity(player.getLocation().getDirection().multiply(1.5D));
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 25, 0.5D, 0.5D, 0.5D, 0.05D);
                player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.8F, 1.2F);
            }
            case "bone_wing" -> {
                if (isNight(player)) {
                    startShadeLoop(player);
                } else {
                    // Lore-visszajelzés: a szárny csak az éj leple alatt változtat árnyékká.
                    player.sendActionBar(messageManager.getMessage("relic.bone-wing-day",
                            "<gray>☠ A Csontszárny nappal néma — az árnyék-forma az éj leple alatt ébred.</gray>"));
                }
            }
            default -> {
            }
        }
    }

    /**
     * Csontszárny árnyék-forma (lore: "Éjjel a viselője maga is árnyékká válik"): amíg a
     * viselő ÉJJEL siklik, az árnyék-forma FOLYAMATOSAN fennáll — a korábbi, csak a
     * felszállás pillanatában adott 10 mp helyett a hurok 2 mp-enként frissíti a
     * láthatatlanság+gyorsaság effektet, és magától leáll, ha a repülés véget ér, felkel
     * a nap, vagy a szárny lekerül. A játékos SAJÁT entity-schedulerén fut (Folia-safe).
     */
    private void startShadeLoop(final Player player) {
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation(), 30, 0.5D, 0.5D, 0.5D, 0.03D);
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 0.6F, 0.6F);
        player.getScheduler().runAtFixedRate(plugin, task -> {
            if (!player.isOnline() || !player.isGliding() || !isNight(player)
                    || !"bone_wing".equals(resolveWornWing(player, false))) {
                task.cancel();
                return;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 4 * 20, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 4 * 20, 0, false, false, true));
            player.getWorld().spawnParticle(Particle.SOUL, player.getLocation(), 6, 0.4D, 0.4D, 0.4D, 0.02D);
        }, null, 1L, 40L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        final String wingId = resolveWornWing(player, false);
        if (wingId == null) {
            return;
        }

        switch (wingId) {
            case "phoenix_wing" -> {
                if (FIRE_CAUSES.contains(event.getCause())) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                    // Phoenix landing: a burst of flame ignites nearby enemies.
                    // Folia: idegen régió entitásához scheduler-hop (audit-javítás).
                    for (final Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), 4.0D, 2.0D, 4.0D)) {
                        if (nearby instanceof LivingEntity living && nearby != player) {
                            if (org.bukkit.Bukkit.isOwnedByCurrentRegion(living)) {
                                living.setFireTicks(Math.max(living.getFireTicks(), 60));
                            } else {
                                living.getScheduler().run(plugin, task ->
                                        living.setFireTicks(Math.max(living.getFireTicks(), 60)), null);
                            }
                        }
                    }
                    player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 30, 1.8D, 0.5D, 1.8D, 0.05D);
                    player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0F, 0.8F);
                }
            }
            case "frost_wing" -> {
                if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
                    event.setCancelled(true);
                }
            }
            case "wander_wind" -> {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
            }
            case "bone_wing" -> {
                if (event.getCause() == EntityDamageEvent.DamageCause.WITHER) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    /**
     * A frakció-szárnyat idegen frakció tagja fel sem veheti a földről (config-kapcsolható):
     * a szárny "elutasítja" — a tárgy a helyén marad, az inaktivitás-szabály előbb-utóbb
     * visszaveszi. A pickup-event a felvevő játékos régió-szálán fut (Folia-safe).
     */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(final org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final RelicDefinition definition = relicManager.identify(event.getItem().getItemStack());
        if (definition == null) {
            return;
        }
        final FactionType wingFaction = WING_FACTIONS.get(definition.id().toLowerCase(java.util.Locale.ROOT));
        if (wingFaction == null || factionManager.getFaction(player.getUniqueId()) == wingFaction) {
            return;
        }
        if (!configManager.getBoolean("relics.wings.faction-locked-pickup", true)) {
            return;
        }
        event.setCancelled(true);
        player.sendActionBar(messageManager.getMessage(
                "relic.wing-pickup-rejected",
                "<red>A(z) {faction} szárnya megperzseli az idegen kezét — nem emelheted fel.</red>",
                Map.of("faction", wingFaction.getDisplayName())));
    }

    /**
     * Identifies the worn elytra relic if (and only if) the wearer may benefit
     * from it: relic owner + matching faction.
     *
     * @param player the player
     * @param notifyOnMismatch whether to warn when the wearer fails the requirements
     * @return the wing relic id, or null
     */
    private String resolveWornWing(final Player player, final boolean notifyOnMismatch) {
        final ItemStack chestplate = player.getInventory().getChestplate();
        final RelicDefinition definition = relicManager.identify(chestplate);
        if (definition == null) {
            return null;
        }

        final FactionType wingFaction = WING_FACTIONS.get(definition.id().toLowerCase(java.util.Locale.ROOT));
        if (wingFaction == null) {
            return null;
        }

        if (!relicManager.canUse(player, chestplate)
                || factionManager.getFaction(player.getUniqueId()) != wingFaction) {
            if (notifyOnMismatch) {
                player.sendMessage(messageManager.getMessage(
                        "relic.wing-rejected",
                        "<red>A szárny nem ismer el gazdájának — csak a(z) {faction} frakció hű tagját szolgálja.</red>",
                        Map.of("faction", wingFaction.getDisplayName())
                ));
            }
            return null;
        }

        return definition.id().toLowerCase(java.util.Locale.ROOT);
    }

    /** A frost-nova cél-effektje (a cél SAJÁT régió-szálán fut). */
    private static void freezeTarget(final LivingEntity living) {
        living.setFreezeTicks(Math.max(living.getFreezeTicks(), 200));
        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 4 * 20, 1, false, true, true));
    }

    /**
     * Audit-javítás: a szárny konténerből (láda/hopper) sem vehető ki idegen frakció által —
     * a földi pickup-tiltás párja. A saját inventoryn belüli mozgatást nem érinti.
     */
    @EventHandler(ignoreCancelled = true)
    public void onContainerTake(final org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() == null
                || event.getClickedInventory() == player.getInventory()) {
            return;
        }
        final RelicDefinition definition = relicManager.identify(event.getCurrentItem());
        if (definition == null) {
            return;
        }
        final FactionType wingFaction = WING_FACTIONS.get(definition.id().toLowerCase(java.util.Locale.ROOT));
        if (wingFaction == null || factionManager.getFaction(player.getUniqueId()) == wingFaction
                || !configManager.getBoolean("relics.wings.faction-locked-pickup", true)) {
            return;
        }
        event.setCancelled(true);
        player.sendActionBar(messageManager.getMessage(
                "relic.wing-pickup-rejected",
                "<red>A(z) {faction} szárnya megperzseli az idegen kezét — nem emelheted fel.</red>",
                Map.of("faction", wingFaction.getDisplayName())));
    }

    private boolean isNight(final Player player) {
        // A vanília nap-fogalmát követjük (a korábbi kézi 13000-23000 ablak a hajnal előtti
        // órákat kihagyta — a lore "éjjel"-je a TELJES éjszaka). Nem-overworld dimenzióban
        // (Nether/End) az idő áll: ott az árnyék-forma mindig él (örök félhomály).
        final org.bukkit.World world = player.getWorld();
        return world.getEnvironment() != org.bukkit.World.Environment.NORMAL || !world.isDayTime();
    }
}
