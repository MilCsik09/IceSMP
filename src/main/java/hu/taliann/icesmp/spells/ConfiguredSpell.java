package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Declaratively configured spell used by the SpellCatalog to define the large
 * themed spell pools without one class per spell. Supports three targeting
 * modes (self, ray-traced target, area) and composable instant effects:
 * damage, self-sacrifice, healing, feeding, ignite/freeze, lightning visuals,
 * knockback/launch/pull, dashes and potion effect lists. Everything resolves
 * immediately on the caster's region thread — no scheduled tasks (Folia-safe).
 */
public final class ConfiguredSpell extends BaseSpell {

    public enum Targeting { SELF, TARGET, AOE }

    private static final int FREEZE_BASE_TICKS = 140;

    private final Targeting targeting;
    private final double range;
    private final double radius;
    private final boolean friendlyAoe;
    private final double damage;
    private final double selfDamage;
    private final double healSelf;
    private final int feedSelf;
    private final int igniteTicks;
    private final int freezeTicks;
    private final boolean lightning;
    private final double knockback;
    private final double launchUp;
    private final double pullStrength;
    private final double dashForward;
    private final double dashUp;
    private final List<PotionEffect> targetEffects;
    private final List<PotionEffect> selfEffects;
    private final Particle particle;
    private final int particleCount;
    private final Sound sound;
    private final float soundVolume;
    private final float soundPitch;

    private ConfiguredSpell(final Builder builder) {
        super(builder.messageManager, builder.id, builder.defaultName, builder.cooldown, builder.costType, builder.costAmount);
        this.targeting = builder.targeting;
        this.range = builder.range;
        this.radius = builder.radius;
        this.friendlyAoe = builder.friendlyAoe;
        this.damage = builder.damage;
        this.selfDamage = builder.selfDamage;
        this.healSelf = builder.healSelf;
        this.feedSelf = builder.feedSelf;
        this.igniteTicks = builder.igniteTicks;
        this.freezeTicks = builder.freezeTicks;
        this.lightning = builder.lightning;
        this.knockback = builder.knockback;
        this.launchUp = builder.launchUp;
        this.pullStrength = builder.pullStrength;
        this.dashForward = builder.dashForward;
        this.dashUp = builder.dashUp;
        this.targetEffects = List.copyOf(builder.targetEffects);
        this.selfEffects = List.copyOf(builder.selfEffects);
        this.particle = builder.particle;
        this.particleCount = builder.particleCount;
        this.sound = builder.sound;
        this.soundVolume = builder.soundVolume;
        this.soundPitch = builder.soundPitch;
    }

    public static Builder builder(final MessageManager messageManager, final String id, final String defaultName,
                                  final int cooldown, final SpellCostType costType, final int costAmount) {
        return new Builder(messageManager, id, defaultName, cooldown, costType, costAmount);
    }

    @Override
    public void execute(final Player player) {
        executeSpell(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        return executeSpell(player, 1.0D);
    }

    /**
     * Casts with a spell-mastery power multiplier (1.0 = base). Higher mastery
     * ranks scale the offensive output — damage, self-heal and the DURATION of
     * the applied potion effects — while costs and self-damage stay at base.
     *
     * @param player the caster
     * @param power the mastery power multiplier (>= 1.0)
     * @return true if the spell's effect fired
     */
    @Override
    public boolean executeSpell(final Player player, final double power) {
        return switch (targeting) {
            case SELF -> {
                executeSelf(player, power);
                yield true;
            }
            case TARGET -> executeTarget(player, power);
            case AOE -> {
                executeAoe(player, power);
                yield true;
            }
        };
    }

    private void executeSelf(final Player player, final double power) {
        applySelf(player, power);
        playFeedback(player, player.getLocation());
    }

    private boolean executeTarget(final Player player, final double power) {
        final LivingEntity target = SpellTargetingUtil.rayTraceLivingEntity(player, range);
        if (target == null) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a látómeződben."));
            return false;
        }

        affect(player, target, power);
        applySelf(player, power);
        playFeedback(player, target.getLocation());
        return true;
    }

    private void executeAoe(final Player player, final double power) {
        for (final Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity == player) {
                continue;
            }

            if (friendlyAoe && !(living instanceof Player)) {
                continue;
            }

            affect(player, living, power);
        }

