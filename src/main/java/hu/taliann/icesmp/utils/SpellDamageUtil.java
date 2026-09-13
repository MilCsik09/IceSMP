package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.data.SpellSchool;
import hu.taliann.icesmp.spells.CastModifiers;
import hu.taliann.icesmp.spells.SpellExecutionContext;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Közös spell-sebzés út: minden varázslat a saját ISKOLÁJA damage-type-jával üt
 * (icesmp:tuz_magia, fagy_magia, … — bootstrap-regisztrált), így a mágia-sebzés
 * iskolánként megkülönböztethető — erre épül a Rúnavért (generikus) és az
 * iskola-counter enchantok ellenállása, plusz a magyar halál-üzenet
 * (SpellDamageListener). A caster causing-entityként rákerül a source-ra, ezért a
 * kill-attribúció (getKiller → bűn/raid/bounty logika) változatlanul működik.
 *
 * <p><b>Skálázás:</b> a szinkron spell-output automatikusan a
 * {@link SpellExecutionContext} damage multiplierét használja. Scheduler-hop után
 * az explicit {@link CastModifiers} overloadot kell hívni. A projectile volley-k
 * immutable PDC snapshotot visznek a damage eventig.</p>
 *
 * <p><b>Iskola-besorolás</b> (élőben olvasott config, spells.yml):
 * {@code spells.spell-schools.by-spell.<spellId>} felülírás → a caster kasztjának
 * {@code spells.spell-schools.by-class.<jobId>} defaultja → ŐSMÁGIA. Így MINDEN
 * spell egyedivé hangolható restart nélkül.
 *
 * <p>Fallback: ha a damage-type nem érhető el (a bootstrap-regisztráció hibázott),
 * az út visszaesik a vanília {@code damage(amount, attacker)} hívásra — a spell
 * sosem marad sebzés nélkül.
 *
 * <p>Folia: a hívó felelőssége, hogy a CÉL régió-szálán fusson (a meglévő spell-kód
 * pontosan így hívta az eddigi damage()-et is); a resolver csak configot és a
 * caster PDC-jét olvassa — a caster a hívó kontextusában régió-biztos.
 */
public final class SpellDamageUtil {

    private static final NamespacedKey PROJECTILE_DAMAGE_MULTIPLIER =
            new NamespacedKey("icesmp", "cast_damage_multiplier");
    private static final NamespacedKey PROJECTILE_SPELL_ID =
            new NamespacedKey("icesmp", "cast_spell_id");
    private static final NamespacedKey PROJECTILE_BASE_DAMAGE =
            new NamespacedKey("icesmp", "cast_base_damage");
    private static final NamespacedKey PROJECTILE_SCHOOL =
            new NamespacedKey("icesmp", "cast_spell_school");
    private static final NamespacedKey PROJECTILE_CASTER =
            new NamespacedKey("icesmp", "cast_caster_uuid");
    private static final NamespacedKey PROJECTILE_HIT_TARGETS =
            new NamespacedKey("icesmp", "cast_hit_targets");

    private static volatile hu.taliann.icesmp.managers.ConfigManager configManager;
    private static volatile hu.taliann.icesmp.managers.JobManager jobManager;
    private static volatile hu.taliann.icesmp.managers.SpellRegistry spellRegistry;
    private static final ThreadLocal<java.util.Set<java.util.UUID>> CANONICAL_PROJECTILE_DAMAGE =
            new ThreadLocal<>();

    public record ProjectileSnapshot(String spellId, SpellSchool school, java.util.UUID casterId,
                                     double baseDamage, double multiplier) {
        public double scaledDamage() {
            final double value = baseDamage * multiplier;
            return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
        }
    }

    private SpellDamageUtil() {
    }

    /** Egyszeri bekötés az IceSMPCore-ból (a statikus út miatt — minta: ProtectionBridge). */
    public static void init(final hu.taliann.icesmp.managers.ConfigManager config,
                            final hu.taliann.icesmp.managers.JobManager jobs,
                            final hu.taliann.icesmp.managers.SpellRegistry spells) {
        configManager = config;
        jobManager = jobs;
        spellRegistry = spells;
    }

    /** Spell-sebzés a jelenlegi szinkron cast-contexttel. */
    public static void damageBySpell(final Player caster, final LivingEntity victim,
                                     final double amount, final String spellId) {
        damageBySpell(caster, victim, amount, spellId, SpellExecutionContext.current());
    }

