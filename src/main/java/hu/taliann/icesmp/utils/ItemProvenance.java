package hu.taliann.icesmp.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.persistence.PersistentDataType;

/**
 * Item-entitás eredetének jelölése: a JÁTÉKOS inventoryából a földre került tárgy nem számít
 * „gyűjtésnek".
 *
 * <p><b>Miért kell:</b> a {@code COLLECT_ITEMS} progressz a felvett stack méretét könyvelte, és a
 * dobás MINDIG új item-entitást hoz létre (új UUID-val), ezért sem entitás-UUID, sem esemény-azonosító
 * szerinti dedup nem fogta meg a ledob–felvesz visszajátszást. Ugyanazzal a fizikai stackkel korlátlanul
 * növelhető volt a személyes gyűjtő-quest ÉS az ismételhető közösségi cél — utóbbi minden körben
 * kifizette a teljes treasury-, liga- és buff-jutalmat. A jelölés a földön lévő ENTITÁSON él, ezért
 * két játékos közti pingpong sem termel progresszt.
 *
 * <p>A jelölés szándékosan csak a PROGRESSZ-könyvelést tiltja: a tárgy felvétele, működése és
 * minden más rendszer változatlan.
 *
 * <p>Folia: az item-entitás PDC-jét a saját régió-szálán írjuk/olvassuk (a drop- és pickup-event is
 * ott fut).
 */
public final class ItemProvenance {

    /** Fix névtér: a jelölésnek a plugin-példánytól függetlenül felismerhetőnek kell lennie. */
    private static final NamespacedKey PLAYER_DROPPED = new NamespacedKey("icesmp", "player_dropped");

    private ItemProvenance() {
    }

    /** Megjelöli a játékos inventoryából a földre került tárgyat (kézi dobás vagy halál-drop). */
    public static void markPlayerDropped(final Item item) {
        if (item != null) {
            item.getPersistentDataContainer().set(PLAYER_DROPPED, PersistentDataType.BYTE, (byte) 1);
        }
    }

    /** Játékos-inventoryból származik-e (tehát a felvétele NEM gyűjtés). */
    public static boolean isPlayerDropped(final Item item) {
        return item != null
                && item.getPersistentDataContainer()
                .getOrDefault(PLAYER_DROPPED, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }
}
