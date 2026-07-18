package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.PartyManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

public final class SpellTargetingUtil {

    // Party-tudatos célzás kontextusa: az IceSMPCore enable()-je tölti fel,
    // volatile a régió-szálakról való biztonságos olvasáshoz (BaseSpell.balanceSource mintája).
    private static volatile PartyManager partyManager;
    private static volatile FactionManager factionManager;
    private static volatile ConfigManager configManager;

    private SpellTargetingUtil() {
    }

    /** Called once from IceSMPCore.enable() so spells can resolve party/faction allegiance. */
    public static void initCombatContext(final PartyManager party, final FactionManager factions,
                                         final ConfigManager config) {
        partyManager = party;
        factionManager = factions;
        configManager = config;
    }

    /**
     * Whether the target counts as the caster's ALLY for hostile spell purposes — ally targets are
     * skipped by every damaging/debuffing AoE, splash, beam and projectile, so a spell can never
     * hit the caster's own party (default) or, when enabled, kingdom mates.
     *
     * <ul>
     *   <li>party-tag: védett, amíg {@code spells.friendly-fire.protect-party} igaz (default);</li>
     *   <li>frakció-társ: csak ha {@code spells.friendly-fire.protect-faction} igaz (default
     *       HAMIS — háborús szerver, a frakción belüli konfliktus megengedett, az árulás-bűn
     *       kezeli);</li>
     *   <li>mob sosem szövetséges (a minionok hűségét a MinionManager kezeli külön).</li>
     * </ul>
     */
    public static boolean isAlly(final Player caster, final LivingEntity target) {
        if (caster == null || target == null) {
            return false;
        }
        if (target == caster) {
            return true;
        }
        if (!(target instanceof Player other)) {
            return false;
        }

        final ConfigManager config = configManager;
        final PartyManager party = partyManager;
        if (party != null && (config == null || config.getBoolean("spells.friendly-fire.protect-party", true))
                && party.isSameParty(caster.getUniqueId(), other.getUniqueId())) {
            return true;
        }

        final FactionManager factions = factionManager;
        if (factions != null && config != null
                && config.getBoolean("spells.friendly-fire.protect-faction", false)) {
            final FactionType casterFaction = factions.getFaction(caster.getUniqueId());
            return casterFaction != null && casterFaction != FactionType.NEUTRAL
                    && casterFaction == factions.getFaction(other.getUniqueId());
        }
        return false;
    }

    /**
     * UUID-alapú változat a lövedék-/item-tickekhez, ahol a kaszter Player-példánya nem érhető el
     * biztonságosan (más régióban járhat) — a party/frakció-tagság UUID-ből is eldönthető.
     */
    public static boolean isAlly(final java.util.UUID casterId, final LivingEntity target) {
        if (casterId == null || !(target instanceof Player other)) {
            return false;
        }
        if (casterId.equals(other.getUniqueId())) {
            return true;
        }

        final ConfigManager config = configManager;
        final PartyManager party = partyManager;
        if (party != null && (config == null || config.getBoolean("spells.friendly-fire.protect-party", true))
                && party.isSameParty(casterId, other.getUniqueId())) {
            return true;
        }

        final FactionManager factions = factionManager;
        if (factions != null && config != null
                && config.getBoolean("spells.friendly-fire.protect-faction", false)) {
            final FactionType casterFaction = factions.getFaction(casterId);
            return casterFaction != null && casterFaction != FactionType.NEUTRAL
                    && casterFaction == factions.getFaction(other.getUniqueId());
        }
        return false;
    }

    /** Kényelmi negált forma a cél-szűrő láncokhoz. */
    public static boolean isHostileTarget(final Player caster, final LivingEntity target) {
        return target != null && target != caster && !isAlly(caster, target);
    }

    /**
     * A képernyő-zavaró effektek (vakság/sötétség/hányinger) CSAK játékosra
     * hatnak — mob-célponton ekvivalens, ténylegesen ható effektre fordítjuk őket, a méreg pedig
     * élőhalotton rothasztássá (wither) válik. Így egyetlen ponton javul az összes deklaratív
     * spell, és a bespoke spellek is ezt hívják.
     *
     * <ul>
     *   <li>BLINDNESS / DARKNESS / NAUSEA mobra → SLOWNESS (azonos időtartam/erősség);</li>
     *   <li>POISON élőhalottra → WITHER (a méreg-immunitás megkerülése, tematikusan);</li>
     *   <li>minden más változatlan.</li>
     * </ul>
     */
    public static org.bukkit.potion.PotionEffect adaptForTarget(final LivingEntity target,
                                                                final org.bukkit.potion.PotionEffect effect) {
        if (target instanceof Player || effect == null) {
            return effect;
        }
        final org.bukkit.potion.PotionEffectType type = effect.getType();
        if (type == org.bukkit.potion.PotionEffectType.BLINDNESS
                || type == org.bukkit.potion.PotionEffectType.DARKNESS
                || type == org.bukkit.potion.PotionEffectType.NAUSEA) {
            return new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS,
                    effect.getDuration(), Math.max(0, effect.getAmplifier()),
                    effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
        }
        if (type == org.bukkit.potion.PotionEffectType.POISON && isUndead(target)) {
            return new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WITHER,
                    effect.getDuration(), effect.getAmplifier(),
                    effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
        }
        return effect;
    }

    /** Élőhalott-e a célpont (a méreg rájuk hatástalan — vanília szabály). */
    public static boolean isUndead(final LivingEntity entity) {
        return entity instanceof org.bukkit.entity.Zombie
                || entity instanceof org.bukkit.entity.AbstractSkeleton
                || entity instanceof org.bukkit.entity.Phantom
                || entity instanceof org.bukkit.entity.Wither
                || entity instanceof org.bukkit.entity.Zoglin
                || entity instanceof org.bukkit.entity.SkeletonHorse
                || entity instanceof org.bukkit.entity.ZombieHorse;
    }

    public static LivingEntity rayTraceLivingEntity(final Player player, final double range) {
        final RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                // Ellenséges célzás: a saját párttag nem "fogja meg" a sugarat (átmegy rajta).
                entity -> entity instanceof LivingEntity living && entity != player && !isAlly(player, living)
        );

        if (result == null) {
            return null;
        }

        final Entity hit = result.getHitEntity();
        return hit instanceof LivingEntity living ? living : null;
    }
}
