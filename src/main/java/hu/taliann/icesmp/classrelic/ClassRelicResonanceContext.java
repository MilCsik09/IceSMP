package hu.taliann.icesmp.classrelic;

import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * A Resonance-hook futásidejű kontextusa (Paper/Folia adapter-réteg a pure signal fölött).
 * A dispatch garantálja, hogy a hook az ACTOR saját régió-szálán fut és a {@code actor}
 * referencia ezen a szálon érvényes — a hook-implementációknak SOHA nem kell (és nem
 * szabad) globális {@code Bukkit.getPlayer} lookupot végezniük. Cél-oldali effekt idegen
 * entityn csak a cél schedulerére hoppolva érintheti a célt: a signal ezért CSAK
 * identitást (UUID) hordoz a célról, élő referenciát nem.
 */
public record ClassRelicResonanceContext(
        Player actor,
        ClassRelicActivation activation,
        ClassGameplaySignal signal) {

    public ClassRelicResonanceContext {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(signal, "signal");
        if (!actor.getUniqueId().equals(signal.actorId())) {
            throw new IllegalArgumentException("signal actor mismatch: " + signal.actorId());
        }
        if (!actor.getUniqueId().equals(activation.playerId())) {
            throw new IllegalArgumentException("activation player mismatch: "
                    + activation.playerId());
        }
    }
}
