package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;
import java.util.List;

/**
 * B26 — rúna-hatások, SZŰK közös hook-pontokon (nem spellenként szórva):
 * <ul>
 *   <li>közelharci találat (runa_elek +sebzés%, runa_lang gyújtás-esély,
 *       runa_fagy lassítás-esély) — a támadó fegyverének PDC-jéből;</li>
 *   <li>lövedék-találat (runa_zapor +sebzés%) — lövéskor a nyíl PDC-t örököl
 *       az íj rúnájából, a sebzés-event a nyílból olvas (a fegyver-váltás trükk
 *       így nem játszható ki);</li>
 *   <li>kapott sebzés (runa_bastya -sebzés%) — a viselt mellvért PDC-jéből.</li>
 * </ul>
 * A Mohóság rúnáját a MobMoneyDropListener kezeli (drop-esély bónusz).
 * Minden érték élő config (runes.<id>.*). Folia: minden ág az esemény saját
 * régió-szálán fut; a célpont maga az event-entitás, így az effekt-adás biztonságos.
 */
public final class RuneEffectListener implements Listener {

    private final ConfigManager configManager;
    private final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService;
    private final hu.taliann.icesmp.itemization.EquipmentProficiencyService proficiency;
    private static final org.bukkit.NamespacedKey RUNE_LIST_PDC_KEY =
            org.bukkit.NamespacedKey.fromString("icesmp:rune_effects");
    /** E7 — setter-injektált: Varázsló rúnaíró affinitás (dupla rúna-hatás). */
    private volatile hu.taliann.icesmp.managers.JobManager jobManager;

    public RuneEffectListener(final ConfigManager configManager,
                              final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService,
                              final hu.taliann.icesmp.itemization.EquipmentProficiencyService proficiency) {
        this.configManager = configManager;
        this.itemIdentityService = itemIdentityService;
        this.proficiency = proficiency;
    }

    public void setJobManager(final hu.taliann.icesmp.managers.JobManager jobManager) {
        this.jobManager = jobManager;
    }

    /** E7 — a Varázsló „olvassa” a rúnákat: hatás-szorzó (alap 2.0, élő config). */
    private double affinity(final Player player) {
        final hu.taliann.icesmp.managers.JobManager jobRef = jobManager;
        if (jobRef != null && jobRef.getPrimaryJob(player) == hu.taliann.icesmp.data.JobType.WIZARD) {
            return Math.max(1.0D, configManager.getDouble("runes.wizard-affinity-multiplier", 2.0D));
        }
        return 1.0D;
    }

    /** A tárgyra vésett rúna id-ja (null, ha nincs). */
    public List<String> runesOf(final ItemStack item) {
        return itemIdentityService.runesOf(item);
    }

    private List<String> activeRunes(final Player player, final ItemStack item,
                                     final hu.taliann.icesmp.itemization.ItemTemplate.Slot slot) {
        final var inspection = itemIdentityService.inspect(item);
        if (inspection.status()
                == hu.taliann.icesmp.itemization.ItemIdentityService.Status.NOT_MANAGED) {
            return runesOf(item);
        }
        return proficiency.isActive(player, item, slot) ? runesOf(item) : List.of();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMelee(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            final List<String> runes = activeRunes(attacker,
                    attacker.getInventory().getItemInMainHand(),
                    hu.taliann.icesmp.itemization.ItemTemplate.Slot.MAIN_HAND);
            if (!runes.isEmpty() && event.getEntity() instanceof LivingEntity victim) {
                for (final String rune : runes) applyWeaponRune(rune, event, victim, affinity(attacker));
            }
        } else if (event.getDamager() instanceof Projectile projectile) {
            final String runes = projectile.getPersistentDataContainer()
                    .get(RUNE_LIST_PDC_KEY, PersistentDataType.STRING);
            if (runes != null && java.util.Arrays.asList(runes.split(",")).contains("runa_zapor")) {
                final double bonus = pct("runes.runa_zapor.projectile-damage-percent", 7.0D);
                event.setDamage(event.getDamage() * (1.0D + bonus / 100.0D));
            }
            if (runes != null && java.util.Arrays.asList(runes.split(",")).contains("runa_vadasz")
                    && event.getEntity() instanceof LivingEntity living
                    && !(living instanceof Player)) {
                final double bonus = pct("runes.runa_vadasz.monster-damage-percent", 5.0D);
                event.setDamage(event.getDamage() * (1.0D + bonus / 100.0D));
            }
        }
        // runa_bastya: a sértett játékos mellvértje csillapít
        if (event.getEntity() instanceof Player victim) {
            final List<String> chestRunes = activeRunes(victim,
                    victim.getInventory().getChestplate(),
                    hu.taliann.icesmp.itemization.ItemTemplate.Slot.CHEST);
            if (chestRunes.contains("runa_bastya")) {
                final double reduction = pct("runes.runa_bastya.damage-reduction-percent", 4.0D) * affinity(victim);
                event.setDamage(Math.max(0.0D, event.getDamage() * (1.0D - reduction / 100.0D)));
            }
            if (chestRunes.contains("runa_oltalom")
                    && victim.getHealth() <= victim.getMaxHealth() * 0.35D) {
                final double reduction = pct(
                        "runes.runa_oltalom.low-health-reduction-percent", 6.0D) * affinity(victim);
                event.setDamage(Math.max(0.0D,
                        event.getDamage() * (1.0D - Math.min(40.0D, reduction) / 100.0D)));
            }
        }
    }

