package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.items.ItemDataFactory;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Restriction metadata is regenerated from canonical identity; it never grants developer authority. */
public final class ItemPrototypePolicy {
    public enum Scan { CLEAN, PROTOTYPE, UNAVAILABLE }
    public record Identity(UUID owner, UUID operation) {
        public Identity { Objects.requireNonNull(owner); Objects.requireNonNull(operation); }
    }
    private static final NamespacedKey MARKER = new NamespacedKey("icesmp", "dev_prototype");
    private static final NamespacedKey OWNER = new NamespacedKey("icesmp", "dev_prototype_owner");
    private static final NamespacedKey OPERATION = new NamespacedKey("icesmp", "dev_prototype_operation");
    private static final NamespacedKey PAYLOAD = new NamespacedKey("icesmp", "item_instance");
    private static final NamespacedKey ARTIFACT = new NamespacedKey("icesmp", "dev_item_id");
    private ItemPrototypePolicy() { }

    public static boolean isPrototype(ItemInstance instance) {
        return instance != null && (instance.states().contains(ItemState.DEV_PROTOTYPE)
                || instance.origin().sourceTag().equals("dev:prototype"));
    }
    public static Identity identity(ItemInstance instance) {
        if (!isPrototype(instance) || !instance.states().contains(ItemState.DEV_PROTOTYPE)
                || !instance.origin().sourceTag().equals("dev:prototype")) throw new IllegalArgumentException("Incomplete prototype identity");
        return new Identity(instance.origin().crafterId(), UUID.fromString(instance.origin().sourceId()));
    }
    public static Optional<Identity> identity(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        final var pdc = item.getItemMeta().getPersistentDataContainer();
        try {
            if (!Byte.valueOf((byte) 1).equals(pdc.get(MARKER, PersistentDataType.BYTE))) return Optional.empty();
            final var value = new Identity(UUID.fromString(pdc.get(OWNER, PersistentDataType.STRING)),
                    UUID.fromString(pdc.get(OPERATION, PersistentDataType.STRING)));
            if (pdc.has(PAYLOAD)) {
                final String payload = pdc.get(PAYLOAD, PersistentDataType.STRING);
                if (payload == null || payload.length() > 87_384) return Optional.empty();
                final var instance = ItemInstanceCodec.decode(payload);
                if (!value.equals(identity(instance))) return Optional.empty();
            }
            return Optional.of(value);
        } catch (RuntimeException malformed) { return Optional.empty(); }
    }
    public static boolean direct(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        final var pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(MARKER) || pdc.has(OWNER) || pdc.has(OPERATION)) return true;
        final String payload = pdc.get(PAYLOAD, PersistentDataType.STRING);
        if (payload == null || payload.length() > 87_384) return false;
        try { return isPrototype(ItemInstanceCodec.decode(payload)); }
        catch (RuntimeException malformed) { return false; }
    }
    public static Scan scan(ItemStack item) { return scan(item, 0, new int[] { 4096 }); }
    private static Scan scan(ItemStack item, int depth, int[] remaining) {
        if (item == null || item.getType().isAir()) return Scan.CLEAN;
        if (remaining[0]-- <= 0 || depth > 16) return Scan.UNAVAILABLE;
        if (direct(item)) return Scan.PROTOTYPE;
        if (!item.hasItemMeta()) return Scan.CLEAN;
        final var meta = item.getItemMeta();
        final Iterable<ItemStack> nested;
        if (meta instanceof BundleMeta bundle) nested = bundle.getItems();
        else if (meta instanceof org.bukkit.inventory.meta.CrossbowMeta crossbow) nested = crossbow.getChargedProjectiles();
        else if (meta instanceof BlockStateMeta block && block.hasBlockState()
                && block.getBlockState() instanceof Container container) nested = Arrays.asList(container.getInventory().getContents());
        else return Scan.CLEAN;
        boolean unknown = false;
        for (final var value : nested) {
            final var found = scan(value, depth + 1, remaining);
            if (found == Scan.PROTOTYPE) return found;
            if (found == Scan.UNAVAILABLE) unknown = true;
            if (remaining[0] <= 0) return Scan.UNAVAILABLE;
        }
        return unknown ? Scan.UNAVAILABLE : Scan.CLEAN;
    }
    public static boolean allowedCustody(ItemStack item, UUID holder, UUID primaryDeveloper) {
        return item != null && item.getAmount() == 1 && direct(item) && hu.taliann.icesmp.security.HiddenDevAuthority.isDeveloper(holder)
                && holder.equals(primaryDeveloper)
                && identity(item).map(value -> value.owner().equals(holder)).orElse(false);
    }
    public static void quarantinePlayer(org.bukkit.entity.Player player, UUID primaryDeveloper) {
        if (player == null || !org.bukkit.Bukkit.isOwnedByCurrentRegion(player)) throw new IllegalStateException("Prototype custody owner required");
        final var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final var item = inventory.getItem(slot);
            if (scan(item) == Scan.PROTOTYPE && !allowedCustody(item, player.getUniqueId(), primaryDeveloper)) inventory.setItem(slot, null);
        }
        final var cursor = player.getItemOnCursor();
        if (scan(cursor) == Scan.PROTOTYPE && !allowedCustody(cursor, player.getUniqueId(), primaryDeveloper)) player.setItemOnCursor(null);
        final var ender = player.getEnderChest();
        for (int slot = 0; slot < ender.getSize(); slot++) if (scan(ender.getItem(slot)) == Scan.PROTOTYPE) ender.setItem(slot, null);
    }
    /** Only detached copies or owner-local native render paths may stamp a prototype restriction. */
    public static void mark(ItemStack item, Identity identity) {
        Objects.requireNonNull(item); Objects.requireNonNull(identity);
        if (item.getAmount() != 1 || item.getType().isAir()) throw new IllegalArgumentException("Prototype singleton required");
        final var meta = item.getItemMeta(); final var pdc = meta.getPersistentDataContainer();
        if (pdc.has(ARTIFACT)) throw new IllegalArgumentException("Developer artifact is not a prototype");
        if ((pdc.has(MARKER) || pdc.has(OWNER) || pdc.has(OPERATION))
                && !identity(item).filter(identity::equals).isPresent()) throw new IllegalArgumentException("Prototype custody identity cannot change");
        if (pdc.has(PAYLOAD) && !identity.equals(identity(ItemInstanceCodec.decode(pdc.get(PAYLOAD, PersistentDataType.STRING))))) {
            throw new IllegalArgumentException("Prototype marker differs from canonical identity");
        }
        pdc.set(MARKER, PersistentDataType.BYTE, (byte) 1);
        pdc.set(OWNER, PersistentDataType.STRING, identity.owner().toString());
        pdc.set(OPERATION, PersistentDataType.STRING, identity.operation().toString());
        for (final var enchant : Set.copyOf(meta.getEnchants().keySet())) meta.removeEnchant(enchant);
        item.setItemMeta(meta);
        ItemDataFactory.applyCanonicalAttributeModifiers(item, List.of(), false);
    }
}
