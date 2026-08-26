package hu.taliann.icesmp.security;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/** Immutable boundary for hidden-content developer surfaces; permissions and OP never grant it. */
public final class HiddenDevAuthority {

    public static final UUID PRIMARY_DEVELOPER = UUID.fromString("2d47d7b6-294e-4a14-922c-befacd66ee6d");
    private static final Set<UUID> DEVELOPERS = Set.of(PRIMARY_DEVELOPER);

    private HiddenDevAuthority() {
    }

    public static boolean isDeveloper(final UUID playerId) {
        return playerId != null && DEVELOPERS.contains(playerId);
    }

    public static boolean mayUseHiddenContent(final CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) return true;
        return sender instanceof Player player && isDeveloper(player.getUniqueId());
    }
}