    private void applyWeaponRune(final String rune, final EntityDamageByEntityEvent event,
                                 final LivingEntity victim, final double affinity) {
        switch (rune) {
            case "runa_elek" -> {
                final double bonus = pct("runes.runa_elek.melee-damage-percent", 5.0D) * affinity;
                event.setDamage(event.getDamage() * (1.0D + bonus / 100.0D));
            }
            case "runa_visszhang" -> {
                // Varázsló-exkluzív rúna: kis esély visszhang-csapásra (bónusz-sebzés).
                if (roll("runes.runa_visszhang.chance", 0.08D * (affinity > 1.0D ? 1.5D : 1.0D))) {
                    final double bonus = pct("runes.runa_visszhang.echo-damage-percent", 30.0D);
                    event.setDamage(event.getDamage() * (1.0D + bonus / 100.0D));
                    victim.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT,
                            victim.getLocation().clone().add(0.0D, 1.0D, 0.0D), 20, 0.4D, 0.6D, 0.4D, 0.05D);
                }
            }
            case "runa_lang" -> {
                if (roll("runes.runa_lang.chance", 0.10D * affinity)) {
                    victim.setFireTicks(Math.max(victim.getFireTicks(),
                            configManager.getInt("runes.runa_lang.fire-ticks", 40)));
                }
            }
            case "runa_fagy" -> {
                if (roll("runes.runa_fagy.chance", 0.10D * affinity)) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            configManager.getInt("runes.runa_fagy.slow-ticks", 40), 0, false, true));
                }
            }
            case "runa_suly" -> {
                if (victim.getMaxHealth() >= configManager.getDouble(
                        "runes.runa_suly.minimum-target-health", 40.0D)) {
                    final double bonus = pct("runes.runa_suly.large-target-damage-percent", 4.0D)
                            * affinity;
                    event.setDamage(event.getDamage() * (1.0D + bonus / 100.0D));
                }
            }
            default -> { }
        }
    }

    /** Lövéskor a nyíl örökli az íj rúnáját — a sebzés-event a nyílból olvas. */
    @EventHandler(ignoreCancelled = true)
    public void onShoot(final EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final List<String> runes = activeRunes(player, event.getBow(),
                hu.taliann.icesmp.itemization.ItemTemplate.Slot.MAIN_HAND);
        if (!runes.isEmpty() && event.getProjectile() instanceof Projectile projectile) {
            projectile.getPersistentDataContainer().set(
                    RUNE_LIST_PDC_KEY, PersistentDataType.STRING, String.join(",", runes));
        }
    }

    private double pct(final String path, final double fallback) {
        return Math.max(0.0D, configManager.getDouble(path, fallback));
    }

    private boolean roll(final String path, final double fallback) {
        final double chance = Math.max(0.0D, Math.min(1.0D, configManager.getDouble(path, fallback)));
        return ThreadLocalRandom.current().nextDouble() < chance;
    }
}
