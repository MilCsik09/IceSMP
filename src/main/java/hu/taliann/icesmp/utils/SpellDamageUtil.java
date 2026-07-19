package hu.taliann.icesmp.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Közös spell-sebzés út: minden varázslat az {@code icesmp:magia} damage-type-pal üt
 * (bootstrap-regisztrált), így a mágia-sebzés megkülönböztethető a vanília forrásoktól —
 * erre épül a Rúnavért ellenállás-enchant és a magyar halál-üzenet
 * (SpellDamageListener). A caster causing-entityként rákerül a source-ra, ezért a
 * kill-attribúció (getKiller → bűn/raid/bounty logika) változatlanul működik.
 *
 * <p>Fallback: ha a damage-type nem érhető el (a bootstrap-regisztráció hibázott),
 * az út visszaesik a vanília {@code damage(amount, attacker)} hívásra — a spell
 * sosem marad sebzés nélkül.
 *
 * <p>Folia: a hívó felelőssége, hogy a CÉL régió-szálán fusson (a meglévő spell-kód
 * pontosan így hívta az eddigi damage()-et is — az út nem változtat a szálazáson).
 */
public final class SpellDamageUtil {

    private static final NamespacedKey MAGIC_KEY = NamespacedKey.fromString("icesmp:magia");

    private SpellDamageUtil() {
    }

    /**
     * Spell-sebzés a mágia damage-type-pal.
     *
     * @param caster a varázsló (lehet null a cross-region fallback-ágakon — akkor
     *               attribúció nélkül, de mágia-típussal üt)
     * @param victim a cél (a hívó a cél régió-szálán fut)
     * @param amount a sebzés
     */
    public static void damageBySpell(final Player caster, final LivingEntity victim, final double amount) {
        if (victim == null || amount <= 0.0D) {
            return;
        }
        final DamageType magic = resolveMagicType();
        if (magic == null) {
            if (caster != null) {
                victim.damage(amount, caster);
            } else {
                victim.damage(amount);
            }
            return;
        }
        final DamageSource.Builder builder = DamageSource.builder(magic);
        if (caster != null) {
            builder.withCausingEntity(caster).withDirectEntity(caster);
        }
        victim.damage(amount, builder.build());
    }

    /** Az icesmp:magia damage-type, vagy null, ha a registryben nem érhető el. */
    public static DamageType resolveMagicType() {
        try {
            return io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.DAMAGE_TYPE)
                    .get(MAGIC_KEY);
        } catch (final Exception exception) {
            return null;
        }
    }

    /** A sebzés-forrás a mi mágia-típusunk-e (a resist-listener szűrője). */
    public static boolean isMagicDamage(final DamageSource source) {
        return source != null && MAGIC_KEY != null
                && MAGIC_KEY.equals(source.getDamageType().getKey());
    }
}
