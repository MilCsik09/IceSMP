package hu.taliann.icesmp.storage;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public final class PlayerInventoryCommitRegressionSuite {
    public interface HandleServer { Object getHandle(); }
    public static final class PlayerList { public final Storage playerIo; PlayerList(Path path) { playerIo = new Storage(path); } }
    public static final class Storage { private final Path path; Storage(Path path) { this.path = path; } public File getPlayerDir() { return path.toFile(); } }
    private static int assertions;
    public static void main(String[] args) throws Exception {
        final Path dir = Files.createTempDirectory("physical-inventory-commit");
        final UUID id = UUID.randomUUID();
        final Path file = dir.resolve(id + ".dat");
        final Map<NamespacedKey, Object> pdc = new HashMap<>();
        final boolean[] writes = {true}, owned = {true};
        final PersistentDataContainer data = (PersistentDataContainer) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{PersistentDataContainer.class}, (p,m,a) -> switch(m.getName()) {
            case "get" -> pdc.get(a[0]); case "set" -> { pdc.put((NamespacedKey) a[0], a[2]); yield null; } default -> null;
        });
        final PlayerList list = new PlayerList(dir);
        final Server server = (Server) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Server.class, HandleServer.class}, (p,m,a) -> switch(m.getName()) {
            case "getHandle" -> list; case "isOwnedByCurrentRegion" -> owned[0]; default -> null;
        });
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (p,m,a) -> switch(m.getName()) {
            case "getServer" -> server; case "getUniqueId" -> id; case "isOnline" -> true; case "getPersistentDataContainer" -> data;
            case "saveData" -> { if (writes[0]) write(file, pdc.get(new NamespacedKey("icesmp", "inventory_commit")).toString(), true); yield null; }
            default -> null;
        });
        final Field field = Bukkit.class.getDeclaredField("server"); field.setAccessible(true);
        final Object previous = field.get(null); field.set(null, server);
        try {
            PlayerInventoryCommit.require(player); assertions++;
            final byte[] saved = Files.readAllBytes(file);
            writes[0] = false;
            refuses(() -> PlayerInventoryCommit.require(player));
            check(Arrays.equals(saved, Files.readAllBytes(file)), "failed save changed previous durable snapshot");
            Files.delete(file);
            refuses(() -> PlayerInventoryCommit.require(player));
            writes[0] = true;
            PlayerInventoryCommit.require(player); assertions++;
            owned[0] = false;
            refuses(() -> PlayerInventoryCommit.require(player));
            owned[0] = true;
            write(file, "wrong", true);
            refuses(() -> PlayerInventoryCommit.verifyAndForce(file, "icesmp:inventory_commit", "expected"));
            write(file, "expected", false);
            refuses(() -> PlayerInventoryCommit.verifyAndForce(file, "icesmp:inventory_commit", "expected"));
            Files.write(file, new byte[]{1,2,3});
            refuses(() -> PlayerInventoryCommit.verifyAndForce(file, "icesmp:inventory_commit", "expected"));
            write(file, "expected", true);
            PlayerInventoryCommit.verifyAndForce(file, "icesmp:inventory_commit", "expected"); assertions++;
        } finally {
            field.set(null, previous);
            Files.deleteIfExists(file); Files.deleteIfExists(dir);
        }
        System.out.println("Physical inventory commit regression passed. assertions=" + assertions);
    }
    private static void write(Path path, String token, boolean inBukkitValues) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))) {
            out.writeByte(10); out.writeUTF("");
            out.writeByte(9); out.writeUTF("Inventory"); out.writeByte(10); out.writeInt(1);
            out.writeByte(8); out.writeUTF("id"); out.writeUTF("minecraft:stone"); out.writeByte(0);
            out.writeByte(10); out.writeUTF(inBukkitValues ? "BukkitValues" : "unrelated");
            out.writeByte(8); out.writeUTF("icesmp:inventory_commit"); out.writeUTF(token); out.writeByte(0); out.writeByte(0);
        }
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void refuses(Action action) throws Exception {
        try { action.run(); throw new AssertionError("unacknowledged physical commit accepted"); }
        catch (IOException | IllegalStateException expected) { assertions++; }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
}
