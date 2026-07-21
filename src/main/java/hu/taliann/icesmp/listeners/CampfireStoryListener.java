package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * D15 — Tábortűz-mesélés (lore: a kódex szájhagyomány-felülete). Tábortűz mellett
 * SNEAK+jobb-katt indítja a mesélést: ha a játékos hold-seconds ideig a tűz mellett
 * marad, kap egy véletlen sztori-sort (a kódex szájhagyománya) + kevés vanília-XP-t.
 * AFK-farm ellen: PDC-cooldown ({@code cd_campfire_story}) + alacsony összeg — a
 * jutalom csak SIKERES kitartás után jár, és csak akkor indul cooldown.
 *
 * <p>Folia: az interakció és a késleltetett ellenőrzés is a játékos SAJÁT
 * entity-schedulerén fut (a tábortűz kattintás-távolságban, régió-lokális).
 * Minden kulcs élőben olvasódik (campfire-story.*).
 */
public final class CampfireStoryListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final NamespacedKey cooldownKey;
    private final NamespacedKey pendingKey;

    /** Sztori-sor variánsok (messages-kulcs: campfire-story-1..6; config-listával bővíthető). */
    private static final String[] STORIES = {
            "<gray>🔥 „…és amikor a Fa első gyökere vizet ért, a jég megtanult énekelni. Így mesélik a régiek.”</gray>",
            "<gray>🔥 „A Vérháborúk előtt a két nép egy tűznél ült — mint most mi. A tűz emlékszik.”</gray>",
            "<gray>🔥 „Bokic vize sosem fagy be. Azt mondják, a folyó egy alvó szív ere.”</gray>",
            "<gray>🔥 „A Kapun túlról nem jött vissza senki. Csak a hamu — az minden tavasszal visszajön.”</gray>",
            "<gray>🔥 „A Számvevők mindent felírnak. De a tábortűz meséit sosem — azok a miénk.”</gray>",
            "<gray>🔥 „A Néma Királynő nem gonosz, fiam. Csak nagyon-nagyon régóta vár valakire.”</gray>",
            "<gray>🔥 „I. Zhoris lángmadarai? Á, azok nem madarak voltak. De ezt itt ne mondd hangosan.”</gray>",
            "<gray>🔥 „Glatziendorf falait nem kő tartja. Emlék tartja. Ezért nem dőlnek le soha.”</gray>",
            "<gray>🔥 „A Mélység Népe nem tűnt el, fiam. Csak elhallgatott. Az nem ugyanaz.”</gray>",
            "<gray>🔥 „Régen a két nép egy nyelvet beszélt. A szavak maradtak — csak a hangsúly lett fegyver.”</gray>",
            "<gray>🔥 „Az Idegen? Láttam egyszer. Vagy ő látott engem. Azóta se tudom, melyik a rosszabb.”</gray>",
            "<gray>🔥 „A Számvevők mindent tudnak a pénzedről. De hogy MIÉRT gyűjtöd — azt csak a tűz.”</gray>",
            "<gray>🔥 „Minden korszak úgy kezdődik, hogy valaki tüzet rak. Így, mint mi most.”</gray>",
            "<gray>🔥 „A sculk alatt nem üresség van. Fülek vannak. Ezért suttogunk a tűznél is.”</gray>",
            "<gray>🔥 „Nagyapám látta a Hetedik Vérháborút. Sosem beszélt róla. CSAK a tűznek, halkan.”</gray>",
            "<gray>🔥 „A Bokic egyszer kiöntött, és egy egész falut odébb rakott. Senki se halt meg. A folyó válogat.”</gray>",
            "<gray>🔥 „Az Arany Liga? Volt. Nincs. A Számvevők átvették a könyveiket, és a nevüket is kifizették.”</gray>",
            "<gray>🔥 „A jégsárkányok nem haltak ki, fiam. Alszanak. És Kallan tudja, hol.”</gray>",
            "<gray>🔥 „Thanaopolisban éjjel ne fütyülj. Nem babona. Csak udvariasság — valaki mindig hallgatózik.”</gray>",
            "<gray>🔥 „A Vándorünnep lepénye? Az igazi receptjét csak három szakács tudja, és nem beszélnek egymással.”</gray>",
            "<gray>🔥 „Volt egy uralkodó, I. Lineata, aki sosem vesztett csatát. Aztán egyszer nem jött vissza. A Könyvben üres a lapja.”</gray>",
            "<gray>🔥 „A meteorvas jó vas. De ha éjjel kovácsolod, néha visszakalapál.”</gray>",
            "<gray>🔥 „A Lapforduló Őre nem szörny. Őr. Az a kérdés, MITŐL őrzi a lapot… vagy KITŐL.”</gray>",
            "<gray>🔥 „A Kitaszítottak közt több a becsület, mint a fővárosban. Csak ott senki se írja fel.”</gray>",
            "<gray>🔥 „Az Első Csendről nem mesélünk. Ez a mese. Vége.”</gray>",
            "<gray>🔥 „A Fa egyik gyökere állítólag a tábortüzek alatt fut. Ezért melegszik át a történet is.”</gray>",
            "<gray>🔥 „Pyralingrad kohói sosem hűlnek ki. Egyszer kihűltek. Arról az évről nincs krónika.”</gray>",
            "<gray>🔥 „A Szellemszarvas nem hátas. Vendéglátó. Sose sarkantyúzd.”</gray>",
            "<gray>🔥 „Minden térképen van egy fehér folt. Nem azért, mert nem jártak ott. Azért, mert visszajöttek, és nem rajzolták be.”</gray>"
    };

    public CampfireStoryListener(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.cooldownKey = new NamespacedKey(plugin, "cd_campfire_story");
        this.pendingKey = new NamespacedKey(plugin, "campfire_story_pending");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCampfireUse(final PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getClickedBlock() == null) {
            return;
        }
        final Material type = event.getClickedBlock().getType();
        if (type != Material.CAMPFIRE && type != Material.SOUL_CAMPFIRE) {
            return;
        }
        final Player player = event.getPlayer();
        if (!player.isSneaking() || !configManager.getBoolean("campfire-story.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        final long cooldownMillis = Math.max(0L,
                configManager.getLong("campfire-story.cooldown-minutes", 60L)) * 60_000L;
        final Long lastStory = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        if (lastStory != null && now - lastStory < cooldownMillis) {
            return; // Csendben: a tábortűz vanília-interakcióját nem zavarjuk üzenettel.
        }
        // Egyszerre csak egy futó mesélés (dupla kattra ne induljon két időzítő).
        final Long pending = player.getPersistentDataContainer().get(pendingKey, PersistentDataType.LONG);
        if (pending != null && pending > now) {
            return;
        }
        final long holdSeconds = Math.max(2L, configManager.getLong("campfire-story.hold-seconds", 6L));
        player.getPersistentDataContainer().set(pendingKey, PersistentDataType.LONG, now + holdSeconds * 1000L + 2000L);

        final Location fire = event.getClickedBlock().getLocation().add(0.5D, 0.5D, 0.5D);
        player.sendActionBar(messageManager.getMessage("campfire-story-start",
                "<gray>🔥 Leülsz a tűzhöz… maradj mellette, és hallgasd a mesét.</gray>"));

        // A játékos SAJÁT schedulerén ér véget — ha kilépett/elment, nincs jutalom.
        player.getScheduler().runDelayed(plugin, task -> {
            player.getPersistentDataContainer().remove(pendingKey);
            final double radius = Math.max(1.5D, configManager.getDouble("campfire-story.radius", 3.5D));
            if (!player.getWorld().equals(fire.getWorld())
                    || player.getLocation().distanceSquared(fire) > radius * radius) {
                player.sendActionBar(messageManager.getMessage("campfire-story-left",
                        "<gray>🔥 A mese félbeszakadt — elhagytad a tüzet.</gray>"));
                return;
            }
            // Siker: sztori-sor + kis XP + hangulat; a cooldown CSAK most indul.
            player.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, System.currentTimeMillis());
            final List<String> custom = configManager.getStringList("campfire-story.stories");
            if (custom.isEmpty()) {
                final int index = ThreadLocalRandom.current().nextInt(STORIES.length);
                player.sendMessage(messageManager.getMessage("campfire-story-" + (index + 1), STORIES[index]));
            } else {
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize(custom.get(ThreadLocalRandom.current().nextInt(custom.size()))));
            }
            final int xp = Math.max(0, configManager.getInt("campfire-story.xp-reward", 8));
            if (xp > 0) {
                player.giveExp(xp);
            }
            hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), Particle.SOUL_FIRE_FLAME,
                    fire, 14, 0.4D, 0.5D, 0.4D, 0.01D);
            player.playSound(fire, Sound.AMBIENT_CAVE, 0.3F, 1.4F);
            // Kis közösségi lökés: a tűz körül ülő TÖBBI játékos is látja a mesét (régió-lokális kör).
            for (final org.bukkit.entity.Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof Player listener && org.bukkit.Bukkit.isOwnedByCurrentRegion(listener)) {
                    listener.sendActionBar(messageManager.getMessage("campfire-story-nearby",
                            "<gray>🔥 {player} mesél a tűznél…</gray>",
                            java.util.Map.of("player", player.getName())));
                }
            }
        }, null, holdSeconds * 20L);
    }
}