        applySelf(player, power);
        playFeedback(player, player.getLocation());
    }

    @Override
    public List<String> describe() {
        final List<String> lines = new ArrayList<>();
        lines.add(switch (targeting) {
            case SELF -> "Cél: önmagad";
            case TARGET -> "Cél: célzott lény (hatótáv " + trim(range) + ")";
            case AOE -> "Cél: körzet (sugár " + trim(radius) + ")" + (friendlyAoe ? ", csak szövetségesek" : "");
        });
        if (damage > 0.0D) {
            lines.add("Sebzés: " + trim(damage));
        }
        if (selfDamage > 0.0D) {
            lines.add("Önsebzés: " + trim(selfDamage));
        }
        if (healSelf > 0.0D) {
            lines.add("Gyógyítás: " + trim(healSelf));
        }
        if (feedSelf > 0) {
            lines.add("Jóllakottság: +" + feedSelf);
        }
        if (igniteTicks > 0) {
            lines.add("Gyújtás: " + secondsOf(igniteTicks) + " mp");
        }
        if (freezeTicks > 0) {
            lines.add("Fagyasztás: " + secondsOf(FREEZE_BASE_TICKS + freezeTicks) + " mp");
        }
        if (lightning) {
            lines.add("Villámcsapás");
        }
        if (knockback > 0.0D) {
            lines.add("Hátralökés");
        }
        if (launchUp > 0.0D) {
            lines.add("Fellökés a levegőbe");
        }
        if (pullStrength > 0.0D) {
            lines.add("Behúzás magadhoz");
        }
        if (dashForward != 0.0D || dashUp != 0.0D) {
            lines.add("Kitörés / lendület");
        }
        for (final PotionEffect effect : targetEffects) {
            lines.add("Célra: " + effectName(effect));
        }
        for (final PotionEffect effect : selfEffects) {
            lines.add("Magadra: " + effectName(effect));
        }
        return lines;
    }

    private static String trim(final double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static int secondsOf(final int ticks) {
        return Math.max(1, ticks / 20);
    }

    private static String effectName(final PotionEffect effect) {
        final String type = effect.getType().getKey().getKey().replace('_', ' ');
        final int level = effect.getAmplifier() + 1;
        return type + " " + level + " (" + secondsOf(effect.getDuration()) + " mp)";
    }

    /** Rebuilds a potion effect with its duration scaled by the mastery power (1.0 = unchanged). */
    private static PotionEffect scaledDuration(final PotionEffect effect, final double power) {
        if (power <= 1.0D) {
            return effect;
        }
        final int scaledTicks = Math.max(1, (int) Math.round(effect.getDuration() * power));
        return new PotionEffect(effect.getType(), scaledTicks, effect.getAmplifier(),
                effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
    }

    private void affect(final Player caster, final LivingEntity target, final double power) {
        if (lightning) {
            target.getWorld().strikeLightningEffect(target.getLocation());
        }

        if (damage > 0.0D) {
            target.damage(damage * power, caster);
        }

        for (final PotionEffect effect : targetEffects) {
            target.addPotionEffect(scaledDuration(effect, power));
        }

        if (igniteTicks > 0) {
            target.setFireTicks(Math.max(target.getFireTicks(), igniteTicks));
        }

        if (freezeTicks > 0) {
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), FREEZE_BASE_TICKS + freezeTicks));
        }

        if (knockback > 0.0D) {
            final Vector away = target.getLocation().toVector().subtract(caster.getLocation().toVector()).setY(0.0D);
            if (away.lengthSquared() > 1.0E-4D) {
                target.setVelocity(away.normalize().multiply(knockback).setY(0.3D));
            }
        }

        if (launchUp > 0.0D) {
            target.setVelocity(target.getVelocity().setY(launchUp));
        }

        if (pullStrength > 0.0D) {
            final Vector toward = caster.getLocation().toVector().subtract(target.getLocation().toVector());
            if (toward.lengthSquared() > 1.0E-4D) {
                target.setVelocity(toward.normalize().multiply(pullStrength).setY(0.25D));
            }
        }
    }

    private void applySelf(final Player player, final double power) {
        for (final PotionEffect effect : selfEffects) {
            player.addPotionEffect(scaledDuration(effect, power));
        }

        // Self-damage is a cost, not offensive output — it stays at base regardless of mastery.
        if (selfDamage > 0.0D) {
            player.damage(selfDamage);
        }

        if (healSelf > 0.0D) {
            final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                player.setHealth(Math.min(maxHealth.getValue(), player.getHealth() + (healSelf * power)));
            }
        }

        if (feedSelf > 0) {
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + feedSelf));
        }

        if (dashForward != 0.0D || dashUp != 0.0D) {
            Vector velocity = player.getLocation().getDirection().setY(0.0D);
            if (velocity.lengthSquared() > 1.0E-4D) {
                velocity = velocity.normalize().multiply(dashForward);
            } else {
                velocity = new Vector();
            }
            velocity.setY(dashUp);
            player.setVelocity(velocity);
        }
    }

    private void playFeedback(final Player player, final Location focus) {
        if (particle != null) {
            player.getWorld().spawnParticle(particle, focus.clone().add(0.0D, 1.0D, 0.0D),
                    particleCount, radius > 0.0D ? radius / 2.0D : 0.4D, 0.6D, radius > 0.0D ? radius / 2.0D : 0.4D, 0.02D);
        }

        if (sound != null) {
            player.getWorld().playSound(focus, sound, soundVolume, soundPitch);
        }
    }

    public static final class Builder {

        private final MessageManager messageManager;
        private final String id;
        private final String defaultName;
        private final int cooldown;
        private final SpellCostType costType;
        private final int costAmount;

        private Targeting targeting = Targeting.SELF;
        private double range = 10.0D;
        private double radius;
        private boolean friendlyAoe;
        private double damage;
        private double selfDamage;
        private double healSelf;
        private int feedSelf;
        private int igniteTicks;
        private int freezeTicks;
        private boolean lightning;
        private double knockback;
        private double launchUp;
        private double pullStrength;
        private double dashForward;
        private double dashUp;
        private final List<PotionEffect> targetEffects = new ArrayList<>();
        private final List<PotionEffect> selfEffects = new ArrayList<>();
        private Particle particle;
        private int particleCount = 25;
        private Sound sound;
        private float soundVolume = 1.0F;
        private float soundPitch = 1.0F;

        private Builder(final MessageManager messageManager, final String id, final String defaultName,
                        final int cooldown, final SpellCostType costType, final int costAmount) {
            this.messageManager = messageManager;
            this.id = id;
            this.defaultName = defaultName;
            this.cooldown = cooldown;
            this.costType = costType;
            this.costAmount = costAmount;
        }

        public Builder self() {
            this.targeting = Targeting.SELF;
            return this;
        }

        public Builder target(final double targetRange) {
            this.targeting = Targeting.TARGET;
            this.range = targetRange;
            return this;
        }

        public Builder aoe(final double aoeRadius) {
            this.targeting = Targeting.AOE;
            this.radius = aoeRadius;
            return this;
        }

        /** Restricts the AOE to players (used for support auras that buff nearby allies). */
        public Builder friendly() {
            this.friendlyAoe = true;
            return this;
        }

        public Builder damage(final double amount) {
            this.damage = amount;
            return this;
        }

        public Builder selfDamage(final double amount) {
            this.selfDamage = amount;
            return this;
        }

        public Builder healSelf(final double amount) {
            this.healSelf = amount;
            return this;
        }

        public Builder feedSelf(final int amount) {
            this.feedSelf = amount;
            return this;
        }

        public Builder ignite(final int ticks) {
            this.igniteTicks = ticks;
            return this;
        }

        public Builder freeze(final int extraTicks) {
            this.freezeTicks = extraTicks;
            return this;
        }

        public Builder lightning() {
            this.lightning = true;
            return this;
        }

        public Builder knockback(final double strength) {
            this.knockback = strength;
            return this;
        }

        public Builder launchUp(final double strength) {
            this.launchUp = strength;
            return this;
        }

        public Builder pull(final double strength) {
            this.pullStrength = strength;
            return this;
        }

        public Builder dash(final double forward, final double up) {
            this.dashForward = forward;
            this.dashUp = up;
            return this;
        }

        public Builder targetEffect(final PotionEffectType type, final int ticks, final int amplifier) {
            this.targetEffects.add(new PotionEffect(type, ticks, amplifier, false, true, true));
            return this;
        }

        public Builder selfEffect(final PotionEffectType type, final int ticks, final int amplifier) {
            this.selfEffects.add(new PotionEffect(type, ticks, amplifier, false, false, true));
            return this;
        }

        public Builder particle(final Particle effectParticle, final int count) {
            this.particle = effectParticle;
            this.particleCount = count;
            return this;
        }

        public Builder sound(final Sound effectSound, final float volume, final float pitch) {
            this.sound = effectSound;
            this.soundVolume = volume;
            this.soundPitch = pitch;
            return this;
        }

        public ConfiguredSpell build() {
            return new ConfiguredSpell(this);
        }
    }
}