    /**
     * Spell-sebzés explicit immutable modifier snapshottal. Ezt kell használni
     * delayed/channel/scheduler-hop utáni outputhoz.
     */
    public static void damageBySpell(final Player caster, final LivingEntity victim,
                                     final double amount, final String spellId,
                                     final CastModifiers modifiers) {
        final double scaledAmount = scaledDamage(amount, modifiers);
        if (victim == null || scaledAmount <= 0.0D) {
            return;
        }
        final DamageType type = resolveType(schoolFor(caster, spellId));
        final DamageSource.Builder builder = DamageSource.builder(type);
        if (caster != null) {
            builder.withCausingEntity(caster).withDirectEntity(caster);
        }
        victim.damage(scaledAmount, builder.build());
    }

    /** Iskola-besorolás nélküli (ősmágia) út — régi hívásokhoz. */
    public static void damageBySpell(final Player caster, final LivingEntity victim, final double amount) {
        damageBySpell(caster, victim, amount, null);
    }

    /** Pure scaling helper for cross-region vanilla fallback branches. */
    public static double scaledDamage(final double baseAmount, final CastModifiers modifiers) {
        if (!Double.isFinite(baseAmount) || baseAmount <= 0.0D) {
            return 0.0D;
        }
        final CastModifiers effective = modifiers == null ? CastModifiers.IDENTITY : modifiers;
        final double scaled = baseAmount * effective.damageMultiplier();
        return Double.isFinite(scaled) && scaled > 0.0D ? scaled : 0.0D;
    }

