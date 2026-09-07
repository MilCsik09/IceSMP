package hu.taliann.icesmp.integrity;

import java.util.*;

/** Owner-captured routing identity; contributor discovery never receives a live Bukkit handle. */
public record GameplaySourceSubject(UUID id, Kind kind) {
    public enum Kind { PLAYER, MOB, PROJECTILE, ENTITY }
    public GameplaySourceSubject { Objects.requireNonNull(id); Objects.requireNonNull(kind); }
}
