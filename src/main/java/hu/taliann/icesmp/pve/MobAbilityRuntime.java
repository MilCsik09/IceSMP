package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.EventSpawnGuard;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.utils.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Event-driven, per-entity ability and elite-affix runtime. No global mob scan is used. */
public final class MobAbilityRuntime implements Listener {
    private static final int MAX_ACTIVE_MOBS = 2048;
    private static final long RUNTIME_STEP_TICKS = 20L;

    private static final class RuntimeState {
        private final Map<String, Long> readyAtTick = new LinkedHashMap<>();
        private long tick;
        private boolean casting;
    }

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MobScalingManager scaling;
    private final MobTemplateRegistry templates;
    private final MobAbilityRegistry abilities;
    private final Map<UUID, RuntimeState> states = new ConcurrentHashMap<>();
    private final NamespacedKey volatileArmedKey;
    private final NamespacedKey frenziedKey;
    private final NamespacedKey summonOwnerKey;

    public MobAbilityRuntime(final JavaPlugin plugin, final ConfigManager config,
                             final MobScalingManager scaling,
                             final MobTemplateRegistry templates,
                             final MobAbilityRegistry abilities) {
        this.plugin = plugin;
        this.config = config;
        this.scaling = scaling;
        this.templates = templates;
        this.abilities = abilities;
        this.volatileArmedKey = new NamespacedKey(plugin, "mob_volatile_armed");
        this.frenziedKey = new NamespacedKey(plugin, "mob_frenzied_active");
        this.summonOwnerKey = new NamespacedKey(plugin, "mob_summon_owner");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(final CreatureSpawnEvent event) {
        final LivingEntity entity = event.getEntity();
        // Event/authored spawners attach their template after World#spawn returns.
        entity.getScheduler().runDelayed(plugin, task -> attach(entity), null, 1L);
    }

    public void attach(final LivingEntity entity) {
        if (!(entity instanceof Mob mob) || !mob.isValid()) return;
        final String templateId = scaling.getTemplateId(mob);
        final MobTemplate template = templates.find(templateId).orElse(null);
        final ArrayList<MobAbilityDefinition> definitions = new ArrayList<>();
        if (template != null) {
            for (final String abilityId : template.abilityIds()) definitions.add(abilities.require(abilityId));
        }
        final List<EliteAffix> affixes = scaling.getAffixes(mob);
        if (affixes.contains(EliteAffix.ARCANE)) addIfAbsent(definitions, "rime_burst");
        if (affixes.contains(EliteAffix.SUMMONER)) addIfAbsent(definitions, "call_frozen");
        if (affixes.contains(EliteAffix.SHIELDED)) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    Integer.MAX_VALUE, 0, false, true, true));
        }
        if (definitions.isEmpty() && affixes.isEmpty()) return;
        final RuntimeState state = new RuntimeState();
        if (!registerState(mob.getUniqueId(), state)) return;
        try {
            mob.getScheduler().runAtFixedRate(plugin,
                    task -> tick(mob, definitions, state),
                    () -> states.remove(mob.getUniqueId(), state),
                    RUNTIME_STEP_TICKS, RUNTIME_STEP_TICKS);
        } catch (final RuntimeException rejected) {
            states.remove(mob.getUniqueId(), state);
        }
    }

    /** Serialises producer admission so concurrent Folia regions cannot overshoot the hard cap. */
    private boolean registerState(final UUID mobId, final RuntimeState state) {
        synchronized (states) {
            if (states.containsKey(mobId) || states.size() >= MAX_ACTIVE_MOBS) return false;
            states.put(mobId, state);
            return true;
        }
    }

    private void addIfAbsent(final List<MobAbilityDefinition> definitions, final String abilityId) {
        if (definitions.stream().noneMatch(definition -> definition.abilityId().equals(abilityId))) {
            abilities.find(abilityId).ifPresent(definitions::add);
        }
    }

    private void tick(final Mob mob, final List<MobAbilityDefinition> definitions,
                      final RuntimeState state) {
        if (!mob.isValid() || mob.isDead()) {
            states.remove(mob.getUniqueId(), state);
            return;
        }
        state.tick += RUNTIME_STEP_TICKS;
        if (state.casting || definitions.isEmpty()) return;
        final MobAbilityDefinition chosen = definitions.stream()
                .filter(definition -> state.tick >= state.readyAtTick
                        .getOrDefault(definition.abilityId(), 0L))
                .findFirst().orElse(null);
        if (chosen == null) return;
        final Location target = targetSnapshot(mob, chosen.radius());
        if (requiresTarget(chosen.kind()) && target == null) return;
        state.casting = true;
        state.readyAtTick.put(chosen.abilityId(), state.tick + chosen.cooldownTicks());
        telegraph(mob, chosen, target);
        try {
            mob.getScheduler().runDelayed(plugin, task -> {
                try {
                    if (mob.isValid() && !mob.isDead()) execute(mob, chosen, target);
                } finally {
                    state.casting = false;
                }
            }, () -> {
                state.casting = false;
                states.remove(mob.getUniqueId(), state);
            }, Math.max(1L, chosen.telegraphTicks()));
        } catch (final RuntimeException rejected) {
            state.casting = false;
            states.remove(mob.getUniqueId(), state);
        }
    }

    private Location targetSnapshot(final Mob mob, final double radius) {
        for (final Entity nearby : mob.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player player && Bukkit.isOwnedByCurrentRegion(player)
                    && survivor(player)) return player.getLocation().clone();
        }
        return null;
    }

    private void telegraph(final Mob mob, final MobAbilityDefinition definition,
                           final Location target) {
        final Location center = target == null ? mob.getLocation() : target;
        final Particle particle = definition.kind() == MobAbilityDefinition.Kind.PROJECTILE_BURST
                ? Particle.ELECTRIC_SPARK : Particle.SNOWFLAKE;
        ParticleUtil.spawn(center.getWorld(), particle, center.clone().add(0.0D, 0.2D, 0.0D),
                28, Math.min(3.0D, definition.radius()), 0.2D,
                Math.min(3.0D, definition.radius()), 0.01D);
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2F, 1.2F);
    }

    private void execute(final Mob mob, final MobAbilityDefinition definition,
                         final Location target) {
        switch (definition.kind()) {
            case LUNGE -> {
                if (target == null || target.getWorld() != mob.getWorld()) return;
                final Vector direction = target.toVector().subtract(mob.getLocation().toVector());
                if (direction.lengthSquared() > 0.01D) {
                    mob.setVelocity(direction.normalize().multiply(definition.power()).setY(0.25D));
                }
            }
            case GROUND_SLAM -> impactPlayers(mob, mob.getLocation(), definition.radius(),
                    definition.power(), true);
            case PROJECTILE_BURST -> {
                if (target == null || target.getWorld() != mob.getWorld()) return;
                final Vector center = target.toVector().subtract(mob.getEyeLocation().toVector())
                        .normalize();
                final int count = Math.max(1, Math.min(5, (int) Math.round(
                        definition.tuning().getOrDefault("projectiles", 3.0D))));
                for (int index = 0; index < count; index++) {
                    final Vector spread = center.clone().add(new Vector(
                            (index - (count - 1) / 2.0D) * 0.08D, 0.02D * index, 0.0D));
                    final Arrow projectile = mob.launchProjectile(Arrow.class,
                            spread.normalize().multiply(1.2D));
                    projectile.setDamage(definition.power());
                    projectile.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                }
            }
            case SHIELD -> mob.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    Math.max(40, (int) Math.min(400, definition.cooldownTicks() / 2)),
                    definition.power() >= 0.5D ? 1 : 0, false, true, true));
            case HEAL_PULSE -> {
                final double maximum = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                        ? mob.getHealth() : mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                mob.setHealth(Math.min(maximum, mob.getHealth() + maximum * definition.power()));
            }
            case SUMMON -> summonAdds(mob, definition);
        }
    }

    private void summonAdds(final Mob mob, final MobAbilityDefinition definition) {
        final int count = Math.min(definition.maxSummons(), Math.max(0,
                config.getInt("mob-scaling.abilities.maximum-summons-per-cast", 3)));
        final long lifespan = Math.max(40L, config.getLong(
                "mob-scaling.abilities.summon-lifespan-ticks", 300L));
        for (int index = 0; index < count; index++) {
            final Location at = mob.getLocation().clone().add(
                    ThreadLocalRandom.current().nextDouble(-2.5D, 2.5D), 0.0D,
                    ThreadLocalRandom.current().nextDouble(-2.5D, 2.5D));
            final Skeleton add = mob.getWorld().spawn(at, Skeleton.class);
            EventSpawnGuard.prepare(add);
            add.getPersistentDataContainer().set(summonOwnerKey, PersistentDataType.STRING,
                    mob.getUniqueId().toString());
            add.getScheduler().runDelayed(plugin, task -> {
                if (add.isValid()) add.remove();
            }, null, lifespan);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAffixDamage(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity attacker
                && event.getEntity() instanceof Player player) {
            final List<EliteAffix> affixes = scaling.getAffixes(attacker);
            if (affixes.contains(EliteAffix.FROSTBOUND)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        50, 0, false, true, true));
            }
            if (affixes.contains(EliteAffix.VAMPIRIC)) {
                final double healing = Math.min(12.0D, event.getFinalDamage() * 0.25D);
                attacker.getScheduler().run(plugin, task -> heal(attacker, healing), null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAffixHurt(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        final List<EliteAffix> affixes = scaling.getAffixes(mob);
        final double projected = mob.getHealth() - event.getFinalDamage();
        final double maximum = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? mob.getHealth() : mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        if (affixes.contains(EliteAffix.FRENZIED) && projected > 0.0D
                && projected <= maximum * 0.5D && !mob.getPersistentDataContainer()
                .has(frenziedKey, PersistentDataType.BYTE)) {
            mob.getPersistentDataContainer().set(frenziedKey, PersistentDataType.BYTE, (byte) 1);
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    Integer.MAX_VALUE, 0, false, true, true));
            mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                    Integer.MAX_VALUE, 0, false, true, true));
        }
        if (affixes.contains(EliteAffix.VOLATILE) && projected <= 0.0D
                && !mob.getPersistentDataContainer().has(volatileArmedKey, PersistentDataType.BYTE)) {
            mob.getPersistentDataContainer().set(volatileArmedKey, PersistentDataType.BYTE, (byte) 1);
            armVolatile(mob.getLocation().clone());
        }
    }

    private void armVolatile(final Location center) {
        ParticleUtil.spawn(center.getWorld(), Particle.FLAME, center, 36, 2.5D, 0.2D, 2.5D, 0.02D);
        center.getWorld().playSound(center, Sound.ENTITY_CREEPER_PRIMED, 1.2F, 1.0F);
        try {
            plugin.getServer().getRegionScheduler().runDelayed(plugin, center, task -> {
                ParticleUtil.spawn(center.getWorld(), Particle.EXPLOSION, center, 2);
                for (final Player player : center.getWorld().getNearbyPlayers(center, 3.0D)) {
                    player.getScheduler().run(plugin, hit -> {
                        if (survivor(player) && player.getWorld() == center.getWorld()
                                && player.getLocation().distanceSquared(center) <= 9.0D) {
                            player.damage(Math.max(1.0D, config.getDouble(
                                    "mob-scaling.affixes.volatile-damage", 5.0D)));
                        }
                    }, null);
                }
            }, 30L);
        } catch (final RuntimeException rejected) {
            // Scheduler rejection leaves only the harmless telegraph; no stale state is retained.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(final EntityDeathEvent event) {
        states.remove(event.getEntity().getUniqueId());
    }

    public void shutdown() { states.clear(); }

    public int activeStateCount() { return states.size(); }

    private static void heal(final LivingEntity entity, final double amount) {
        if (!entity.isValid() || entity.isDead() || amount <= 0.0D) return;
        final var max = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        entity.setHealth(Math.min(max == null ? entity.getHealth() : max.getValue(),
                entity.getHealth() + amount));
    }

    private void impactPlayers(final Mob caster, final Location center,
                               final double radius, final double damage,
                               final boolean knockback) {
        for (final Entity nearby : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Player player)) continue;
            player.getScheduler().run(plugin, task -> {
                if (!survivor(player) || player.getWorld() != center.getWorld()
                        || player.getLocation().distanceSquared(center) > radius * radius) return;
                player.damage(damage);
                if (knockback) {
                    final Vector vector = player.getLocation().toVector().subtract(center.toVector());
                    if (vector.lengthSquared() > 0.01D) player.setVelocity(
                            vector.normalize().multiply(0.8D).setY(0.45D));
                }
            }, null);
        }
    }

    private static boolean survivor(final Player player) {
        return player.isOnline() && (player.getGameMode() == GameMode.SURVIVAL
                || player.getGameMode() == GameMode.ADVENTURE);
    }

    private static boolean requiresTarget(final MobAbilityDefinition.Kind kind) {
        return kind == MobAbilityDefinition.Kind.LUNGE
                || kind == MobAbilityDefinition.Kind.PROJECTILE_BURST;
    }
}
