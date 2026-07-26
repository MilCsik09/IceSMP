package hu.taliann.icesmp.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevItemStateCodecTest {

    private static final UUID OWNER = UUID.fromString("eb80c20f-092a-4d76-bd44-d168c91ea9e2");
    private static final UUID INSTANCE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    @Test
    void validIssuedStateRoundTripsWithoutChangingPendingReward() {
        final ItemStack reward = new ItemStack(Material.DIAMOND, 3);
        final DevItemStateCodec.Snapshot source = new DevItemStateCodec.Snapshot(
                OWNER, true, INSTANCE, 600_000L,
                "epikus", "DIAMOND:3", reward,
                7, 8, 9);

        final DevItemStateCodec.Snapshot decoded = DevItemStateCodec.decode(
                DevItemStateCodec.encode(source));

        assertEquals(OWNER, decoded.owner());
        assertEquals(INSTANCE, decoded.instanceId());
        assertEquals("epikus", decoded.pendingRarity());
        assertEquals("DIAMOND:3", decoded.pendingEntry());
        assertEquals(Material.DIAMOND, decoded.pendingItem().getType());
        assertEquals(3, decoded.pendingItem().getAmount());
        assertEquals(7, decoded.sinceRare());
        assertEquals(8, decoded.sinceEpic());
        assertEquals(9, decoded.sinceLegendary());
    }

    @Test
    void legacySelectedRewardWithoutExactItemRemainsRecoverable() {
        final YamlConfiguration yaml = baseIssuedState();
        yaml.set("bingulus.pending.rarity", "ritka");
        yaml.set("bingulus.pending.entry", "EMERALD:2");

        final DevItemStateCodec.Snapshot decoded = DevItemStateCodec.decode(yaml);

        assertEquals("ritka", decoded.pendingRarity());
        assertEquals("EMERALD:2", decoded.pendingEntry());
        assertNull(decoded.pendingItem());
    }

    @Test
    void malformedInstanceUuidIsRejectedInsteadOfRotatingSingletonIdentity() {
        final YamlConfiguration yaml = baseIssuedState();
        yaml.set("bingulus.instance", "not-a-uuid");

        assertThrows(IllegalArgumentException.class, () -> DevItemStateCodec.decode(yaml));
    }

    @Test
    void orphanPendingItemIsRejectedInsteadOfBeingSilentlyRebound() {
        final YamlConfiguration yaml = baseIssuedState();
        yaml.set("bingulus.pending.item", new ItemStack(Material.NETHER_STAR));

        assertThrows(IllegalArgumentException.class, () -> DevItemStateCodec.decode(yaml));
    }

    @Test
    void unissuedStateCannotCarryProgressPityOrReward() {
        final YamlConfiguration yaml = baseIssuedState();
        yaml.set("bingulus.issued", false);
        yaml.set("bingulus.progress-millis", 1L);

        assertThrows(IllegalArgumentException.class, () -> DevItemStateCodec.decode(yaml));
    }

    @Test
    void wrongScalarTypesAreRejectedInsteadOfUsingBukkitDefaults() {
        final YamlConfiguration yaml = baseIssuedState();
        yaml.set("bingulus.pity.since-rare", "12");

        assertThrows(IllegalArgumentException.class, () -> DevItemStateCodec.decode(yaml));
    }

    private static YamlConfiguration baseIssuedState() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("bingulus.schema-version", DevItemStateCodec.SCHEMA_VERSION);
        yaml.set("bingulus.owner", OWNER.toString());
        yaml.set("bingulus.instance", INSTANCE.toString());
        yaml.set("bingulus.issued", true);
        yaml.set("bingulus.progress-millis", 0L);
        return yaml;
    }
}
