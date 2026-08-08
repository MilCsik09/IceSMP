package hu.taliann.icesmp.classrelic;

import java.util.Locale;
import java.util.Objects;

/**
 * Fizikai birtoklás immutable pillanatképe. KIZÁRÓLAG a játékos saját régió-szálán
 * készül (inventory-szkennelés + kanonikus használhatóság-validáció), az UUID-only
 * olvasók pedig csak ezen keresztül látnak birtoklást — Player-dereferencia nélkül.
 * Az elévülés-szabály fail-closed: ismeretlen vagy lejárt pillanatkép = nincs birtoklás,
 * korlátlan ideig tovább élő "true" nem létezhet.
 */
public record PossessionSnapshot(String relicId, boolean present, long scannedAtMillis) {

    public PossessionSnapshot {
        Objects.requireNonNull(relicId, "relicId");
        relicId = relicId.trim().toLowerCase(Locale.ROOT);
    }

    /** A pillanatkép csak a TTL-en belül és csak a saját relic-id-jére hihető. */
    public boolean usableFor(final String queriedRelicId, final long nowMillis,
                             final long ttlMillis) {
        if (queriedRelicId == null
                || !relicId.equals(queriedRelicId.trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (nowMillis - scannedAtMillis >= ttlMillis) {
            return false;
        }
        return present;
    }
}
