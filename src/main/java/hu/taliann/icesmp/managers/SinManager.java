package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Owns the sin / dark-pact / bounty domain — split out of the (weapon-named)
 * {@code MetelytepoManager} so the mechanic lives in a class named for what it
 * is. All state is player PDC ({@code is_sinner}, {@code dark_pact},
 * {@code sin_count}); there is no volatile per-player state, so this manager
 * needs no cleanup hook.
 *
 * <p>A sin is recorded by {@link #addSin(Player, int)} (murder, betrayal,
 * theft — the listeners decide the weight); reaching the configured threshold
 * exiles the player to the Dark faction and seals the permanent dark pact. The
 * only way back is the penance quest chain ({@link #breakDarkPact(Player)}).
 * The bounty system reads {@link #getSinCount(Player)} and resets it on a
 * justified execution ({@link #resetSinCount(Player)}). The Mételytépő relic
 * reads {@link #isSinner(Player)} for its targeting.</p>
 */
public final class SinManager {

    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final FactionManager factionManager;
    private final NamespacedKey sinnerKey;
    private final NamespacedKey darkPactKey;
    private final NamespacedKey sinCountKey;

    public SinManager(final JavaPlugin plugin, final ConfigManager configManager,
                      final MessageManager messageManager, final FactionManager factionManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
        // Identical key strings to the pre-split MetelytepoManager, so existing
        // player data (sinner mark, dark pact, sin counter) is preserved.
        this.sinnerKey = new NamespacedKey(plugin, "is_sinner");
        this.darkPactKey = new NamespacedKey(plugin, "dark_pact");
        this.sinCountKey = new NamespacedKey(plugin, "sin_count");
    }

    public int getSinCount(final Player player) {
        return player == null ? 0
                : player.getPersistentDataContainer().getOrDefault(sinCountKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * Records a sin: marks the player as sinner, increments the sin counter,
     * and once the configured threshold is reached the sinner is automatically
     * exiled to the Dark faction (sealing the permanent dark pact).
     *
     * @param player the sinning player
     * @param amount how many sins to add
     * @return the new sin count
     */
    public int addSin(final Player player, final int amount) {
        if (player == null || amount <= 0) {
            return getSinCount(player);
        }

        final int newCount = getSinCount(player) + amount;
        player.getPersistentDataContainer().set(sinCountKey, PersistentDataType.INTEGER, newCount);
        markAsSinner(player);

        final int exileThreshold = Math.max(0, configManager.getInt("factions.sins.exile-threshold", 4));
        if (exileThreshold > 0 && newCount >= exileThreshold
                && factionManager.getFaction(player.getUniqueId()) != FactionType.DARK) {
            exileToDark(player);
        }

        return newCount;
    }

    private void exileToDark(final Player player) {
        factionManager.setFaction(player.getUniqueId(), FactionType.DARK);
        sealDarkPact(player);
        AdvancementService.award(player, "exiled");
        player.sendMessage(messageManager.getMessage(
                "sinner.exiled",
                "<dark_purple>Bűneid súlya alatt összeroskadt a becsületed: a Kitaszítottak közé száműztek. A paktum örök.</dark_purple>"
        ));
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "sinner.exile-broadcast",
                "<dark_purple>{player} bűnei elérték a tűréshatárt — a Kitaszítottak közé száműzték!</dark_purple>",
                Map.of("player", player.getName())
        ));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6F, 0.7F);
        player.getWorld().spawnParticle(Particle.SQUID_INK, player.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.4D, 0.6D, 0.4D, 0.03D);
    }

    public boolean isSinner(final Player player) {
        return player != null
                && player.getPersistentDataContainer().getOrDefault(sinnerKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    public void markAsSinner(final Player player) {
        if (player != null) {
            player.getPersistentDataContainer().set(sinnerKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    /**
     * Resets the sin counter to zero while KEEPING the sinner mark (and dark
     * pact). Used by the bounty system: an executed criminal "paid" with their
     * life, so their bounty resets, but they remain a marked sinner.
     *
     * @param player the executed player
     */
    /** G6 — becsület-párbaj: bűnpont-csökkentés (0 alá nem megy). */
    public int reduceSin(final Player player, final int amount) {
        final int current = getSinCount(player);
        final int reduced = Math.max(0, current - Math.max(0, amount));
        player.getPersistentDataContainer().set(sinCountKey, org.bukkit.persistence.PersistentDataType.INTEGER, reduced);
        return reduced;
    }

    public void resetSinCount(final Player player) {
        if (player != null) {
            player.getPersistentDataContainer().set(sinCountKey, PersistentDataType.INTEGER, 0);
        }
    }

    /**
     * Seals the dark pact on a player who joined the Dark faction:
     * the sinner mark becomes permanent and can never be cleansed again.
     *
     * @param player the player joining the Dark faction
     */
    public void sealDarkPact(final Player player) {
        if (player == null) {
            return;
        }

        player.getPersistentDataContainer().set(darkPactKey, PersistentDataType.BYTE, (byte) 1);
        markAsSinner(player);
    }

    public boolean hasDarkPact(final Player player) {
        return player != null
                && player.getPersistentDataContainer().getOrDefault(darkPactKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /**
     * Breaks the dark pact (the only path: the completed penance quest chain).
     * Removes the pact, the sinner mark and the sin counter with a redemption effect.
     *
     * @param player the redeemed player
     */
    public void breakDarkPact(final Player player) {
        if (player == null) {
            return;
        }

        player.getPersistentDataContainer().remove(darkPactKey);
        player.getPersistentDataContainer().remove(sinCountKey);
        player.getPersistentDataContainer().remove(sinnerKey);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.4F);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D), 60, 0.5D, 0.8D, 0.5D, 0.05D);
        AdvancementService.award(player, "redeemed");
        player.sendMessage(messageManager.getMessage(
                "sinner.pact-broken",
                "<gold>A vezeklésed teljes: a sötét paktum megtört, bűneid feloldozást nyertek.</gold>"
        ));
    }

    /**
     * Clears the sinner mark unless the player is bound by the dark pact.
     *
     * @param player the player to cleanse
     * @return true if the mark was removed (or was absent), false if the dark pact blocks it
     */
    public boolean clearSinner(final Player player) {
        if (player == null) {
            return true;
        }

        if (hasDarkPact(player)) {
            return false;
        }

        // Cleansing also wipes the sin counter.
        player.getPersistentDataContainer().remove(sinCountKey);

        if (!player.getPersistentDataContainer().has(sinnerKey, PersistentDataType.BYTE)) {
            return true;
        }

        player.getPersistentDataContainer().remove(sinnerKey);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.7F, 1.8F);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D), 24, 0.35D, 0.5D, 0.35D, 0.02D);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0D, 1.0D, 0.0D), 16, 0.25D, 0.4D, 0.25D, 0.01D);
        player.sendMessage(messageManager.getMessage("sinner.cleansed", "<green><i>Megtisztultal a buneidtol...</i></green>"));
        return true;
    }
}
