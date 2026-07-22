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
    /** E7 — setter-injektált: Varázsló rúnaíró affinitás (dupla rúna-hatás). */
    private volatile hu.taliann.icesmp.managers.JobManager jobManager;

    public RuneEffectListener(final ConfigManager configManager) {
        this.configManager = configManager;
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
    public static String runeOf(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(RuneApplyListener.RUNE_PDC_KEY, PersistentDataType.STRING);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMelee(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            final String rune = runeOf(attacker.getInventory().getItemInMainHand());
            if (rune != null && event.getEntity() instanceof LivingEntity victim) {
                applyWeaponRune(rune, event, victim, affinity(attacker));
            }
        } else if (event.getDamager() instanceof Projectile projectile) {
            final String rune = projectile.getPersistentDataContainer()
                    .get(RuneApplyListener.RUNE_PDC_KEY, PersistentDataType.STRING);
            if ("runa_zapor".equals(rune)) {
                final double bonus = pct("runes.runa_zapor.projectile-damage-percent", 7.0D);
                event.setDamage(event.getDamage() * (1.0D + bonus / 100.0D));
            }
        }
        // runa_bastya: a sértett játékos mellvértje csillapít
        if (event.getEntity() instanceof Player victim) {
            final String rune = runeOf(victim.getInventory().getChestplate());
            if ("runa_bastya".equals(rune)) {
                final double reduction = pct("runes.runa_bastya.damage-reduction-percent", 4.0D) * affinity(victim);
                event.setDamage(Math.max(0.0D, event.getDamage() * (1.0D - reduction / 100.0D)));
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
            default -> { }
        }
    }

    /** Lövéskor a nyíl örökli az íj rúnáját — a sebzés-event a nyílból olvas. */
    @EventHandler(ignoreCancelled = true)
    public void onShoot(final EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        final String rune = runeOf(event.getBow());
        if ("runa_zapor".equals(rune) && event.getProjectile() instanceof Projectile projectile) {
            projectile.getPersistentDataContainer().set(
                    RuneApplyListener.RUNE_PDC_KEY, PersistentDataType.STRING, rune);
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
