package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ChronicleManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jspecify.annotations.NonNull;

/**
 * /kronika — az utolsó Heti Krónika visszaolvasása (B15). A kiadást a
 * ChronicleManager időzíti; admin kézi kiadás: /events chronicle.
 */
public final class KronikaCommand implements BasicCommand {

    private final ChronicleManager chronicleManager;
    private final MessageManager messageManager;

    public KronikaCommand(final ChronicleManager chronicleManager, final MessageManager messageManager) {
        this.chronicleManager = chronicleManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final var sender = commandSourceStack.getSender();
        final var issue = chronicleManager.getLastIssue();
        if (issue.isEmpty()) {
            sender.sendMessage(messageManager.get("chronicle-empty",
                    "&7A krónikások még nem adtak ki lapot — türelem, a Számvevők lassan írnak."));
            return;
        }
        final var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
        for (final String line : issue) {
            sender.sendMessage(legacy.deserialize(line));
        }
    }
}
