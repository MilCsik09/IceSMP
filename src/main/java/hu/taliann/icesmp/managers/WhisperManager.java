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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * K9 — a Suttogók hibrid modellje. A Suttogó egy REJTETT státusz a látható frakció
 * (RED/BLUE/NEUTRAL) fölött: titkos csatorna, sötét kötődés — kívülről semmi sem
 * árulja el. A lelepleződés köti a Kitaszítottakhoz: a gyanú-pont (suspicion)
 * sötét tettektől nő (rajtakapott árulás, rajtakapott rítus, tanú-vád), lassan
 * csökken, és a küszöbnél a világ lerántja az álcát — a játékos bűnökkel terhelve
 * a meglévő bűn/száműzetés-pipeline-on át a Kitaszítottak közé zuhan.
 *
 * <p>Állapot: player-PDC (whisperer-flag, suspicion) — nem szivárgó, restart-álló;
 * a rövid életű Tanú-tokenek concurrent map + {@code clearPlayerState} takarítás.
 * Folia: minden player-érintés a cél saját schedulerén; a decay-tick a globális
 * world-events tickről hopol. Minden küszöb/súly configból, élőben olvasva
 * (factions.whisper.*).
 */
public final class WhisperManager implements hu.taliann.icesmp.storage.PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final MessageManager messageManager;
    private final NamespacedKey whispererKey;
    private final NamespacedKey suspicionKey;
    /** Rövid életű Tanú-tokenek: UUID → lejárat (millis). */
    private final Map<UUID, Long> witnessUntil = new ConcurrentHashMap<>();
    /**
     * Online Suttogók cache-e (a PDC-t csak a játékos szálán olvashatjuk — a decay-tick és
     * a suttogás-kézbesítés e nélkül MINDEN online játékosra hopolna, csak hogy kiderüljön,
     * nem Suttogó). Join-kor töltődik (a join a játékos szálán fut), quit-kor ürül; a
     * rítus/leleplezés naprakészen tartja. A forrás az igazság mindig a PDC marad.
     */
    private final java.util.Set<UUID> whispererCache = ConcurrentHashMap.newKeySet();

    private volatile long nextDecayAt;

    public WhisperManager(final JavaPlugin plugin, final ConfigManager configManager,
                          final FactionManager factionManager, final SinManager sinManager,
                          final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.messageManager = messageManager;
        this.whispererKey = new NamespacedKey(plugin, "whisperer");
        this.suspicionKey = new NamespacedKey(plugin, "whisper_suspicion");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("factions.whisper.enabled", true);
    }

    /**
     * N25b-crossover — a Királynő kegye: amikor egy kultista esemény BETELJESÜL
     * (rítus / célba érő hírvivő), minden online, felesküdött Suttogó gyanúja
     * csillapodik — a hálózat sikere mélyíti az álcájukat. Ez adja a "védd meg az
     * eseményt" oldal tétjét a rejtett játékosoknak. Folia: minden PDC-írás az
     * érintett játékos SAJÁT régió-szálán fut (hop), az üzenet privát (nem leplez le).
     */
    public void rewardFaithful(final double relief) {
        if (!isEnabled() || relief <= 0.0D) {
            return;
        }
        for (final Player online : java.util.List.copyOf(org.bukkit.Bukkit.getOnlinePlayers())) {
            online.getScheduler().run(plugin, task -> {
                if (!isWhisperer(online)) {
                    return;
                }
                final double current = online.getPersistentDataContainer()
                        .getOrDefault(suspicionKey, PersistentDataType.DOUBLE, 0.0D);
                online.getPersistentDataContainer().set(suspicionKey, PersistentDataType.DOUBLE,
                        Math.max(0.0D, current - relief));
                online.sendMessage(messageManager.getMessage("whisper-queen-favor",
                        "<dark_gray>🕯 A Kapu érzi a hűséged — a gyanú árnyéka halványul körülötted. <gray>(−{relief} gyanú)</gray></dark_gray>",
                        java.util.Map.of("relief", String.valueOf((int) relief))));
                // Suttogó-erősítés (tulaj-kérés): a hálózat sikere TÁRGY-részesedést is hoz —
                // privát csomag a rítus-loot táblából (nem leplez le; tárgy, sosem pénz).
                final int lootRolls = Math.max(0, configManager.getInt("cultists.whisper-loot-rolls", 1));
                if (lootRolls > 0) {
                    boolean gaveAny = false;
                    for (final org.bukkit.inventory.ItemStack loot
                            : LootTable.roll(configManager, "cultists.rite-loot", lootRolls)) {
                        online.getInventory().addItem(loot).values().forEach(left ->
                                online.getWorld().dropItemNaturally(online.getLocation(), left));
                        gaveAny = true;
                    }
                    if (gaveAny) {
                        online.sendMessage(messageManager.getMessage("whisper-queen-share",
                                "<dark_gray>🕯 A hálózat osztozik a zsákmányon — csendben tedd el, ami a tiéd.</dark_gray>"));
                    }
                }
            }, null);
        }
    }

    /** Suttogó-e (a konkurens cache-ből — BÁRMELY régió-szálról biztonságos). */
    public boolean isWhispererCached(final UUID playerId) {
        return whispererCache.contains(playerId);
    }

    /** A játékos Suttogó-e (rejtett státusz; a játékos SAJÁT szálán olvasandó). */
    public boolean isWhisperer(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(whispererKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    /** Felveszi a Suttogó-státuszt (a Sötét Rítus sikere; a játékos saját szálán). */
    public void makeWhisperer(final Player player) {
        player.getPersistentDataContainer().set(whispererKey, PersistentDataType.BYTE, (byte) 1);
        player.getPersistentDataContainer().set(suspicionKey, PersistentDataType.DOUBLE, 0.0D);
        whispererCache.add(player.getUniqueId());
    }

    /** Join-kor hívandó (a játékos SAJÁT szálán — a WhisperListener köti be): cache-frissítés. */
    public void handleJoin(final Player player) {
        if (isWhisperer(player)) {
            whispererCache.add(player.getUniqueId());
        }
    }

    public double getSuspicion(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(suspicionKey, PersistentDataType.DOUBLE, 0.0D);
    }

    /**
     * Gyanú növelése (a CÉL saját régió-szálán hívandó). A küszöb átlépésekor a
     * leleplezés azonnal lefut.
     *
     * @param player the whisperer
     * @param amount gyanú-pont
     */
    public void addSuspicion(final Player player, final double amount) {
        if (!isEnabled() || !isWhisperer(player) || amount <= 0.0D) {
            return;
        }
        final double threshold = Math.max(1.0D, configManager.getDouble("factions.whisper.suspicion-threshold", 100.0D));
        final double next = getSuspicion(player) + amount;
        player.getPersistentDataContainer().set(suspicionKey, PersistentDataType.DOUBLE, next);
        if (next >= threshold) {
            expose(player);
        }
    }

    /** Tanú-token adása (aki sötét tettet LÁTOTT; bármely szálról hívható). */
    public void grantWitnessToken(final UUID playerId) {
        final long seconds = Math.max(10L, configManager.getLong("factions.whisper.witness-seconds", 120L));
        witnessUntil.put(playerId, System.currentTimeMillis() + seconds * 1000L);
    }

    /** Van-e élő Tanú-tokenje (a vádhoz); a token az ellenőrzéskor NEM fogy el. */
    public boolean hasWitnessToken(final UUID playerId) {
        final Long until = witnessUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            witnessUntil.remove(playerId);
            return false;
        }
        return true;
    }

    /** A vád elfogyasztja a tokent (cooldown-jelleg: egy token = egy vád). */
    public void consumeWitnessToken(final UUID playerId) {
        witnessUntil.remove(playerId);
    }

    /**
     * A leleplezés pillanata (a leleplezett SAJÁT régió-szálán fut): fény kialszik,
     * lélek-örvény, kürt; bűnök a fejére (a bűn-küszöb a meglévő száműzetés-mechanikát
     * indítja → Kitaszítottak), az álca lehull, szerver-broadcast.
     */
    public void expose(final Player player) {
        if (!isWhisperer(player)) {
            return;
        }
        player.getPersistentDataContainer().remove(whispererKey);
        player.getPersistentDataContainer().remove(suspicionKey);
        whispererCache.remove(player.getUniqueId());

        // Dráma: sötét örvény + kürt + vakság-pillanat.
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0.0D, 1.0D, 0.0D), 60, 0.6D, 1.0D, 0.6D, 0.05D);
        player.getWorld().spawnParticle(Particle.SQUID_INK, player.getLocation().add(0.0D, 1.5D, 0.0D), 30, 0.5D, 0.8D, 0.5D, 0.03D);
        player.getWorld().playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1.2F, 0.5F);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0F, 0.4F);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.BLINDNESS, 3 * 20, 0, false, false, false));

        // A bűn-pipeline viszi a Kitaszítottak közé (exile-threshold átlépése), és a
        // bűn-alapú vérdíj azonnal emelt fejpénzt jelent (áruló-prémium a súlyokból).
        final int sins = Math.max(1, configManager.getInt("factions.whisper.exposure-sins", 4));
        sinManager.addSin(player, sins);

        if (configManager.getBoolean("factions.whisper.expose-broadcast", true)) {
            Bukkit.getServer().broadcast(messageManager.getMessage(
                    "whisper-exposed",
                    "<dark_purple>💀 A Suttogás lelepleződött: <white>{player}</white> a Néma Királynő szolgája!</dark_purple>",
                    Map.of("player", player.getName())));
        }
    }

    /**
     * Suttogó-üzenet kézbesítése: minden online Suttogónak, ki-ki a SAJÁT régió-szálán
     * (a feladó ellenőrzése a hívó oldalán történt). A csatorna kívülről láthatatlan.
     */
    public void deliverWhisper(final Player sender, final String message) {
        final net.kyori.adventure.text.Component line = messageManager.getMessage(
                "whisper-chat-line",
                "<dark_purple>✧ Suttogás</dark_purple> <gray>{sender}:</gray> <light_purple>{message}</light_purple>",
                Map.of("sender", sender.getName(), "message", message));
        // Csak a cache szerinti Suttogókra (+ a feladóra) hopolunk — a többség kimarad.
        for (final Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!whispererCache.contains(online.getUniqueId())
                    && !online.getUniqueId().equals(sender.getUniqueId())) {
                continue;
            }
            online.getScheduler().run(plugin, task -> {
                if (isWhisperer(online) || online.getUniqueId().equals(sender.getUniqueId())) {
                    online.sendMessage(line);
                    online.playSound(online.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.4F, 1.6F);
                }
            }, null);
        }
    }

    /**
     * Lassú gyanú-csillapodás: a world-events tickről hívva, decay-minutes ütemben
     * minden online Suttogó gyanúja csökken (a titok magától halványul, ha nem
     * táplálják új sötét tettek).
     */
    public void tick() {
        if (!isEnabled()) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextDecayAt) {
            return;
        }
        nextDecayAt = now + Math.max(1L, configManager.getLong("factions.whisper.decay-minutes", 10L)) * 60_000L;
        final double decay = Math.max(0.0D, configManager.getDouble("factions.whisper.decay-amount", 5.0D));
        if (decay <= 0.0D) {
            return;
        }
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!whispererCache.contains(player.getUniqueId())) {
                continue; // Nem Suttogó (cache) — felesleges lenne a régió-hop.
            }
            player.getScheduler().run(plugin, task -> {
                if (!isWhisperer(player)) {
                    return;
                }
                final double current = getSuspicion(player);
                if (current > 0.0D) {
                    player.getPersistentDataContainer().set(suspicionKey, PersistentDataType.DOUBLE,
                            Math.max(0.0D, current - decay));
                }
            }, null);
        }
    }

    /** A Suttogó látható frakciója nem DARK — a leleplezés után a státusz értelmét veszti. */
    public boolean canBecomeWhisperer(final Player player) {
        return factionManager.getFaction(player.getUniqueId()) != FactionType.DARK && !isWhisperer(player);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        witnessUntil.remove(playerId);
        whispererCache.remove(playerId);
    }
}
