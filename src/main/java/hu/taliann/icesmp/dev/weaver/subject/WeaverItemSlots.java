package hu.taliann.icesmp.dev.weaver.subject;

import hu.taliann.icesmp.dev.weaver.api.WeaverDomainRejection;
import hu.taliann.icesmp.itemization.ItemIdentityService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.OptionalLong;

/** Inspection delegates managed identity to its canonical service; no raw gameplay tags are interpreted. */
public final class WeaverItemSlots {
    private final ItemIdentityService identity;
    public WeaverItemSlots(final ItemIdentityService identity) { this.identity = java.util.Objects.requireNonNull(identity); }
    public ItemStack require(final Player player, final WeaverSlot slot) {
        return peek(player, slot).orElseThrow(() -> new WeaverDomainRejection("ITEM_SLOT_EMPTY"));
    }
    /** Current owner-local slot state, including assessed emptiness; it is not an identity rebind. */
    public Optional<ItemStack> peek(final Player player, final WeaverSlot slot) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) throw new IllegalStateException("Foreign item-slot access");
        final ItemStack item = switch (slot.kind()) {
            case INVENTORY -> player.getInventory().getItem(slot.index());
            case MAIN_HAND -> player.getInventory().getItemInMainHand();
            case OFF_HAND -> player.getInventory().getItemInOffHand();
            case CURSOR -> player.getItemOnCursor();
            case HELMET -> player.getInventory().getHelmet();
            case CHESTPLATE -> player.getInventory().getChestplate();
            case LEGGINGS -> player.getInventory().getLeggings();
            case BOOTS -> player.getInventory().getBoots();
        };
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(item);
    }
    public ItemSlotRef capture(final Player player, final WeaverSlot slot) {
        final ItemStack item = require(player, slot);
        final var inspection = identity.inspect(item);
        if (inspection.status() != ItemIdentityService.Status.VALID && inspection.status() != ItemIdentityService.Status.NOT_MANAGED) {
            throw new WeaverDomainRejection("ITEM_IDENTITY_INVALID");
        }
        final var instance = inspection.instance();
        return new ItemSlotRef(player.getUniqueId(), slot, instance == null ? Optional.empty() : Optional.of(instance.templateId()),
                instance == null ? Optional.empty() : Optional.of(instance.itemId()),
                instance == null ? OptionalLong.empty() : OptionalLong.of(instance.mutationRevision()), fingerprint(item.serializeAsBytes()));
    }
    public void verify(final Player player, final ItemSlotRef expected) {
        if (!capture(player, expected.slot()).equals(expected)) throw new WeaverDomainRejection("STALE_SUBJECT");
    }
    public static String fingerprint(final byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (final NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
