package hu.taliann.icesmp.utils;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Formázott, palettázott spell-effektek particle-ből. A {@link ConfiguredSpell} eddig egyetlen
 * gömb-puffot szórt a célpontra; ez a réteg a {@code Targeting}-ből (vagy explicit
 * {@link Shape}-ből) formát ad — sugár a cél felé, gyűrű az AoE-nek, hélix a buffnak —, a
 * {@link Palette} pedig szín-átmenetes port (DUST_COLOR_TRANSITION) a „mágia-anyagérzethez".
 *
 * <p>Időzítés nélküli, egyetlen híváson belül lezajló geometria: minden pont {@code count=1}
 * dust (ARCHITECTURE §6), a pontszám {@link #maxPoints} felső korláttal. A spell saját accent-
 * particle-je (FLAME/SOUL/…) a gócponton marad, hogy a kaszt-íz ne vesszen el.
 *
 * <p>Folia: minden metódus a hívó (kasztoló) régió-szálán fut, a helyek helyi részecske-
 * csomagok — nincs kereszt-régiós entitás-érintés, nincs scheduler-hop igény.
 */
public final class SpellVfx {

    private SpellVfx() {
    }

    public enum Shape { AUTO, BEAM, IMPACT, RING, HELIX, CONE, ARC }

    /**
     * Spec-paletta: két RGB szín a por-átmenethez (primer→szekunder). A spell explicit palettát
     * adhat, vagy a {@link #forParticle} a meglévő accent-particle-ből származtat egy ésszerűt,
     * így a ~390 spell kézi hangolás nélkül is színhelyes.
     */
    public enum Palette {
        FIRE(0xFF8A2B, 0xE01B0B),
        FROST(0x8FD8FF, 0xFFFFFF),
        HOLY(0xFFE9A3, 0xFFFFFF),
        DECAY(0x9A2BE2, 0x1A0026),
        NATURE(0x74E23B, 0x1B7A2B),
        ARCANE(0xB36BFF, 0x4B1B8A),
        BLOOD(0xE01B3B, 0x4A0010),
        STORM(0x9AD0FF, 0xF0F8FF),
        SHADOW(0x5A4B7A, 0x0A0612),
        NEUTRAL(0xE8E8F0, 0xAEAEC4);

        private final Color from;
        private final Color to;

        Palette(final int fromRgb, final int toRgb) {
            this.from = Color.fromRGB(fromRgb);
            this.to = Color.fromRGB(toRgb);
        }

        /** Accent-particle → paletta heurisztika, hogy a nem-hangolt spellek is színhelyesek legyenek. */
        public static Palette forParticle(final Particle particle) {
            if (particle == null) {
                return NEUTRAL;
            }
            return switch (particle) {
                case FLAME, LAVA, SMALL_FLAME, SOUL_FIRE_FLAME, DRIPPING_LAVA -> FIRE;
                case SNOWFLAKE, ITEM_SNOWBALL, DRIPPING_WATER, SPLASH, BUBBLE_POP -> FROST;
                case END_ROD, TOTEM_OF_UNDYING, GLOW, ENCHANT -> HOLY;
                case SCULK_SOUL, SCULK_CHARGE, SQUID_INK, ASH, SMOKE, LARGE_SMOKE -> SHADOW;
                case HAPPY_VILLAGER, COMPOSTER, SPORE_BLOSSOM_AIR -> NATURE;
                case WITCH, PORTAL, DRAGON_BREATH, REVERSE_PORTAL -> ARCANE;
                case SOUL, SPELL_WITCH, ANGRY_VILLAGER -> DECAY;
                case ELECTRIC_SPARK, FIREWORK, SWEEP_ATTACK -> STORM;
                case HEART, DAMAGE_INDICATOR, CRIT -> BLOOD;
                default -> NEUTRAL;
            };
        }
    }

    // Load-időben állítjuk be (IceSMPCore); sok régió-szálról olvassuk, ezért volatile.
    private static volatile boolean enabled = true;
    private static volatile int maxPoints = 48;
    private static final float DUST_SIZE = 1.1F;

    public static void configure(final boolean vfxEnabled, final int pointCap) {
        enabled = vfxEnabled;
        maxPoints = Math.max(4, pointCap);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * A spell visszajelzés-belépője. A forma AUTO esetén a targeting-jellegből jön (a hívó adja
     * meg BEAM/RING/HELIX-ként), a paletta és az accent-particle a spellé.
     *
     * @param from a kaszt-forrás (szem/láb), a BEAM/ARC kezdete és a HELIX/RING alapja
     * @param to   a célpont (BEAM/ARC/IMPACT vége); RING/HELIX/CONE esetén lehet null
     * @param radius az AoE/RING sugara (0, ha nem releváns)
     */
    public static void render(final Shape shape, final Palette palette, final Location from, final Location to,
                              final double radius, final Particle accent, final int accentCount) {
        if (!enabled || shape == null || from == null || from.getWorld() == null) {
            return;
        }
        final Palette pal = palette != null ? palette : Palette.forParticle(accent);
        switch (shape) {
            case BEAM -> { if (to != null) { beam(from, to, pal); accentAt(to, accent, accentCount); } }
            case ARC -> { if (to != null) { arc(from, to, pal); accentAt(to, accent, accentCount); } }
            case IMPACT -> { impact(to != null ? to : from, pal); accentAt(to != null ? to : from, accent, accentCount); }
            case RING -> { ring(from, Math.max(1.0D, radius), pal); accentAt(from, accent, accentCount); }
            case CONE -> { cone(from, to, pal); accentAt(from, accent, accentCount); }
            case HELIX, AUTO -> { helix(from, pal); accentAt(from, accent, accentCount); }
        }
    }

    private static void accentAt(final Location at, final Particle accent, final int accentCount) {
        if (accent == null || at == null || accentCount <= 0) {
            return;
        }
        ParticleUtil.spawn(at.getWorld(), accent, at.clone().add(0.0D, 1.0D, 0.0D),
                Math.min(accentCount, 12), 0.25D, 0.35D, 0.25D, 0.02D);
    }

    private static void beam(final Location from, final Location to, final Palette pal) {
        final World world = from.getWorld();
        final Vector delta = to.toVector().subtract(from.toVector());
        final double dist = delta.length();
        if (dist < 0.1D) {
            impact(to, pal);
            return;
        }
        final int points = clampPoints((int) Math.round(dist * 2.0D));
        final Vector step = delta.multiply(1.0D / points);
        final Location cursor = from.clone();
        for (int i = 0; i <= points; i++) {
            dust(world, cursor, pal);
            cursor.add(step.getX(), step.getY(), step.getZ());
        }
    }

    private static void arc(final Location from, final Location to, final Palette pal) {
        final World world = from.getWorld();
        final int points = clampPoints((int) Math.round(from.distance(to) * 2.0D));
        final double peak = Math.min(3.0D, from.distance(to) * 0.35D);
        for (int i = 0; i <= points; i++) {
            final double t = (double) i / points;
            final double x = from.getX() + (to.getX() - from.getX()) * t;
            final double y = from.getY() + (to.getY() - from.getY()) * t + Math.sin(Math.PI * t) * peak;
            final double z = from.getZ() + (to.getZ() - from.getZ()) * t;
            dust(world, new Location(world, x, y, z), pal);
        }
    }

    private static void impact(final Location center, final Palette pal) {
        final World world = center.getWorld();
        final int points = clampPoints(14);
        final Location body = center.clone().add(0.0D, 1.0D, 0.0D);
        for (int i = 0; i < points; i++) {
            final double a = 2.0D * Math.PI * i / points;
            final double r = 0.6D + 0.25D * Math.sin(a * 3.0D);
            dust(world, body.clone().add(Math.cos(a) * r, 0.15D * Math.sin(a * 2.0D), Math.sin(a) * r), pal);
        }
    }

    private static void ring(final Location center, final double radius, final Palette pal) {
        final World world = center.getWorld();
        final int points = clampPoints((int) Math.round(radius * 8.0D));
        final double y = ParticleUtil.markerY(world, center.getBlockX(), center.getBlockZ(), center.getY() + 0.2D);
        for (int i = 0; i < points; i++) {
            final double a = 2.0D * Math.PI * i / points;
            dust(world, new Location(world, center.getX() + Math.cos(a) * radius, y, center.getZ() + Math.sin(a) * radius), pal);
        }
    }

    private static void helix(final Location base, final Palette pal) {
        final World world = base.getWorld();
        final int points = clampPoints(24);
        final double height = 2.2D;
        final double radius = 0.55D;
        for (int i = 0; i < points; i++) {
            final double t = (double) i / points;
            final double a = 2.0D * Math.PI * t * 2.0D;
            final double y = base.getY() + t * height;
            dust(world, new Location(world, base.getX() + Math.cos(a) * radius, y, base.getZ() + Math.sin(a) * radius), pal);
            dust(world, new Location(world, base.getX() - Math.cos(a) * radius, y, base.getZ() - Math.sin(a) * radius), pal);
        }
    }

    private static void cone(final Location origin, final Location to, final Palette pal) {
        final World world = origin.getWorld();
        Vector dir = to != null ? to.toVector().subtract(origin.toVector()) : origin.getDirection();
        if (dir.lengthSquared() < 1.0E-4D) {
            dir = origin.getDirection();
        }
        dir = dir.normalize();
        final Vector up = Math.abs(dir.getY()) > 0.99D ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        final Vector right = dir.clone().crossProduct(up).normalize();
        final Vector realUp = right.clone().crossProduct(dir).normalize();
        final int rings = 3;
        final int perRing = clampPoints(24) / rings;
        final Location mouth = origin.clone().add(0.0D, 1.2D, 0.0D);
        for (int r = 1; r <= rings; r++) {
            final double len = r * 0.9D;
            final double spread = r * 0.4D;
            for (int i = 0; i < perRing; i++) {
                final double a = 2.0D * Math.PI * i / perRing;
                final Vector offset = right.clone().multiply(Math.cos(a) * spread).add(realUp.clone().multiply(Math.sin(a) * spread));
                final Location p = mouth.clone().add(dir.clone().multiply(len)).add(offset);
                dust(world, p, pal);
            }
        }
    }

    private static void dust(final World world, final Location at, final Palette pal) {
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.0D, 0.0D, 0.0D, 0.0D,
                new Particle.DustTransition(pal.from, pal.to, DUST_SIZE));
    }

    private static int clampPoints(final int desired) {
        return Math.max(4, Math.min(maxPoints, desired));
    }
}
