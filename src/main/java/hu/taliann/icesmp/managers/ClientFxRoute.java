package hu.taliann.icesmp.managers;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Domain-oldali seam a kliens FX-esemény csatornához (a ClientHudRoute mintája): a
 * domain-emitter csak ezt az interfészt látja, a kapuzás (session/capability/rádiusz)
 * a route implementáció dolga. Az FX tranziens kiegészítő réteg — a vanilla
 * telegráf-partikula/hang minden kliensnek változatlanul megy, a gameplay-kimenetet
 * az FX-esemény léte vagy hiánya nem befolyásolhatja.
 */
public interface ClientFxRoute {

    /** Pozicionált FX-esemény a hely körüli, capability-s klienseknek. */
    void emitFx(String fxId, Location center, double radius, int durationTicks);

    /** Cél-játékosnak szóló, nem-pozicionált FX-esemény (pl. saját awakening-arming). */
    void emitFxFor(UUID playerId, String fxId);
}
