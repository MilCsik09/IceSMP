package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.DialogService;
import hu.taliann.icesmp.managers.QuestManager;
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

    /**
     * Original P4d welcome copy shipped before the current /menu -> /profile onboarding flow.
     * Existing servers keep copied config files across plugin updates, so recognizing this exact
     * bundled text lets us upgrade the stale stock dialog without overwriting genuine custom copy.
     */
    private static final List<String> LEGACY_WELCOME_DIALOG_LINES = List.of(
            "<gray>A <white>Fa árnyékában</white> írod a legendád. Első lépések:</gray>",
            "<yellow>•</yellow> <white>/kaszt</white> — válassz hivatást (13 kaszt).",
            "<yellow>•</yellow> <white>/faction</white> — a Menedékben kezdesz — állj a Láng vagy a Fagy zászlaja alá.",
            "<yellow>•</yellow> <white>/menu</white> — minden rendszer egy helyen.",
            "<gray>A haladásodat a <white>Haladás</white> képernyőn (L) is követheted.</gray>");

    /** Current first-join copy, aligned with the player guide and greenfield Profile v2. */
    private static final List<String> CURRENT_WELCOME_DIALOG_LINES = List.of(
            "<gray>Aetrinita visszahívott. A <white>Menedék vendégeként</white> kezded az utad.</gray>",
            "<yellow>•</yellow> <white>/menu</white> — innen eléred a fő rendszereket.",
            "<yellow>•</yellow> <white>/profile</white> — itt választasz kasztot, szakmát, később specializációt és talenteket.",
            "<red>!</red> <gray>A kasztválasztás tartós döntés; teljes kaszt-resethez adminisztrátor kell.</gray>",
            "<yellow>•</yellow> <white>Kövesd a kezdő küldetést</white> — játék közben végigvezet az alapokon.",
            "<gray>A frakció külön, tudatos döntés. Addig Menedék-vendég vagy, nem automatikus neutral tag.</gray>");

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

        // QuestManager#accept() already refuses a quest that is active or (non-repeatable)
        // completed, but the isActive/hasCompleted pre-check avoids even attempting it once
        // the chain has moved on — a re-join must never re-announce step one.
        if (questManager.isActive(player, firstQuest) || questManager.hasCompleted(player, firstQuest)
                || !questManager.accept(player, firstQuest)) {
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
     * Native first-join welcome dialog. It appears after the intro title sequence on the player's
     * own Folia scheduler. The old bundled copy is migrated in memory so already-deployed
     * quests.yml files do not need to be deleted just to receive corrected onboarding text.
     */
    private void showWelcomeDialog(final Player player) {
        if (!configManager.getBoolean("onboarding.welcome-dialog", true)) {
            return;
        }
        final long delay = Math.max(1L, configManager.getLong("onboarding.welcome-dialog-delay-ticks", 80L));
        final MiniMessage mm = MiniMessage.miniMessage();
        final Component title = mm.deserialize(configManager.getString(
                "onboarding.welcome-dialog-title", "<gradient:#8ab4ff:#c9a3ff>Üdv az IceSMP-n!</gradient>"));
        final List<String> configured = configManager.getStringList("onboarding.welcome-dialog-lines");
        final List<String> lines = configured.isEmpty() || isLegacyStockDialog(configured)
                ? CURRENT_WELCOME_DIALOG_LINES : configured;
        final List<Component> body = lines.stream().map(mm::deserialize).toList();
        player.getScheduler().runDelayed(plugin, task -> DialogService.showNotice(player, title, body), null, delay);
    }

    private static boolean isLegacyStockDialog(final List<String> configured) {
        if (configured.equals(LEGACY_WELCOME_DIALOG_LINES)) {
            return true;
        }
        // Tolerate trivial edits/formatting changes made to the old stock copy while avoiding
        // replacement of unrelated custom dialogs.
        final boolean pointsAtRemovedKasztShortcut = configured.stream()
                .anyMatch(line -> line.contains("<white>/kaszt</white>"));
        final boolean limitsFactionChoiceToTwoFlags = configured.stream()
                .anyMatch(line -> line.contains("Láng vagy a Fagy"));
        return pointsAtRemovedKasztShortcut && limitsFactionChoiceToTwoFlags;
    }
}
