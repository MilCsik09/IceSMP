package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.data.SpellSchool;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Közös spell-sebzés út: minden varázslat a saját ISKOLÁJA damage-type-jával üt
 * (icesmp:tuz_magia, fagy_magia, … — bootstrap-regisztrált), így a mágia-sebzés
 * iskolánként megkülönböztethető — erre épül a Rúnavért (generikus) és az
 * iskola-counter enchantok ellenállása, plusz a magyar halál-üzenet
 * (SpellDamageListener). A caster causing-entityként rákerül a source-ra, ezért a
 * kill-attribúció (getKiller → bűn/raid/bounty logika) változatlanul működik.
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

    /**
     * Spell-sebzés a spell iskolájának damage-type-jával.
     *
     * @param caster a varázsló (lehet null a cross-region fallback-ágakon)
     * @param victim a cél (a hívó a cél régió-szálán fut)
     * @param amount a sebzés
     * @param spellId a spell azonosítója (a by-spell felüliráshoz; lehet null)
     */
    public static void damageBySpell(final Player caster, final LivingEntity victim,
                                     final double amount, final String spellId) {
        if (victim == null || amount <= 0.0D) {
            return;
        }
        final DamageType type = resolveType(schoolFor(caster, spellId));
        if (type == null) {
            if (caster != null) {
                victim.damage(amount, caster);
            } else {
                victim.damage(amount);
            }
            return;
        }
        final DamageSource.Builder builder = DamageSource.builder(type);
        if (caster != null) {
            builder.withCausingEntity(caster).withDirectEntity(caster);
        }
        victim.damage(amount, builder.build());
    }

    /** Iskola-besorolás nélküli (ősmágia) út — régi hívásokhoz. */
    public static void damageBySpell(final Player caster, final LivingEntity victim, final double amount) {
        damageBySpell(caster, victim, amount, null);
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
