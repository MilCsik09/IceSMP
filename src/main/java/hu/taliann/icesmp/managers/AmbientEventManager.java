package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambient world events: infrequent, small, atmospheric
 * happenings that make the world feel alive without touching balance or the
 * economy. Each firing picks one <em>enabled</em> flavour at random, checks
 * that flavour's environmental gate (time of day / weather) against the main
 * world and — only if it passes — broadcasts + paints client-side particles,
 * hands out a short cosmetic buff, or spawns a small herd of passive animals.
 * If the rolled flavour's gate fails the cycle is skipped silently (no reroll),
 * so flavours stay tied to a believable moment (aurora at night, fog at dawn…).
 *
 * <p>Players who are outdoors (sky access) in the firing world when an event
 * lands get a small "caught the moment" reward: a little currency into their
 * own faction balance plus a short flavour-appropriate potion effect.
 *
 * <p>The tick runs on the global region scheduler; world time/weather reads are
 * therefore thread-safe here (same precedent as {@link BloodMoonManager#tick()}).
 * Every player- or location-touching effect hops to the owning region thread
 * (Folia-safe).
 */
public final class AmbientEventManager {

    /** How a flavour's time window interacts with weather. */
    private enum WeatherGate {
        /** Only the time window matters. */
        NONE,
        /** Time window AND clear (no storm). */
        CLEAR_ONLY,
        /** Time window OR currently storming. */
        WINDOW_OR_RAIN
    }

    /** The atmospheric flavours; each is individually toggleable in config, and each
     * carries its own environmental gate (time-of-day window + weather rule) and its
     * own outdoors-participation reward effect. */
    private enum Ambient {
        AURORA(13000L, 23000L, WeatherGate.CLEAR_ONLY, PotionEffectType.NIGHT_VISION, 60),
        FALLING_STAR(13000L, 23000L, WeatherGate.NONE, PotionEffectType.LUCK, 120),
        FOG_ROLL(0L, 2000L, WeatherGate.WINDOW_OR_RAIN, null, 0),
        SPECTRAL_WANDERERS(13000L, 23000L, WeatherGate.NONE, PotionEffectType.INVISIBILITY, 30),
        ANIMAL_MIGRATION(1000L, 11000L, WeatherGate.NONE, PotionEffectType.HERO_OF_THE_VILLAGE, 120),
        FIREFLIES(12000L, 15000L, WeatherGate.NONE, PotionEffectType.SPEED, 60);

        private final long windowStartTick;
        private final long windowEndTick;
        private final WeatherGate weatherGate;
        private final PotionEffectType rewardEffect;
        private final int rewardSeconds;

        Ambient(final long windowStartTick, final long windowEndTick, final WeatherGate weatherGate,
                final PotionEffectType rewardEffect, final int rewardSeconds) {
            this.windowStartTick = windowStartTick;
            this.windowEndTick = windowEndTick;
            this.weatherGate = weatherGate;
            this.rewardEffect = rewardEffect;
            this.rewardSeconds = rewardSeconds;
        }

        String configKey() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        private boolean inWindow(final long time) {
            return time >= windowStartTick && time <= windowEndTick;
        }

        boolean environmentAllows(final World world) {
            final boolean inWindow = inWindow(world.getTime());
            return switch (weatherGate) {
                case NONE -> inWindow;
                case CLEAR_ONLY -> inWindow && !world.hasStorm();
                case WINDOW_OR_RAIN -> inWindow || world.hasStorm();
            };
        }
    }

    private static final EntityType[] HERD = {
            EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN, EntityType.HORSE
    };

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;

    private volatile long nextAttemptAt;

    public AmbientEventManager(final JavaPlugin plugin, final ConfigManager configManager,
                               final MessageManager messageManager, final CurrencyManager currencyManager,
                               final FactionManager factionManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.nextAttemptAt = System.currentTimeMillis() + intervalMillis();
    }

    /** Periodic attempt on the global world-events tick. */
    public void tick() {
        if (!configManager.getBoolean("ambient-events.enabled", true)) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now < nextAttemptAt) {
            return;
        }
        nextAttemptAt = now + intervalMillis();

        final int minOnline = Math.max(0, configManager.getInt("ambient-events.min-online-players", 1));
        if (Bukkit.getOnlinePlayers().size() < minOnline) {
            return;
        }

        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("ambient-events.chance-percent", 35.0D)));
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return;
        }

        final List<Ambient> enabled = collectEnabled();
        if (enabled.isEmpty()) {
            return;
        }

        final World world = mainWorld();
        if (world == null) {
            return;
        }

        final Ambient chosen = enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
        // Environmental gate: wrong time of day / weather for this flavour → skip this
        // cycle silently. We do NOT reroll another flavour, so gated-out flavours stay rare.
        if (!chosen.environmentAllows(world)) {
            return;
        }
        fire(chosen, world);
    }

    /** Admin override: fires a random enabled ambient event now, bypassing the environmental
     * gate and the min-online-players guard (it is an explicit debug/admin action). Returns
     * false if none are enabled or there is no world to fire it in. */
    public boolean forceRandom() {
        final List<Ambient> enabled = collectEnabled();
        if (enabled.isEmpty()) {
            return false;
        }
        final World world = mainWorld();
        if (world == null) {
            return false;
        }
        fire(enabled.get(ThreadLocalRandom.current().nextInt(enabled.size())), world);
        return true;
    }

    private List<Ambient> collectEnabled() {
        final List<Ambient> enabled = new ArrayList<>();
        for (final Ambient ambient : Ambient.values()) {
            if (configManager.getBoolean("ambient-events.types." + ambient.configKey(), true)) {
                enabled.add(ambient);
            }
        }
        return enabled;
    }

    private World mainWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private void fire(final Ambient ambient, final World world) {
        switch (ambient) {
            case AURORA -> aurora();
            case FALLING_STAR -> fallingStar();
            // Elnyújtott, ízléshez igazított effektek: a köd a talajon terül, a lelkek
            // derékmagasságban sodródnak, a szentjánosbogarak bokor-magasságban pislákolnak.
            case FOG_ROLL -> ambientEffect("ambient-fog", Particle.CLOUD, Sound.WEATHER_RAIN, 0.9F,
                    0.3D, 9.0D, 0.4D, 14, 0.0D);
            case SPECTRAL_WANDERERS -> ambientEffect("ambient-spectral", Particle.SOUL,
                    Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.7F, 1.4D, 7.0D, 1.0D, 6, 0.01D);
            case FIREFLIES -> ambientEffect("ambient-fireflies", Particle.END_ROD,
                    Sound.BLOCK_BEEHIVE_WORK, 0.5F, 1.2D, 8.0D, 1.2D, 4, 0.0D);
            case ANIMAL_MIGRATION -> animalMigration();
        }
        rewardParticipants(ambient, world);
    }

    /** Aurora: broadcast + shimmering sky particles and a brief, purely-cosmetic Night Vision. */
    private void aurora() {
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "ambient-aurora", "&b🌌 Északi fény ragyog fel az égen — a világ egy pillanatra elcsendesedik."));
        final int seconds = Math.max(5, configManager.getInt("ambient-events.aurora-nightvision-seconds", 45));
        final int pulses = Math.max(4, configManager.getInt("ambient-events.effect-seconds", 24) / 2);
        final int veilTicks = Math.max(80, configManager.getInt("ambient-events.effect-seconds", 24) * 20);
        final boolean veil = configManager.getBoolean("display-fx.aurora.enabled", true);
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, seconds * 20, 0, true, false, true));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.2F);
                // A fény MAGASAN az égen hullámzik végig az effekt-időn át, nem a fej fölött villan.
                pulse(player, Particle.END_ROD, 18.0D, 12.0D, 3.0D, 25, 0.01D, pulses);
                pulse(player, Particle.SOUL_FIRE_FLAME, 20.0D, 10.0D, 2.0D, 10, 0.0D, pulses);
                if (veil) {
                    spawnAuroraVeil(player, veilTicks);
                }
            }, null);
        }
    }

    /**
     * Aurora DisplayFx-fátyol: két magasan lebegő, áttetsző, lassan oldalra sodródó fény-lap
     * (BlockDisplay), csak a nézőnek — a pislákoló particle mellé folytonos égi fény-fátyol.
     * Nem-perzisztens + FX-tagelt, az effekt végén auto-despawn. A hívó a játékos szálán fut.
     */
    private void spawnAuroraVeil(final Player player, final int durationTicks) {
        final Location base = player.getLocation();
        if (base.getWorld() == null) {
            return;
        }
        final String matName = configManager.getString("display-fx.aurora.material", "CYAN_STAINED_GLASS");
        final org.bukkit.Material mat = org.bukkit.Material.matchMaterial(matName);
        final org.bukkit.block.data.BlockData block =
                (mat != null && mat.isBlock() ? mat : org.bukkit.Material.CYAN_STAINED_GLASS).createBlockData();
        final float size = 30.0F;
        final int[] glow = {0x53F0C8, 0x8A6BFF};
        for (int i = 0; i < 2; i++) {
            final int band = i;
            final Location at = base.clone().add(-size / 2.0D + band * 4.0D, 22.0D + band * 3.0D, -size / 2.0D);
            hu.taliann.icesmp.utils.DisplayFxUtil.spawnBlockDisplay(plugin, at, block, durationTicks + 20, display -> {
                display.setTransformation(hu.taliann.icesmp.utils.DisplayFxUtil.scale(size, 0.2F, size));
                display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                display.setViewRange(4.0F);
                display.setGlowing(true);
                display.setGlowColorOverride(org.bukkit.Color.fromRGB(glow[band]));
                hu.taliann.icesmp.utils.DisplayFxUtil.showOnlyTo(plugin, display, player);
                hu.taliann.icesmp.utils.DisplayFxUtil.driftHorizontal(plugin, display, size, 0.2F,
                        band == 0 ? 6.0F : -5.0F, 3.0F, durationTicks);
            });
        }
    }

    /** Falling star: broadcast with coordinates near a random player + a streaking particle trail. */
    private void fallingStar() {
        final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        final Player anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        anchor.getScheduler().run(plugin, task -> {
            final Location where = anchor.getLocation().clone();
            // Broadcast-diéta: a hullócsillag helyi látvány — csak a környékbeliek hallanak róla.
            hu.taliann.icesmp.utils.LocalAnnounce.nearby(plugin, where,
                    configManager.getDouble("ambient-events.local-announce-radius", 192.0D),
                    messageManager.getMessage(
                            "ambient-falling-star",
                            "&e☄ Hulló csillag hasít át az égbolton feletted ({x}, {z})!",
                            Map.of(
                                    "world", where.getWorld() == null ? "?" : where.getWorld().getName(),
                                    "x", String.valueOf(where.getBlockX()),
                                    "z", String.valueOf(where.getBlockZ())
                            )
                    ));
            // A short streak of sparks overhead, visible to nearby players.
            final World world = where.getWorld();
            if (world != null) {
                final Location high = where.clone().add(0.0D, 18.0D, 0.0D);
                world.spawnParticle(Particle.FIREWORK, high, 30, 4.0D, 6.0D, 4.0D, 0.05D);
                world.spawnParticle(Particle.END_ROD, high, 30, 3.0D, 5.0D, 3.0D, 0.02D);
                world.playSound(where, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0F, 1.4F);
            }
        }, null);
    }

    /**
     * Elnyújtott hangulat-effekt: a korábbi egyszeri "particle-robbanás" helyett a hatás
     * ~2 másodpercenként pulzál a teljes effekt-időn át (ambient-events.effect-seconds,
     * default 24 mp), a flavour-höz illő magasságban/terítéssel a játékos KÖRÜL — a
     * szentjánosbogár pislákol, a köd terül, nem egy villanás a fej fölött.
     */
    private void ambientEffect(final String messageKey, final Particle particle, final Sound sound,
                               final float volume, final double yOffset, final double spreadXz,
                               final double spreadY, final int perPulse, final double speed) {
        Bukkit.getServer().broadcast(messageManager.getMessage(messageKey, defaultFor(messageKey)));
        final int pulses = Math.max(4, configManager.getInt("ambient-events.effect-seconds", 24) / 2);
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> {
                player.playSound(player.getLocation(), sound, volume, 1.0F);
                pulse(player, particle, yOffset, spreadXz, spreadY, perPulse, speed, pulses);
            }, null);
        }
    }

    /**
     * Egy effekt-pulzus + a következő ütemezése a játékos saját entity-schedulerén (40 tick).
     * A lánc a pulzus-számláló lejártával, kilépéskor (isOnline) vagy a retired-callbackkel
     * áll le — nincs örök task.
     */
    private void pulse(final Player player, final Particle particle, final double yOffset,
                       final double spreadXz, final double spreadY, final int count,
                       final double speed, final int remaining) {
        if (remaining <= 0 || !player.isOnline()) {
            return;
        }
        final Location base = player.getLocation().clone().add(0.0D, yOffset, 0.0D);
        hu.taliann.icesmp.utils.ParticleUtil.spawn(player, particle, base, count, spreadXz, spreadY, spreadXz, speed);
        player.getScheduler().runDelayed(plugin, task ->
                pulse(player, particle, yOffset, spreadXz, spreadY, count, speed, remaining - 1), null, 40L);
    }

    /** Animal migration: a small herd of passive animals wanders in near a random player. */
    private void animalMigration() {
        final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        final Player anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        final int count = Math.max(1, configManager.getInt("ambient-events.migration-herd-size", 5));
        anchor.getScheduler().run(plugin, task -> {
            final Location center = anchor.getLocation().clone();
            plugin.getServer().getRegionScheduler().run(plugin, center, spawn -> spawnHerd(center, count));
        }, null);
    }

    private void spawnHerd(final Location center, final int count) {
        final World world = center.getWorld();
        if (world == null) {
            return;
        }
        final EntityType type = HERD[ThreadLocalRandom.current().nextInt(HERD.length)];
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            final double dx = ThreadLocalRandom.current().nextDouble(-6.0D, 6.0D);
            final double dz = ThreadLocalRandom.current().nextDouble(-6.0D, 6.0D);
            final int x = center.getBlockX() + (int) Math.round(dx);
            final int z = center.getBlockZ() + (int) Math.round(dz);
            final int y = world.getHighestBlockYAt(x, z) + 1;
            final Location spot = new Location(world, x + 0.5D, y, z + 0.5D);
            try {
                world.spawnEntity(spot, type);
                spawned++;
            } catch (final IllegalArgumentException ignored) {
                // Non-spawnable type in this context — skip.
            }
        }
        if (spawned > 0) {
            world.spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(0.0D, 1.0D, 0.0D), 12, 3.0D, 1.0D, 3.0D, 0.0D);
            // Broadcast-diéta: a csorda egy embernek szóló szerencse — helyi hír.
            hu.taliann.icesmp.utils.LocalAnnounce.nearby(plugin, center,
                    configManager.getDouble("ambient-events.local-announce-radius", 192.0D),
                    messageManager.getMessage(
                            "ambient-migration", "&a🐾 Vándorló állatcsorda kelt át a közeledben — élelem az éber telepeseknek."));
        }
    }

    /**
     * Active-participation reward: players standing outdoors (sky access) in the firing
     * world when the event lands get a small currency drop into their own faction balance
     * plus a flavour-fitting potion effect. Each player is visited at most once per firing,
     * so the reward can only land once per event per player. Every player- and
     * location-touching check hops to the player's own region thread (Folia rule).
     */
    private void rewardParticipants(final Ambient ambient, final World world) {
        final double rewardAmount = Math.max(0.0D, configManager.getDouble("ambient-events.reward-amount", 5.0D));
        for (final Player player : List.copyOf(world.getPlayers())) {
            player.getScheduler().run(plugin, task -> rewardIfOutdoors(ambient, player, rewardAmount), null);
        }
    }

    /** AFK-fék (setterrel kötve — az AfkManager később épül a DI-sorrendben). */
    private volatile AfkManager afkManager;

    public void setAfkManager(final AfkManager afkManager) {
        this.afkManager = afkManager;
    }

    /** Runs on the player's own region thread: checks sky access and, if outdoors, pays out. */
    private void rewardIfOutdoors(final Ambient ambient, final Player player, final double rewardAmount) {
        // AFK-parkoló ne szedje fel a hangulat-esemény pénzét (auto-farm guard, mint a többi jutalomnál).
        final AfkManager afkRef = afkManager;
        if (afkRef != null && afkRef.isAfk(player.getUniqueId())) {
            return;
        }
        final Location location = player.getLocation();
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final int highestY = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ());
        if (location.getBlockY() < highestY - 1) {
            // Under a roof / underground — no sky access, no reward for this firing.
            return;
        }

        if (ambient.rewardEffect != null) {
            player.addPotionEffect(new PotionEffect(ambient.rewardEffect, ambient.rewardSeconds * 20, 0, true, true, true));
        }

        if (rewardAmount > 0.0D) {
            final FactionType faction = factionManager.getFaction(player.getUniqueId());
            currencyManager.payOutTokens(player, CurrencyType.fromFactionType(faction), Math.round(rewardAmount));
            player.sendMessage(messageManager.getMessage(
                    "ambient-reward", "&d✨ Az esemény megérintett: &f+{amount} érme",
                    Map.of("amount", formatAmount(rewardAmount))));
        }
    }

    private static String formatAmount(final double amount) {
        if (amount == Math.rint(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private String defaultFor(final String messageKey) {
        return switch (messageKey) {
            case "ambient-fog" -> "&7🌫 Sűrű köd ereszkedik a tájra — óvatosan az utakon.";
            case "ambient-spectral" -> "&8👻 Bolyongó szellemek suhannak át a vidéken az éj leple alatt.";
            case "ambient-fireflies" -> "&e✨ Szentjánosbogarak raja gyúl fel a szürkületben.";
            default -> "&7Valami megmozdul a világban…";
        };
    }

    private long intervalMillis() {
        return Math.max(1L, configManager.getLong("ambient-events.interval-minutes", 40L)) * 60_000L;
    }
}
