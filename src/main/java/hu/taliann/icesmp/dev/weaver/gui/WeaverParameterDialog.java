package hu.taliann.icesmp.dev.weaver.gui;

import hu.taliann.icesmp.dev.artifact.DevArtifactContext;
import hu.taliann.icesmp.dev.weaver.api.ActionParameter;
import hu.taliann.icesmp.dev.weaver.api.WeaverDomainRejection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** Native dialog values never enter player chat or public command logs. */
@SuppressWarnings("UnstableApiUsage")
public final class WeaverParameterDialog {
    public void open(final DevArtifactContext context, final ActionParameter parameter, final Consumer<String> answer) {
        final Player player = context.player();
        if (player == null) throw new WeaverDomainRejection("AUTHORITY_REJECTED");
        final var options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(30)).build();
        final ActionButton apply = ActionButton.builder(Component.text("Alkalmaz"))
                .action(DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player sender) || !sender.getUniqueId().equals(context.owner())) return;
                    final String text = response.getText("value");
                    if (text != null && text.length() <= 256) context.onOwner(owner -> answer.accept(text), () -> {});
                }, options)).build();
        final ActionButton cancel = ActionButton.builder(Component.text("Mégse"))
                .action(DialogAction.customClick((response, audience) -> {}, options)).build();
        final var dialog = Dialog.create(factory -> factory.empty().base(DialogBase.builder(parameter.label())
                .canCloseWithEscape(true).inputs(List.of(DialogInput.text("value", parameter.label()).maxLength(256).build())).build())
                .type(DialogType.confirmation(apply, cancel)));
        player.showDialog(dialog);
    }
}
