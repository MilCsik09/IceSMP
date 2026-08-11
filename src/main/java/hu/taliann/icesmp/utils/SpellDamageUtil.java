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

    private static volatile hu.taliann.icesmp.managers.ConfigManager configManager;
    private static volatile hu.taliann.icesmp.managers.JobManager jobManager;

    private SpellDamageUtil() {
    }

    /** Egyszeri bekötés az IceSMPCore-ból (a statikus út miatt — minta: ProtectionBridge). */
    public static void init(final hu.taliann.icesmp.managers.ConfigManager config,
                            final hu.taliann.icesmp.managers.JobManager jobs) {
        configManager = config;
        jobManager = jobs;
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
        if (type == null) {
            if (caster != null) {
                victim.damage(scaledAmount, caster);
            } else {
                victim.damage(scaledAmount);
            }
            return;
        }
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
                                      final CastModifiers modifiers) {
        if (projectile == null) {
            return;
        }
        final CastModifiers effective = modifiers == null ? CastModifiers.IDENTITY : modifiers;
        final PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        pdc.set(PROJECTILE_DAMAGE_MULTIPLIER, PersistentDataType.DOUBLE, effective.damageMultiplier());
        if (spellId == null || spellId.isBlank()) {
            pdc.remove(PROJECTILE_SPELL_ID);
        } else {
            pdc.set(PROJECTILE_SPELL_ID, PersistentDataType.STRING, spellId.trim().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Returns the projectile snapshot multiplier, or identity for non-spell damage. */
    public static double projectileDamageMultiplier(final Entity damager) {
        if (!(damager instanceof Projectile projectile)) {
            return 1.0D;
        }
        final Double value = projectile.getPersistentDataContainer()
                .get(PROJECTILE_DAMAGE_MULTIPLIER, PersistentDataType.DOUBLE);
        return value == null || !Double.isFinite(value) || value < 0.0D ? 1.0D : value;
    }

    /** Returns the spell id carried by a marked projectile, or null. */
    public static String projectileSpellId(final Entity damager) {
        if (!(damager instanceof Projectile projectile)) {
            return null;
        }
        return projectile.getPersistentDataContainer().get(PROJECTILE_SPELL_ID, PersistentDataType.STRING);
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

    /** Az iskola damage-type-ja a registryből, vagy null (bootstrap-hiba → vanília fallback). */
    private static DamageType resolveType(final SpellSchool school) {
        try {
            return io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.DAMAGE_TYPE)
                    .get(NamespacedKey.fromString("icesmp:" + school.getTypeId()));
        } catch (final Exception exception) {
            return null;
        }
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
