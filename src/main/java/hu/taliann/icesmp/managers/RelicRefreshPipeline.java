package hu.taliann.icesmp.managers;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Per-entry isolation for inventory refreshes; programming failures remain visible to the caller. */
final class RelicRefreshPipeline {

    private RelicRefreshPipeline() {
    }

    static <T, D> int refresh(final T[] entries,
                              final Function<T, D> identifier,
                              final BiConsumer<T, D> refresher,
                              final FailureHandler<T, D> failureHandler) {
        if (entries == null) {
            return 0;
        }

        int changed = 0;
        for (int index = 0; index < entries.length; index++) {
            final T entry = entries[index];
            D definition = null;
            try {
                definition = identifier.apply(entry);
                if (definition == null) {
                    continue;
                }
                refresher.accept(entry, definition);
                changed++;
            } catch (final RuntimeException exception) {
                failureHandler.accept(index, entry, definition, exception);
            }
        }
        return changed;
    }

    @FunctionalInterface
    interface FailureHandler<T, D> {
        void accept(int index, T entry, D definition, RuntimeException exception);
    }
}
