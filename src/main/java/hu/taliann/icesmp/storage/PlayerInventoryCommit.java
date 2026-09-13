package hu.taliann.icesmp.storage;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** Physical inventory acknowledgement, never a player progression authority. */
public final class PlayerInventoryCommit {
    private static final NamespacedKey KEY = new NamespacedKey("icesmp", "inventory_commit");
    private static final int MAX_NBT = 16 * 1024 * 1024;
    private PlayerInventoryCommit() { }

    public static String receipt(Player player, NamespacedKey key) {
        requireReceiptKey(key);
        return player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static void receipt(Player player, NamespacedKey key, String value) {
        requireReceiptKey(key);
        UUID.fromString(value);
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
    }

    private static void requireReceiptKey(NamespacedKey key) {
        if (!KEY.equals(key) && !new NamespacedKey("icesmp", "trash_vendor_removed").equals(key))
            throw new IllegalArgumentException("Not a physical inventory receipt key");
    }

    public static void require(Player player) {
        if (!player.isOnline() || !Bukkit.isOwnedByCurrentRegion(player))
            throw new IllegalStateException("Inventory commit requires its online owner");
        final String token = UUID.randomUUID().toString();
        receipt(player, KEY, token);
        try {
            // Resolve the exact storage used by CraftPlayer.saveData, including custom world roots.
            final Object playerList = player.getServer().getClass().getMethod("getHandle").invoke(player.getServer());
            final Object storage = playerList.getClass().getField("playerIo").get(playerList);
            final File directory = (File) storage.getClass().getMethod("getPlayerDir").invoke(storage);
            player.saveData();
            verifyAndForce(directory.toPath().resolve(player.getUniqueId() + ".dat"), KEY.toString(), token);
        } catch (ReflectiveOperationException | IOException failure) {
            throw new IllegalStateException("Physical inventory save was not acknowledged", failure);
        }
    }

    static void verifyAndForce(Path path, String key, String expected) throws IOException {
        try (FileChannel file = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
                GZIPInputStream gzip = new GZIPInputStream(Channels.newInputStream(file))) {
            final byte[] nbt = gzip.readNBytes(MAX_NBT + 1);
            if (nbt.length > MAX_NBT) throw new IOException("Player NBT exceeds verification budget");
            final DataInputStream input = new DataInputStream(new ByteArrayInputStream(nbt));
            if (input.readUnsignedByte() != 10) throw new IOException("Invalid player NBT root");
            input.readUTF();
            final String found = compound(input, key, 0, false);
            if (!expected.equals(found) || input.available() != 0) throw new IOException("Player save receipt mismatch");
            file.force(true);
        }
        // The replaced .dat name must survive a crash as well as the file contents.
        try (FileChannel directory = FileChannel.open(path.getParent(), StandardOpenOption.READ)) {
            directory.force(true);
        }
    }

    private static String compound(DataInputStream input, String key, int depth, boolean bukkitValues) throws IOException {
        if (depth > 64) throw new IOException("NBT nesting exceeds verification budget");
        String result = null;
        for (int type; (type = input.readUnsignedByte()) != 0;) {
            final String name = input.readUTF();
            if (depth == 0 && type == 10 && name.equals("BukkitValues")) {
                result = compound(input, key, depth + 1, true);
            } else if (bukkitValues && type == 8 && name.equals(key)) {
                if (result != null) throw new IOException("Duplicate inventory receipt");
                result = input.readUTF();
            } else skip(input, type, depth + 1);
        }
        return result;
    }

    private static void skip(DataInputStream input, int type, int depth) throws IOException {
        if (depth > 64) throw new IOException("NBT nesting exceeds verification budget");
        switch (type) {
            case 1 -> input.readByte();
            case 2 -> input.readShort();
            case 3, 5 -> input.readInt();
            case 4, 6 -> input.readLong();
            case 7, 11, 12 -> {
                final int size = input.readInt();
                final int width = type == 7 ? 1 : type == 11 ? 4 : 8;
                if (size < 0 || size > MAX_NBT / width) throw new IOException("Invalid NBT array");
                input.skipNBytes((long) size * width);
            }
            case 8 -> input.readUTF();
            case 9 -> {
                final int child = input.readUnsignedByte();
                final int size = input.readInt();
                if (size < 0 || size > MAX_NBT || child == 0 && size != 0) throw new IOException("Invalid NBT list");
                for (int i = 0; i < size; i++) skip(input, child, depth + 1);
            }
            case 10 -> compound(input, "", depth, false);
            default -> throw new IOException("Invalid NBT tag");
        }
    }
}
