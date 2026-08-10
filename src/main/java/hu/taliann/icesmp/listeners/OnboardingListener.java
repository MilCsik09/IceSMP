package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.DialogService;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.quest.OnboardingWelcomeCopy;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * Auto-starts the onboarding quest chain ("az új játékos első 10 perce
 * vezetett") on a brand-new player's very first join. Only the first
 * link ({@code onboarding.first-quest} in quests.yml, "Beszélj a hírnökkel"
 * by default) is accepted here; every follow-up link hands itself over via
 * {@link QuestManager}'s {@code next}-field auto-chain the moment the previous
 * one completes (see {@code QuestManager#advanceChain}), so this listener
 * never has to know how many steps the chain has.
 *
 * <p>Fires on {@link PlayerJoinEvent}, which on Folia runs on the joining
 * player's own region thread — every touched state (quest PDC, chat, sound)
 * belongs to that same player, so no scheduler hop is needed.</p>
 */
public final class OnboardingListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final QuestManager questManager;
    private final MessageManager messageManager;

    public OnboardingListener(final JavaPlugin plugin, final ConfigManager configManager,
                              final QuestManager questManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.questManager = questManager;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (player.hasPlayedBefore() || !configManager.getBoolean("onboarding.enabled", true)) {
            return;
        }

        final String firstQuest = configManager.getString("onboarding.first-quest", "onboarding_herald");
        if (firstQuest == null || firstQuest.isBlank()) {
            return;
        }

        // Az accept-út már elutasítja az aktív vagy (nem ismételhető) teljesített questet,
        // de az isActive/hasCompleted elő-ellenőrzés a próbálkozást is elkerüli, ha a lánc
        // már továbbhaladt — az újra-belépés nem jelentheti be újra az első lépést. Az
        // onboarding AUTO-forrás: a lánc-nyitó quest start-policyje ezt engedélyezi.
        if (questManager.isActive(player, firstQuest) || questManager.hasCompleted(player, firstQuest)
                || questManager.acceptWithSource(player, firstQuest,
                        hu.taliann.icesmp.quest.QuestSourceContext.auto()) != null) {
            return;
        }

        final ConfigurationSection questSection = questManager.getQuestSection(firstQuest);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.4F);
        player.sendMessage(messageManager.getMessage(
                "quest.onboarding-welcome",
                "<gold>✦ Üdv az IceSMP-n! Az első küldetésed: <white>{quest}</white> <gray>— {description}</gray></gold>",
                Map.of(
                        "quest", questManager.getDisplayName(firstQuest),
                        "description", questSection == null ? "" : questSection.getString("description", "")
                )
        ));

        showWelcomeDialog(player);
    }

    /**
     * Natív üdvözlő-dialógus az első belépéskor. Rövid késleltetéssel jön, hogy ne takarja az
     * intro-cím-szekvenciát; a JÁTÉKOS saját szálán (a join-thread) marad. Kikapcsolható:
     * {@code onboarding.welcome-dialog=false}. A megjelenítendő szöveget a
     * {@link OnboardingWelcomeCopy} egyetlen szabálya dönti el, hogy egy elavult, de már
     * kimásolt config se ragassza be véglegesen a régi onboarding-szöveget.
     */
    private void showWelcomeDialog(final Player player) {
        if (!configManager.getBoolean("onboarding.welcome-dialog", true)) {
            return;
        }
        final long delay = Math.max(1L, configManager.getLong("onboarding.welcome-dialog-delay-ticks", 80L));
        final MiniMessage mm = MiniMessage.miniMessage();
        final Component title = mm.deserialize(configManager.getString(
                "onboarding.welcome-dialog-title", "<gradient:#8ab4ff:#c9a3ff>Üdv az IceSMP-n!</gradient>"));
        final List<Component> body = OnboardingWelcomeCopy
                .resolve(configManager.getStringList("onboarding.welcome-dialog-lines"))
                .stream().map(mm::deserialize).toList();
        player.getScheduler().runDelayed(plugin, task -> DialogService.showNotice(player, title, body), null, delay);
    }
}
