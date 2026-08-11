package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Declaratively configured spell used by the SpellCatalog for genuinely simple
 * utility, self, targeted and area effects. Signature/stateful gameplay stays in
 * dedicated Java spell classes; this class provides shared primitives rather
 * than hiding a spell-specific lifecycle in a generic DSL.
 *
 * <p>Everything resolves immediately on the caster's region thread. Magnitude
 * and duration outputs use {@link CastModifiers} by semantic category: standard
 * spell power scales damage/healing, while hard CC and effect durations remain
 * unchanged unless an explicit duration modifier is supplied.</p>
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
    private final hu.taliann.icesmp.utils.SpellVfx.Shape vfxShape;
    private final hu.taliann.icesmp.utils.SpellVfx.Palette vfxPalette;

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
        this.vfxShape = builder.vfxShape;
        this.vfxPalette = builder.vfxPalette;
    }

    public static Builder builder(final MessageManager messageManager, final String id, final String defaultName,
                                  final int cooldown, final SpellCostType costType, final int costAmount) {
        return new Builder(messageManager, id, defaultName, cooldown, costType, costAmount);
    }

    /**
     * Reports live balance overrides without rebuilding/re-registering the spell.
     * Every supported override is already read at cast time through {@link BaseSpell},
     * so the previous startup copy was redundant and silently replaced the same
     * registry id. Returning the original instance keeps duplicate-id detection strict.
     */
    public static ConfiguredSpell withBalanceOverrides(final ConfiguredSpell spell, final ConfigManager configManager,
                                                        final Logger logger) {
        if (spell == null || configManager == null || configManager.getConfiguration() == null) {
            return spell;
        }

        final ConfigurationSection section = configManager.getConfiguration()
                .getConfigurationSection("spell-balance." + spell.getId());
        if (section == null) {
            return spell;
        }

        final List<String> configured = section.getKeys(false).stream()
                .sorted(Comparator.naturalOrder())
                .map(key -> key + "=" + String.valueOf(section.get(key)))
                .toList();
        if (logger != null && !configured.isEmpty()) {
            hu.taliann.icesmp.utils.StartupLog.info(logger, configManager,
                    "Spell-balansz élő felülbírálása: " + spell.getId()
                            + " (" + String.join(", ", configured) + ")");
        }
        return spell;
    }

    @Override
    public void execute(final Player player) {
        executeCast(player);
    }

    @Override
    public boolean executeSpell(final Player player) {
        return executeCast(player).effectApplied();
    }

    /** Compatibility bridge for legacy scalar callers. */
    @Override
    public boolean executeSpell(final Player player, final double power) {
        return cast(player, CastModifiers.standardPower(power)).effectApplied();
    }

    @Override
    public CastOutcome executeCast(final Player player) {
        return switch (targeting) {
            case SELF -> {
                executeSelf(player);
                yield CastOutcome.SUCCESS;
            }
            case TARGET -> executeTarget(player) ? CastOutcome.SUCCESS : CastOutcome.NO_TARGET;
            case AOE -> executeAoe(player);
        };
    }

    // ==================== live balansz-olvasás ====================
    // A spell-balance.<id>.<kulcs> felülbírálások HASZNÁLAT idején olvasódnak (BaseSpell.balance
    // a volatile ConfigManager-en át), így /icesmp reload vagy /icesmp config set restart nélkül,
    // azonnal érvényes a deklaratív spelleknél is. Nincs startup snapshot vagy registry replacement.

    private double liveRange() {
        return balance("range", range);
    }

    private double liveRadius() {
        return balance("radius", radius);
    }

    private double liveDamage() {
        return balance("damage", damage);
    }

    private double liveSelfDamage() {
        return balance("self-damage", selfDamage);
    }

    private double liveHealSelf() {
        return balance("heal-self", healSelf);
    }

    private int liveFeedSelf() {
        return balanceInt("feed-self", feedSelf);
    }

    private int liveIgniteTicks() {
        return balanceInt("ignite-ticks", igniteTicks);
    }

    private int liveFreezeTicks() {
        return balanceInt("freeze-ticks", freezeTicks);
    }

    private double liveKnockback() {
        return balance("knockback", knockback);
    }

    private void executeSelf(final Player player) {
        applySelf(player);
        playFeedback(player, player.getLocation());
    }

    private boolean executeTarget(final Player player) {
        final LivingEntity target = SpellTargetingUtil.rayTraceLivingEntity(player, liveRange());
        if (target == null) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs célpont a látómeződben."));
            return false;
        }

        affect(player, target, false);
        applySelf(player);
        playFeedback(player, target.getLocation());
        return true;
    }

    /**
     * Instant configured AoE rejects an empty cast unless it also has a real
     * self-output. Persistent ground abilities are dedicated implementations and
     * can intentionally allow empty placement in their own cast outcome.
     */
    private CastOutcome executeAoe(final Player player) {
        final double aoeRadius = liveRadius();
        int affected = 0;
        for (final Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), aoeRadius, aoeRadius, aoeRadius)) {
            if (!(entity instanceof LivingEntity living) || entity == player) {
                continue;
            }

            if (friendlyAoe) {
                // Baráti AoE (buff): CSAK szövetséges játékost érint — ellenség nem kapja meg a buffot.
                if (!(living instanceof Player) || !SpellTargetingUtil.isAlly(player, living)) {
                    continue;
                }
            } else if (SpellTargetingUtil.isAlly(player, living)) {
                // Ellenséges AoE: a saját párttag (és configtól függően frakciótárs) védett.
                continue;
            }

            affect(player, living, friendlyAoe);
            affected++;
        }

        final boolean selfOutput = hasSelfOutput();
        if (selfOutput) {
            applySelf(player);
        }
        if (affected <= 0 && !selfOutput) {
            player.sendMessage(resolveMessage("no-target", "&7Nincs érvényes célpont a közeledben."));
            return CastOutcome.NO_TARGET;
        }
        playFeedback(player, player.getLocation());
        return CastOutcome.SUCCESS;
    }

    private boolean hasSelfOutput() {
        return !selfEffects.isEmpty()
                || liveSelfDamage() > 0.0D
                || liveHealSelf() > 0.0D
                || liveFeedSelf() > 0
                || dashForward != 0.0D
                || dashUp != 0.0D;
    }

    @Override
    public List<String> describe() {
        final List<String> lines = new ArrayList<>();
        lines.add(switch (targeting) {
            case SELF -> "Cél: önmagad";
            case TARGET -> "Cél: célzott lény (hatótáv " + trim(liveRange()) + ")";
            case AOE -> "Cél: körzet (sugár " + trim(liveRadius()) + ")" + (friendlyAoe ? ", csak szövetségesek" : "");
        });
        if (liveDamage() > 0.0D) {
            lines.add("Sebzés: " + trim(liveDamage()));
        }
        if (liveSelfDamage() > 0.0D) {
            lines.add("Önsebzés: " + trim(liveSelfDamage()));
        }
        if (liveHealSelf() > 0.0D) {
            lines.add("Gyógyítás: " + trim(liveHealSelf()));
        }
        if (liveFeedSelf() > 0) {
            lines.add("Jóllakottság: +" + liveFeedSelf());
        }
        if (liveIgniteTicks() > 0) {
            lines.add("Gyújtás: " + secondsOf(liveIgniteTicks()) + " mp");
        }
        if (liveFreezeTicks() > 0) {
            lines.add("Fagyasztás: " + secondsOf(FREEZE_BASE_TICKS + liveFreezeTicks()) + " mp");
        }
        if (lightning) {
            lines.add("Villámcsapás");
        }
        if (liveKnockback() > 0.0D) {
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

    private static PotionEffect scaledDuration(final PotionEffect effect, final boolean beneficial) {
        final CastModifiers modifiers = SpellExecutionContext.current();
        final double durationMultiplier;
        if (isHardCrowdControl(effect)) {
            durationMultiplier = modifiers.ccDurationMultiplier();
        } else {
            durationMultiplier = beneficial
                    ? modifiers.beneficialDurationMultiplier()
                    : modifiers.harmfulDurationMultiplier();
        }
        if (durationMultiplier == 1.0D) {
            return effect;
        }
        final int scaledTicks = Math.max(1, (int) Math.round(effect.getDuration() * durationMultiplier));
        return new PotionEffect(effect.getType(), scaledTicks, effect.getAmplifier(),
                effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
    }

    private static boolean isHardCrowdControl(final PotionEffect effect) {
        return (effect.getType().equals(PotionEffectType.SLOWNESS) && effect.getAmplifier() >= 1)
                || (effect.getType().equals(PotionEffectType.JUMP_BOOST) && effect.getAmplifier() >= 128)
                || effect.getType().equals(PotionEffectType.BLINDNESS)
                || effect.getType().equals(PotionEffectType.LEVITATION);
    }

    private void affect(final Player caster, final LivingEntity target, final boolean beneficial) {
        if (lightning) {
            target.getWorld().strikeLightningEffect(target.getLocation());
        }

        final double liveDamage = liveDamage();
        if (liveDamage > 0.0D) {
            hu.taliann.icesmp.utils.SpellDamageUtil.damageBySpell(caster, target, liveDamage, getId());
        }

        // Hard-CC DR: spellenként EGY közös szorzó (a freeze+slow kombó nem eszik két töltetet).
        Double ccFactor = null;
        for (final PotionEffect effect : targetEffects) {
            // Mob-célponton a képernyő-effektek ható megfelelőre fordulnak (lásd adaptForTarget).
            PotionEffect scaled = SpellTargetingUtil.adaptForTarget(target, scaledDuration(effect, beneficial));
            if (scaled.getType().equals(PotionEffectType.SLOWNESS) && scaled.getAmplifier() >= 1) {
                if (ccFactor == null) {
                    ccFactor = hu.taliann.icesmp.utils.CcDiminish.nextFactor(target);
                }
                if (ccFactor <= 0.0D) {
                    continue;
                }
                scaled = new PotionEffect(scaled.getType(), Math.max(1, (int) (scaled.getDuration() * ccFactor)),
                        scaled.getAmplifier(), scaled.isAmbient(), scaled.hasParticles(), scaled.hasIcon());
            }
            target.addPotionEffect(scaled);
        }

        final int liveIgnite = liveIgniteTicks();
        if (liveIgnite > 0) {
            final int duration = Math.max(1, (int) Math.round(liveIgnite
                    * SpellExecutionContext.current().harmfulDurationMultiplier()));
            target.setFireTicks(Math.max(target.getFireTicks(), duration));
        }

        final int liveFreeze = liveFreezeTicks();
        if (liveFreeze > 0) {
            if (ccFactor == null) {
                ccFactor = hu.taliann.icesmp.utils.CcDiminish.nextFactor(target);
            }
            if (ccFactor > 0.0D) {
                final int explicitCcDuration = Math.max(1, (int) Math.round(liveFreeze
                        * SpellExecutionContext.current().ccDurationMultiplier()));
                target.setFreezeTicks(Math.max(target.getFreezeTicks(),
                        FREEZE_BASE_TICKS + Math.max(1, (int) (explicitCcDuration * ccFactor))));
            }
        }

        final double liveKnockback = liveKnockback();
        if (liveKnockback > 0.0D) {
            final Vector away = target.getLocation().toVector().subtract(caster.getLocation().toVector()).setY(0.0D);
            if (away.lengthSquared() > 1.0E-4D) {
                target.setVelocity(away.normalize().multiply(liveKnockback).setY(0.3D));
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

    private void applySelf(final Player player) {
        for (final PotionEffect effect : selfEffects) {
            player.addPotionEffect(scaledDuration(effect, true));
        }

        // Self-damage is a cost, not offensive output — it stays at base regardless of spell power.
        final double liveSelfDamage = liveSelfDamage();
        if (liveSelfDamage > 0.0D) {
            player.damage(liveSelfDamage);
        }

        final double liveHealSelf = liveHealSelf();
        if (liveHealSelf > 0.0D) {
            final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                // A direkt gyógyítás a megnőtt HP-tóhoz skálázódik (maxHP/20, plafonig) —
                // a kaszt-HP-profilok mellett a fix heal relatíve elértéktelenedne.
                double healScale = 1.0D;
                final ConfigManager config = balanceSource();
                if (config != null && config.getBoolean("health.enabled", true)
                        && config.getBoolean("health.scale-heals", true)) {
                    healScale = Math.min(Math.max(1.0D, maxHealth.getValue() / 20.0D),
                            Math.max(1.0D, config.getDouble("health.scale-heals-cap", 2.5D)));
                }
                hu.taliann.icesmp.utils.SpellHealingUtil.heal(player, liveHealSelf * healScale);
            }
        }

        final int liveFeedSelf = liveFeedSelf();
        if (liveFeedSelf > 0) {
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + liveFeedSelf));
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
        if (hu.taliann.icesmp.utils.SpellVfx.isEnabled()) {
            final hu.taliann.icesmp.utils.SpellVfx.Shape shape = resolveShape();
            final hu.taliann.icesmp.utils.SpellVfx.Palette palette = vfxPalette != null
                    ? vfxPalette : hu.taliann.icesmp.utils.SpellVfx.paletteFor(getId(), particle);
            final Location origin = switch (shape) {
                case BEAM, ARC, CONE, IMPACT -> player.getEyeLocation();
                default -> player.getLocation();
            };
            // A CONE a néző irányába terül (nem a fókusz felé): to=null → a facing-et használja.
            final Location target = shape == hu.taliann.icesmp.utils.SpellVfx.Shape.CONE ? null : focus;
            hu.taliann.icesmp.utils.SpellVfx.render(shape, palette, origin, target, liveRadius(), particle, particleCount);
        } else if (particle != null) {
            final double spread = liveRadius();
            hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), particle, focus.clone().add(0.0D, 1.0D, 0.0D),
                    particleCount, spread > 0.0D ? spread / 2.0D : 0.4D, 0.6D, spread > 0.0D ? spread / 2.0D : 0.4D, 0.02D);
        }

        if (sound != null) {
            player.getWorld().playSound(focus, sound, soundVolume, soundPitch);
        }
    }

    /**
     * A tényleges forma: explicit {@code vfx(...)} → config-override ({@code spell-vfx.shapes}) →
     * konzervatív kulcsszó-heurisztika (a spell-id alapján) → {@link #defaultShape()} targeting-alap.
     */
    private hu.taliann.icesmp.utils.SpellVfx.Shape resolveShape() {
        if (vfxShape != null) {
            return vfxShape;
        }
        final hu.taliann.icesmp.utils.SpellVfx.Shape mapped = hu.taliann.icesmp.utils.SpellVfx.mappedShape(getId());
        if (mapped != null) {
            return mapped;
        }
        final String id = getId();
        if (targeting == Targeting.TARGET) {
            if (containsAny(id, MELEE_TOKENS)) {
                return hu.taliann.icesmp.utils.SpellVfx.Shape.IMPACT;
            }
            if (containsAny(id, THROWN_TOKENS)) {
                return hu.taliann.icesmp.utils.SpellVfx.Shape.ARC;
            }
        } else if (targeting == Targeting.AOE && containsAny(id, BREATH_TOKENS)) {
            return hu.taliann.icesmp.utils.SpellVfx.Shape.CONE;
        }
        return defaultShape();
    }

    // Konzervatív, egyértelmű kulcsszavak a forma-heurisztikához (a spell-id snake_case angol tokenjei).
    private static final String[] MELEE_TOKENS = {"strike", "smash", "slam", "punch", "kick", "crush",
            "cleave", "rend", "bite", "gore", "maul", "chop", "hack"};
    private static final String[] THROWN_TOKENS = {"throw", "toss", "dart", "javelin", "shuriken", "lob"};
    private static final String[] BREATH_TOKENS = {"breath", "roar", "spray", "exhale"};

    private static boolean containsAny(final String id, final String[] tokens) {
        if (id == null) {
            return false;
        }
        for (final String token : tokens) {
            if (id.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /** A targeting-jellegből adódó alapforma, ha nincs explicit/config/heurisztikus forma. */
    private hu.taliann.icesmp.utils.SpellVfx.Shape defaultShape() {
        return switch (targeting) {
            case SELF -> hu.taliann.icesmp.utils.SpellVfx.Shape.HELIX;
            case TARGET -> hu.taliann.icesmp.utils.SpellVfx.Shape.BEAM;
            case AOE -> hu.taliann.icesmp.utils.SpellVfx.Shape.RING;
        };
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
        private hu.taliann.icesmp.utils.SpellVfx.Shape vfxShape;
        private hu.taliann.icesmp.utils.SpellVfx.Palette vfxPalette;

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

        /**
         * Explicit spell-VFX forma és/vagy paletta. Bármelyik lehet null: a forma AUTO/null esetén
         * a targeting-ből jön (SELF→hélix, TARGET→sugár, AOE→gyűrű), a paletta null esetén az
         * accent-particle-ből származik.
         */
        public Builder vfx(final hu.taliann.icesmp.utils.SpellVfx.Shape shape,
                           final hu.taliann.icesmp.utils.SpellVfx.Palette palette) {
            this.vfxShape = shape == hu.taliann.icesmp.utils.SpellVfx.Shape.AUTO ? null : shape;
            this.vfxPalette = palette;
            return this;
        }

        public ConfiguredSpell build() {
            return new ConfiguredSpell(this);
        }
    }
}
