package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-class "power" resource shown as a coloured bar <b>in the HUD sidebar</b> (a scoreboard line —
 * deliberately NOT a separate boss bar, so it never stacks with the world-boss bar). A 0–max meter,
 * named and coloured per class (Mana / Düh / Energia / Runikus Erő / Csi …): casting a spell BUILDS
 * it, and once full it DISCHARGES into a short empowerment (strength + speed) and resets to 0.
 *
 * <p>This is an <b>additive reward layer</b> on top of the existing spell costs — it never blocks or
 * breaks a cast. {@link #onSpellCast} runs on the caster's region thread (cast event); the HUD reads
 * {@link #hudLine} on its own scoreboard tick (≤1s lag, and the discharge also flashes the action bar).
 */
public final class ResourceManager implements PlayerStateCleanup {

    /** Combat role — drives the empowerment. Determined by SPEC (falls back to class before specialising). */
    private enum Role { MELEE_DPS, RANGED_DPS, CASTER, TANK, HEALER }

    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final SpecializationManager specializationManager;
    private final Map<UUID, Integer> resource = new ConcurrentHashMap<>();
    /** When the player's post-discharge "empowered" window (faster casting) ends, in epoch millis. */
    private final Map<UUID, Long> empoweredUntil = new ConcurrentHashMap<>();

    public ResourceManager(final JavaPlugin plugin, final ConfigManager configManager, final JobManager jobManager,
                           final SpecializationManager specializationManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.specializationManager = specializationManager;
    }

    private boolean enabled() {
        return configManager.getBoolean("spells.resource.enabled", true);
    }

    private int max() {
        return Math.max(10, configManager.getInt("spells.resource.max", 100));
    }

    private int gain() {
        return Math.max(1, configManager.getInt("spells.resource.gain-per-cast", 25));
    }

    /** Builds the resource after a successful cast; discharges into an empowerment at full. */
    public void onSpellCast(final Player player) {
        if (!enabled()) {
            return;
        }
        final int value = resource.merge(player.getUniqueId(), gain(), Integer::sum);
        if (value >= max()) {
            resource.put(player.getUniqueId(), 0);
            discharge(player);
        }
    }

    /**
     * Discharges into a CLASS-FLAVOURED empowerment (the depth comes from <i>what</i> the burst does
     * per role, not from any spell-switching rotation — so it fits Minecraft's catalyst-cycle casting):
     * dps classes burst (strength/speed), tanky classes shield (resistance/absorption), and support
     * classes (Pap/Druida/Paplovag/Sárkányidéző) also pulse-heal nearby allies.
     */
    /** True during the ~6s after a discharge — the cast pipeline grants a cooldown refund (faster casting). */
    public boolean isEmpowered(final Player player) {
        return empoweredUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    private void discharge(final Player player) {
        final int duration = 6 * 20;
        empoweredUntil.put(player.getUniqueId(), System.currentTimeMillis() + (duration * 50L));
        final Role role = roleFor(player);
        for (final PotionEffect effect : empowermentEffects(role, duration)) {
            player.addPotionEffect(effect);
        }
        // SPEC-specific signature: a thematic effect on nearby entities + spec particle/sound, so each
        // of the 26 specs' discharge plays differently (fire specs ignite, frost freeze, healers AoE-heal…).
        final Signature signature = signatureFor(specializationManager.getClassSpecialization(player), role);
        applyNearby(player, signature.nearby());
        player.getWorld().spawnParticle(signature.particle(), player.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.4D, 0.6D, 0.4D, 0.1D);
        player.getWorld().playSound(player.getLocation(), signature.sound(), 1.0F, 1.4F);
        player.sendActionBar(Component.text("⚡ " + nameFor(player) + " kirobban!", NamedTextColor.GOLD));
    }

    /** What a discharge does to nearby entities (the spec's signature). */
    private enum Nearby { NONE, HEAL_ALLIES, IGNITE_ENEMIES, FREEZE_ENEMIES, WITHER_ENEMIES, KNOCKBACK }

    private record Signature(Nearby nearby, Particle particle, Sound sound) {
    }

    private Signature signatureFor(final SpecializationType spec, final Role role) {
        if (spec == null) {
            return switch (role) {
                case HEALER -> new Signature(Nearby.HEAL_ALLIES, Particle.HEART, Sound.BLOCK_BELL_RESONATE);
                case CASTER -> new Signature(Nearby.NONE, Particle.END_ROD, Sound.BLOCK_BEACON_ACTIVATE);
                case TANK -> new Signature(Nearby.NONE, Particle.CRIT, Sound.ITEM_SHIELD_BLOCK);
                case RANGED_DPS -> new Signature(Nearby.NONE, Particle.ELECTRIC_SPARK, Sound.ENTITY_BREEZE_SHOOT);
                case MELEE_DPS -> new Signature(Nearby.NONE, Particle.CRIT, Sound.ENTITY_PLAYER_ATTACK_STRONG);
            };
        }
        return switch (spec) {
            case ELEMENTALIST, DESTRUCTION, DEVASTATION ->
                    new Signature(Nearby.IGNITE_ENEMIES, Particle.FLAME, Sound.ITEM_FIRECHARGE_USE);
            case RETRIBUTION -> new Signature(Nearby.IGNITE_ENEMIES, Particle.END_ROD, Sound.BLOCK_BEACON_ACTIVATE);
            case LUNAR -> new Signature(Nearby.IGNITE_ENEMIES, Particle.END_ROD, Sound.BLOCK_AMETHYST_BLOCK_CHIME);
            case ELEMENTAL -> new Signature(Nearby.IGNITE_ENEMIES, Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_THUNDER);
            case FROST -> new Signature(Nearby.FREEZE_ENEMIES, Particle.SNOWFLAKE, Sound.BLOCK_GLASS_BREAK);
            case NECROMANCER, AFFLICTION, SHADOW, POISONER, PHANTOM ->
                    new Signature(Nearby.WITHER_ENEMIES, Particle.SCULK_SOUL, Sound.ENTITY_WITHER_AMBIENT);
            case BERSERKER, FERAL, ENHANCEMENT, HAVOC, WINDWALKER ->
                    new Signature(Nearby.KNOCKBACK, Particle.CRIT, Sound.ENTITY_RAVAGER_ROAR);
            case HOLY, DISCIPLINE, PRESERVATION, RESTORATION, TIDAL, MISTWEAVER ->
                    new Signature(Nearby.HEAL_ALLIES, Particle.HEART, Sound.BLOCK_BELL_RESONATE);
            case GUARDIAN, BLOOD, BREWMASTER, VENGEANCE, IRONBARK, PROTECTION ->
                    new Signature(Nearby.NONE, Particle.CRIT, Sound.ITEM_SHIELD_BLOCK);
            case SHARPSHOOTER, BEAST_MASTER -> new Signature(Nearby.NONE, Particle.ELECTRIC_SPARK, Sound.ENTITY_BREEZE_SHOOT);
            default -> new Signature(Nearby.NONE, Particle.CRIT, Sound.ENTITY_PLAYER_ATTACK_STRONG);
        };
    }

    /** Applies the signature to nearby entities — region-local on the caster's thread, so Folia-safe. */
    private void applyNearby(final Player player, final Nearby nearby) {
        if (nearby == Nearby.NONE) {
            return;
        }
        if (nearby == Nearby.HEAL_ALLIES) {
            healNearbyAllies(player);
            return;
        }
        for (final org.bukkit.entity.Entity entity : player.getNearbyEntities(6.0D, 6.0D, 6.0D)) {
            if (!(entity instanceof org.bukkit.entity.Monster monster)) {
                continue;
            }
            switch (nearby) {
                case IGNITE_ENEMIES -> monster.setFireTicks(Math.max(monster.getFireTicks(), 80));
                case FREEZE_ENEMIES -> monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5 * 20, 2, false, false, true));
                case WITHER_ENEMIES -> monster.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 5 * 20, 0, false, false, true));
                case KNOCKBACK -> {
                    final org.bukkit.util.Vector kb = monster.getLocation().toVector().subtract(player.getLocation().toVector());
                    if (kb.lengthSquared() > 0.0D) {
                        monster.setVelocity(kb.normalize().setY(0.4D).multiply(0.8D));
                    }
                }
                default -> {
                }
            }
        }
    }

    /**
     * The player's combat role — from the chosen specialization (so a Retribution paladin bursts but a
     * Holy paladin heals, a Blood DK shields but a Frost DK bursts…). Before specialising, falls back to
     * a sensible class default.
     */
    private Role roleFor(final Player player) {
        final SpecializationType spec = specializationManager.getClassSpecialization(player);
        if (spec != null) {
            return switch (spec) {
                case GUARDIAN, BLOOD, BREWMASTER, VENGEANCE, IRONBARK, PROTECTION -> Role.TANK;
                case HOLY, DISCIPLINE, PRESERVATION, RESTORATION, TIDAL, MISTWEAVER -> Role.HEALER;
                case ELEMENTALIST, NECROMANCER, LUNAR, ELEMENTAL, SHADOW, AFFLICTION, DESTRUCTION, DEVASTATION -> Role.CASTER;
                case SHARPSHOOTER, BEAST_MASTER -> Role.RANGED_DPS;
                // berserker, poisoner, phantom, feral, retribution, frost, enhancement, windwalker, havoc
                default -> Role.MELEE_DPS;
            };
        }
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) {
            return Role.MELEE_DPS;
        }
        return switch (job) {
            case WIZARD, WARLOCK -> Role.CASTER;
            case ARCHER -> Role.RANGED_DPS;
            case PRIEST -> Role.HEALER;
            default -> Role.MELEE_DPS;
        };
    }

    private java.util.List<PotionEffect> empowermentEffects(final Role role, final int duration) {
        final PotionEffect strength1 = new PotionEffect(PotionEffectType.STRENGTH, duration, 0, false, false, true);
        final PotionEffect strength2 = new PotionEffect(PotionEffectType.STRENGTH, duration, 1, false, false, true);
        final PotionEffect speed1 = new PotionEffect(PotionEffectType.SPEED, duration, 0, false, false, true);
        final PotionEffect speed2 = new PotionEffect(PotionEffectType.SPEED, duration, 1, false, false, true);
        final PotionEffect resistance = new PotionEffect(PotionEffectType.RESISTANCE, duration, 1, false, false, true);
        final PotionEffect absorption = new PotionEffect(PotionEffectType.ABSORPTION, duration, 1, false, false, true);
        final PotionEffect regen = new PotionEffect(PotionEffectType.REGENERATION, duration, 1, false, false, true);
        return switch (role) {
            case TANK -> java.util.List.of(resistance, absorption);          // shield
            case HEALER -> java.util.List.of(regen, absorption);             // sustain (+ AoE heal)
            case CASTER -> java.util.List.of(speed1, regen);                 // kite + sustain
            case RANGED_DPS -> java.util.List.of(speed2, strength1);         // kiting burst
            default -> java.util.List.of(strength2, speed1);                 // MELEE_DPS burst
        };
    }

    /** Support discharge: pulse-heal (regen) the caster and nearby allies — region-local, Folia-safe. */
    private void healNearbyAllies(final Player player) {
        final PotionEffect heal = new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 1, false, false, true);
        player.addPotionEffect(heal);
        for (final org.bukkit.entity.Entity nearby : player.getNearbyEntities(8.0D, 8.0D, 8.0D)) {
            if (nearby instanceof Player ally) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 1, false, false, true));
            }
        }
    }

    /**
     * The HUD sidebar line for this player's resource: a coloured 10-segment bar plus the value, or
     * {@code null} when the system is disabled. Read by {@code HudManager} on its scoreboard tick.
     *
     * @param player the viewer
     * @return the rendered line, or null
     */
    public Component hudLine(final Player player) {
        if (!enabled()) {
            return null;
        }
        final int maxValue = max();
        final int value = Math.max(0, Math.min(maxValue, resource.getOrDefault(player.getUniqueId(), 0)));
        final int filled = Math.round(value / (float) maxValue * 10.0F);
        final NamedTextColor color = colorFor(player);
        Component bar = Component.text(nameFor(player) + " ", NamedTextColor.GRAY);
        for (int i = 0; i < 10; i++) {
            bar = bar.append(Component.text("▰", i < filled ? color : NamedTextColor.DARK_GRAY));
        }
        return bar.append(Component.text(" " + value, NamedTextColor.WHITE));
    }

    private String nameFor(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) {
            return "Erő";
        }
        return switch (job) {
            case WARRIOR -> "Düh";
            case ARCHER -> "Fókusz";
            case ASSASSIN -> "Energia";
            case DEATH_KNIGHT -> "Runikus Erő";
            case MONK -> "Csi";
            case WARLOCK -> "Lélekerő";
            case DEMON_HUNTER -> "Fúria";
            case PALADIN -> "Szent Erő";
            case DRUID -> "Természeti Erő";
            case EVOKER -> "Eszencia";
            default -> "Mana"; // Varázsló, Sámán, Pap
        };
    }

    private NamedTextColor colorFor(final Player player) {
        final JobType job = jobManager.getPrimaryJob(player);
        if (job == null) {
            return NamedTextColor.LIGHT_PURPLE;
        }
        return switch (job) {
            case WARRIOR, DEATH_KNIGHT -> NamedTextColor.RED;
            case ARCHER, DRUID -> NamedTextColor.GREEN;
            case ASSASSIN, MONK, PALADIN -> NamedTextColor.YELLOW;
            case WARLOCK, DEMON_HUNTER, EVOKER -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.AQUA; // Varázsló, Sámán, Pap
        };
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        resource.remove(playerId);
        empoweredUntil.remove(playerId);
    }
}
