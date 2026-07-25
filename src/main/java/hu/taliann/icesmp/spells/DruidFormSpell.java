package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Druid form/stance spell. The Druid does not change model (a true visual
 * shapeshift would need client-side packet disguises); instead each form is a
 * mutually-exclusive STANCE that swaps the player's stat profile, giving the
 * WoW-style "bear tank / cat skirmisher / moonkin caster / travel" feel through
 * gameplay rather than appearance.
 *
 * <p>Casting a form removes the previous form's effects and applies this one's as
 * long, low-noise potion effects (recastable, no scheduler needed — Folia-safe,
 * runs on the casting player's region thread). Casting the form you are already in
 * drops back to the formless (humanoid) stance. Per-player state is static and
 * shared across the four registered form instances, and is wiped on logout via
 * {@link #clearPlayerState(UUID)} (the {@code SpellRegistry} iterates it).
 */
public final class DruidFormSpell extends BaseSpell {

    /** Long enough to feel persistent; recast to refresh, switch to replace. */
    private static final int FORM_DURATION_TICKS = 20 * 60 * 30;

    /** The player's active form, shared by all four form instances (each is a singleton). */
    private static final Map<UUID, Form> ACTIVE_FORM = new ConcurrentHashMap<>();

    /** A single stat modifier the form applies. */
    private record FormEffect(PotionEffectType type, int amplifier) {
    }

    /** The four Druid stances and their stat profiles (only verified effect types used). */
    public enum Form {
        BEAR("druid_bear_form", "Medveforma", "Tank-alak: ellenállás, felszívás és erő, kissé lassabban.",
                "POLAR_BEAR", Particle.CRIT, Sound.ENTITY_RAVAGER_ROAR, new FormEffect[]{
                new FormEffect(PotionEffectType.RESISTANCE, 0),
                new FormEffect(PotionEffectType.ABSORPTION, 1),
                new FormEffect(PotionEffectType.STRENGTH, 0),
                new FormEffect(PotionEffectType.SLOWNESS, 0),
                new FormEffect(PotionEffectType.HUNGER, 0)
        }),
        CAT("druid_cat_form", "Párducforma", "Fürge harci alak: sebesség és erő a gyors lecsapásokhoz.",
                "CAT", Particle.SMOKE, Sound.ENTITY_PHANTOM_AMBIENT, new FormEffect[]{
                new FormEffect(PotionEffectType.SPEED, 1),
                new FormEffect(PotionEffectType.STRENGTH, 0)
        }),
        MOONKIN("druid_moonkin_form", "Holdforma", "Varázsló-alak: regeneráció és tűzállóság a hold-mágiához.",
                "PARROT", Particle.END_ROD, Sound.BLOCK_BEACON_ACTIVATE, new FormEffect[]{
                new FormEffect(PotionEffectType.REGENERATION, 0),
                new FormEffect(PotionEffectType.FIRE_RESISTANCE, 0),
                new FormEffect(PotionEffectType.MINING_FATIGUE, 5)
        }),
        TRAVEL("druid_travel_form", "Utazóforma", "Utazó-alak: jelentős sebesség a gyors helyváltáshoz.",
                "HORSE", Particle.CLOUD, Sound.ENTITY_BREEZE_SHOOT, new FormEffect[]{
                new FormEffect(PotionEffectType.SPEED, 2),
                new FormEffect(PotionEffectType.WEAKNESS, 1)
        });

        private final String id;
        private final String defaultName;
        private final String description;
        private final String disguiseType;
        private final Particle particle;
        private final Sound sound;
        private final FormEffect[] effects;

        Form(final String id, final String defaultName, final String description, final String disguiseType,
             final Particle particle, final Sound sound, final FormEffect[] effects) {
            this.id = id;
            this.defaultName = defaultName;
            this.description = description;
            this.disguiseType = disguiseType;
            this.particle = particle;
            this.sound = sound;
            this.effects = effects;
        }
    }

    /** Párducforma ára (playtest-balansz): -4 teljes szív max-élet, amíg a forma aktív. */
    private static final NamespacedKey CAT_HEALTH_KEY = new NamespacedKey("icesmp", "druid_cat_form_health");
    private static final double CAT_HEALTH_PENALTY = -8.0D;

    private final Form form;

    public DruidFormSpell(final MessageManager messageManager, final Form form) {
        super(messageManager, form.id, form.defaultName, 6, SpellCostType.HUNGER, 1);
        this.form = form;
    }

    @Override
    public void execute(final Player player) {
        final UUID playerId = player.getUniqueId();
        final Form previous = ACTIVE_FORM.get(playerId);
        removeFormEffects(player, previous);

        if (previous == form) {
            ACTIVE_FORM.remove(playerId);
            hu.taliann.icesmp.integration.DruidDisguise.clear(player);
            player.sendMessage(resolveMessage("spell." + form.id + ".off", "<gray>Visszaváltozol emberi alakodba.</gray>"));
            return;
        }

        final int durationTicks = balanceInt("duration-ticks", FORM_DURATION_TICKS);
        for (final FormEffect effect : form.effects) {
            player.addPotionEffect(new PotionEffect(effect.type(), durationTicks, effect.amplifier(), true, false, true));
        }
        if (form == Form.CAT) {
            applyCatHealthPenalty(player);
        }
        ACTIVE_FORM.put(playerId, form);
        // Optional visual layer: disguise as the matching mob when LibsDisguises is installed (else no-op).
        hu.taliann.icesmp.integration.DruidDisguise.apply(player, form.disguiseType);
        player.getWorld().spawnParticle(form.particle, player.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.4D, 0.6D, 0.4D, 0.02D);
        player.getWorld().playSound(player.getLocation(), form.sound, 1.0F, 1.0F);
        player.sendMessage(resolveMessage("spell." + form.id + ".on", "<green>Felveszed a(z) " + form.defaultName + "át.</green>"));
    }

    private static void removeFormEffects(final Player player, final Form which) {
        if (which == null) {
            return;
        }
        for (final FormEffect effect : which.effects) {
            player.removePotionEffect(effect.type());
        }
        if (which == Form.CAT) {
            removeCatHealthPenalty(player);
        }
    }

    private static void applyCatHealthPenalty(final Player player) {
        final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        removeCatHealthPenalty(player);
        // Transient: kilépéskor magától lejár, így relog után sosem ragad be a levont élet.
        maxHealth.addTransientModifier(new AttributeModifier(CAT_HEALTH_KEY,
                balanceStatic("druid_cat_form", "health-penalty", CAT_HEALTH_PENALTY),
                AttributeModifier.Operation.ADD_NUMBER));
        if (player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    private static void removeCatHealthPenalty(final Player player) {
        final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        for (final AttributeModifier modifier : java.util.List.copyOf(maxHealth.getModifiers())) {
            if (CAT_HEALTH_KEY.equals(modifier.getKey())) {
                maxHealth.removeModifier(modifier);
            }
        }
    }

    @Override
    public java.util.List<String> describe() {
        return java.util.List.of(form.description, "Stance — újra elsütve visszaváltozol emberi alakba.");
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final Form current = ACTIVE_FORM.remove(playerId);
        if (current == null) {
            return;
        }
        final Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            removeFormEffects(player, current);
            hu.taliann.icesmp.integration.DruidDisguise.clear(player);
        }
    }
}