    /** Stores the immutable cast snapshot on a launched projectile. */
    public static void markProjectile(final Projectile projectile, final String spellId,
                                      final double baseDamage, final CastModifiers modifiers) {
        if (projectile == null) {
            return;
        }
        final CastModifiers effective = modifiers == null ? CastModifiers.IDENTITY : modifiers;
        final PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        pdc.set(PROJECTILE_DAMAGE_MULTIPLIER, PersistentDataType.DOUBLE, effective.damageMultiplier());
        pdc.set(PROJECTILE_BASE_DAMAGE, PersistentDataType.DOUBLE, baseDamage);
        final Player caster = projectile.getShooter() instanceof Player player ? player : null;
        final SpellSchool school = schoolFor(caster, spellId);
        pdc.set(PROJECTILE_SCHOOL, PersistentDataType.STRING, school.name());
        if (caster != null) {
            pdc.set(PROJECTILE_CASTER, PersistentDataType.STRING, caster.getUniqueId().toString());
        } else {
            pdc.remove(PROJECTILE_CASTER);
        }
        if (spellId == null || spellId.isBlank()) {
            pdc.remove(PROJECTILE_SPELL_ID);
        } else {
            pdc.set(PROJECTILE_SPELL_ID, PersistentDataType.STRING, spellId.trim().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Returns the spell id carried by a marked projectile, or null. */
    public static String projectileSpellId(final Entity damager) {
        if (!(damager instanceof Projectile projectile)) {
            return null;
        }
        return projectile.getPersistentDataContainer().get(PROJECTILE_SPELL_ID, PersistentDataType.STRING);
    }

    /** Complete, fail-closed cast snapshot; partial/unknown PDC is ordinary vanilla projectile state. */
    public static java.util.Optional<ProjectileSnapshot> projectileSnapshot(final Entity damager) {
        if (!(damager instanceof Projectile projectile)) return java.util.Optional.empty();
        final PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        final String spellId = pdc.get(PROJECTILE_SPELL_ID, PersistentDataType.STRING);
        final Double base = pdc.get(PROJECTILE_BASE_DAMAGE, PersistentDataType.DOUBLE);
        final Double multiplier = pdc.get(PROJECTILE_DAMAGE_MULTIPLIER, PersistentDataType.DOUBLE);
        final String schoolName = pdc.get(PROJECTILE_SCHOOL, PersistentDataType.STRING);
        final String casterText = pdc.get(PROJECTILE_CASTER, PersistentDataType.STRING);
        final var registry = spellRegistry;
        if (spellId == null || registry == null
                || !(registry.getById(spellId) instanceof hu.taliann.icesmp.spells.ProjectileBurstSpell)
                || base == null || !Double.isFinite(base) || base <= 0.0D
                || multiplier == null || !Double.isFinite(multiplier) || multiplier < 0.0D
                || schoolName == null) return java.util.Optional.empty();
        final SpellSchool school;
        final java.util.UUID casterId;
        try {
            school = SpellSchool.valueOf(schoolName);
            casterId = casterText == null ? null : java.util.UUID.fromString(casterText);
        } catch (final IllegalArgumentException invalid) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ProjectileSnapshot(spellId, school, casterId, base, multiplier));
    }

    /** PDC-backed per-target receipt; piercing projectiles may hit many targets, each at most once. */
    public static boolean claimProjectileTarget(final Projectile projectile, final java.util.UUID targetId) {
        if (projectile == null || targetId == null) return false;
        final PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        final String token = targetId.toString();
        final String current = pdc.getOrDefault(PROJECTILE_HIT_TARGETS, PersistentDataType.STRING, "");
        final java.util.Set<String> claimed = new java.util.LinkedHashSet<>();
        if (!current.isBlank()) claimed.addAll(java.util.List.of(current.split(";")));
        if (!claimed.add(token)) return false;
        pdc.set(PROJECTILE_HIT_TARGETS, PersistentDataType.STRING, String.join(";", claimed));
        return true;
    }

    /** Applies one canonical custom DamageType hit with projectile direct-entity attribution. */
    public static boolean damageByProjectile(final Projectile projectile, final LivingEntity victim,
                                             final ProjectileSnapshot snapshot) {
        if (projectile == null || victim == null || snapshot == null) return false;
        final double amount = snapshot.scaledDamage();
        if (amount <= 0.0D) return true;
        final DamageType type = resolveType(snapshot.school());
        final DamageSource.Builder builder = DamageSource.builder(type).withDirectEntity(projectile);
        if (projectile.getShooter() instanceof Entity causing) builder.withCausingEntity(causing);
        java.util.Set<java.util.UUID> active = CANONICAL_PROJECTILE_DAMAGE.get();
        if (active == null) {
            active = new java.util.HashSet<>();
            CANONICAL_PROJECTILE_DAMAGE.set(active);
        }
        if (!active.add(projectile.getUniqueId())) {
            throw new IllegalStateException("recursive canonical projectile damage: " + snapshot.spellId());
        }
        try {
            victim.damage(amount, builder.build());
        } finally {
            active.remove(projectile.getUniqueId());
            if (active.isEmpty()) CANONICAL_PROJECTILE_DAMAGE.remove();
        }
        return true;
    }

    /** Distinguishes the one custom DamageSource event from a later vanilla projectile hit. */
    public static boolean isCanonicalProjectileDamage(final Entity damager) {
        final java.util.Set<java.util.UUID> active = CANONICAL_PROJECTILE_DAMAGE.get();
        return damager != null && active != null && active.contains(damager.getUniqueId());
    }

    /** A spell iskolája: by-spell felülírás → a caster kasztjának by-class defaultja → ŐSMÁGIA. */
    public static SpellSchool schoolFor(final Player caster, final String spellId) {
        final hu.taliann.icesmp.managers.ConfigManager config = configManager;
        if (config != null && spellId != null) {
            final SpellSchool bySpell = SpellSchool.fromInput(
                    config.getString("spells.spell-schools.by-spell." + spellId, ""));
            if (bySpell != null) {
                return bySpell;
            }
        }
        final hu.taliann.icesmp.managers.JobManager jobs = jobManager;
        if (config != null && jobs != null && caster != null) {
            final hu.taliann.icesmp.data.JobType job = jobs.getPrimaryJob(caster);
            if (job != null) {
                final SpellSchool byClass = SpellSchool.fromInput(
                        config.getString("spells.spell-schools.by-class." + job.getId(), ""));
                if (byClass != null) {
                    return byClass;
                }
            }
        }
        return SpellSchool.OSMAGIA;
    }

    /** Az iskola kötelező damage-type-ja; hiányzó bootstrap authority esetén fail-closed. */
    private static DamageType resolveType(final SpellSchool school) {
        final DamageType type;
        try {
            type = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.DAMAGE_TYPE)
                    .get(NamespacedKey.fromString("icesmp:" + school.getTypeId()));
        } catch (final Exception exception) {
            throw new IllegalStateException("custom DamageType registry unavailable for " + school, exception);
        }
        if (type == null) throw new IllegalStateException("missing custom DamageType for " + school);
        return type;
    }

    /** A forrás valamelyik icesmp mágia-iskola típusa-e (a resist-listener szűrője). */
    public static boolean isMagicDamage(final DamageSource source) {
        return schoolOf(source) != null;
    }

    /** A forrás iskolája, vagy null, ha nem icesmp mágia-sebzés. */
    public static SpellSchool schoolOf(final DamageSource source) {
        if (source == null) {
            return null;
        }
        final NamespacedKey key = source.getDamageType().getKey();
        if (!"icesmp".equals(key.getNamespace())) {
            return null;
        }
        return SpellSchool.fromTypeId(key.getKey());
    }
}
