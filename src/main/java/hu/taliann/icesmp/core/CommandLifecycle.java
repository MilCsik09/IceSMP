package hu.taliann.icesmp.core;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Keeps retained Paper command nodes out of a starting or retired plugin instance. */
public final class CommandLifecycle {
    private static final String UNAVAILABLE = "§cAz IceSMP jelenleg nem érhető el; a plugin indul vagy leállt.";
    private static final List<String> NO_SUGGESTIONS = List.of();

    private final BooleanSupplier pluginEnabled;
    private final List<GuardedCommand> commands = new ArrayList<>();
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
    private boolean opened;
    private boolean closed;
    private int entered;

    public CommandLifecycle(final BooleanSupplier pluginEnabled) {
        this.pluginEnabled = Objects.requireNonNull(pluginEnabled, "pluginEnabled");
    }

    public synchronized BasicCommand wrap(final BasicCommand command) {
        if (closed) throw new IllegalStateException("command lifecycle is closed");
        final GuardedCommand guarded = new GuardedCommand(this, Objects.requireNonNull(command));
        commands.add(guarded);
        return guarded;
    }

    public synchronized void open() {
        if (closed || opened || !pluginEnabled.getAsBoolean()) {
            throw new IllegalStateException("command lifecycle cannot open");
        }
        opened = true;
    }

    public CompletableFuture<Void> close() {
        final boolean complete;
        synchronized (this) {
            closed = true;
            for (final GuardedCommand command : commands) command.delegate = null;
            commands.clear();
            complete = entered == 0;
        }
        if (complete) drained.complete(null);
        return drained.copy();
    }

    private synchronized BasicCommand enter(final GuardedCommand command) {
        if (!opened || closed || !pluginEnabled.getAsBoolean()) return null;
        final BasicCommand delegate = command.delegate;
        if (delegate == null) return null;
        entered++;
        return delegate;
    }

    private void leave() {
        final boolean complete;
        synchronized (this) {
            entered--;
            complete = closed && entered == 0;
        }
        if (complete) drained.complete(null);
    }

    private static final class GuardedCommand implements BasicCommand {
        private final CommandLifecycle lifecycle;
        private final String permission;
        private BasicCommand delegate;

        private GuardedCommand(final CommandLifecycle lifecycle, final BasicCommand delegate) {
            this.lifecycle = lifecycle;
            this.delegate = delegate;
            this.permission = delegate.permission();
        }

        @Override
        public void execute(final CommandSourceStack source, final String[] args) {
            final BasicCommand current = lifecycle.enter(this);
            if (current == null) {
                source.getSender().sendMessage(UNAVAILABLE);
                return;
            }
            try {
                current.execute(source, args);
            } finally {
                lifecycle.leave();
            }
        }

        @Override
        public Collection<String> suggest(final CommandSourceStack source, final String[] args) {
            final BasicCommand current = lifecycle.enter(this);
            if (current == null) return NO_SUGGESTIONS;
            try {
                return current.suggest(source, args);
            } finally {
                lifecycle.leave();
            }
        }

        @Override
        public boolean canUse(final CommandSender sender) {
            final BasicCommand current = lifecycle.enter(this);
            if (current == null) return false;
            try {
                return current.canUse(sender);
            } finally {
                lifecycle.leave();
            }
        }

        @Override
        public String permission() {
            return permission;
        }
    }
}
