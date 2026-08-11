package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.TalentManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /emlek — K8 Emlékszilánk-beváltás ("visszaemlékezés"). A Felsők amnéziával
 * érkeztek; az Opálos Emlékszilánkokból (mob/boss-loot) emlék-töredékeket lehet
 * kirakni: kaszt-XP, extra talentpont, a spec-választás szint-kapujának korai
 * feloldása, vagy egy kódex-töredék feltárása. Minden költség configból jön
 * (memory-shards.*), élőben olvasva.
 */
public final class MemoryCommand implements BasicCommand {

    private static final String SHARD_ID = "emlekszilank";

    private static final List<String> MEMORY_FRAGMENTS = List.of(
            "&7…egy kéz, amely a tiéd volt, mégis idegen — pecsétgyűrűt visel, és a gyűrűn a Fa jele…",
            "&7…hó hull egy égő városra. Valaki a nevedet kiáltja, de a hang elvész a viharban…",
            "&7…a Bokic partján ülsz. A víz édes, az alkony arany — és valaki melletted nevet…",
            "&7…egy eskü visszhangja: „amíg a Fa áll” — nem emlékszel, kinek tetted…",
            "&7…lépcsők a mélybe, kőbe vájt arcok — a Mélység Népe hajol meg előtted…",
            "&7…a Kapu árnyéka a falra vetül, és az árnyék nem a tiéd…");

    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final TalentManager talentManager;
    private final SpecializationManager specializationManager;
    private final UniqueMaterialFactory uniqueMaterials;
    private final MessageManager messageManager;

    public MemoryCommand(final ConfigManager configManager, final JobManager jobManager,
                         final TalentManager talentManager, final SpecializationManager specializationManager,
                         final UniqueMaterialFactory uniqueMaterials, final MessageManager messageManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.talentManager = talentManager;
        this.specializationManager = specializationManager;
        this.uniqueMaterials = uniqueMaterials;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (!(commandSourceStack.getSender() instanceof Player player)) {
            commandSourceStack.getSender().sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        final String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "xp" -> redeemXp(player);
            case "talent" -> redeemTalent(player);
            case "spec" -> redeemSpec(player);
            case "lore" -> redeemLore(player);
            default -> status(player);
        }
    }

    private void status(final Player player) {
        final int shards = countShards(player);
        player.sendMessage(messageManager.get("memory-status-header",
                "&d✦ Visszaemlékezés &7— Opálos Emlékszilánkjaid: &f%s", shards));
        player.sendMessage(messageManager.get("memory-status-xp",
                "&e/emlek xp &7— &f%s&7 szilánk → &f%s&7 kaszt-XP", costOf("xp", 3), xpAmount()));
        player.sendMessage(messageManager.get("memory-status-talent",
                "&e/emlek talent &7— &f%s&7 szilánk → &f+1&7 kaszt-talentpont", costOf("talent", 5)));
        player.sendMessage(messageManager.get("memory-status-spec",
                "&e/emlek spec &7— &f%s&7 szilánk → a spec-választás szint-kapuja feloldva", costOf("spec", 8)));
        player.sendMessage(messageManager.get("memory-status-lore",
                "&e/emlek lore &7— &f%s&7 szilánk → egy emlék-töredék feltárása", costOf("lore", 1)));
    }

    private void redeemXp(final Player player) {
        if (!jobManager.hasPrimaryJob(player)) {
            player.sendMessage(messageManager.get("memory-no-class",
                    "&cElőbb kaszt kell, hogy legyen mibe visszaemlékezned (&f/menu&c → Kaszt)."));
            return;
        }
        final int cost = costOf("xp", 3);
        if (!consumeShards(player, cost)) {
            return;
        }
        final int amount = xpAmount();
        final String operationId = "memory-xp:" + player.getUniqueId() + ":" + java.util.UUID.randomUUID();
        jobManager.addXpToJobResultV2(player, amount, operationId).whenComplete((result, failure) ->
                player.getScheduler().run(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(MemoryCommand.class), task -> {
                    if (failure != null) {
                        // An exceptional completion is an uncertain boundary. Never mint a shard refund
                        // without proof that the Profile mutation did not durably commit.
                        player.sendMessage(messageManager.get("memory-xp-uncertain",
                                "&4Az XP-jóváírás állapota bizonytalan; automatikus szilánk-visszatérítés nem történt a duplázás elkerülésére. Jelentkezz vissza, a profil újraépül."));
                        return;
                    }
                    if (result == null || !result.durableOutcomeAccepted()) {
                        refundShards(player, cost);
                        player.sendMessage(messageManager.get("memory-xp-failed",
                                "&cAz XP-jóváírás nem commitolt; az Emlékszilánkokat visszakaptad."));
                        return;
                    }
                    if (result.status() == ProfileMutationResult.Status.STALE_SESSION) {
                        player.sendMessage(messageManager.get("memory-xp-stale",
                                "&eAz XP tartósan jóváíródott. A régi session runtime callbackje már nem futhatott le; visszajelentkezéskor a kasztállapot újraépül."));
                        return;
                    }
                    if (result.status() == ProfileMutationResult.Status.RUNTIME_EFFECT_FAILED) {
                        player.sendMessage(messageManager.get("memory-xp-runtime-failed",
                                "&4Az XP tartósan jóváíródott, de a runtime helyreállítás szükséges; szilánk-visszatérítés nem történt."));
                        return;
                    }
                    remembered(player, messageManager.get("memory-redeemed-xp",
                            "&d✦ Emlékek törnek fel — &f+%s kaszt-XP&d. A kéz emlékszik, amit az elme elfelejtett.", amount));
                }, null));
    }

    private void redeemTalent(final Player player) {
        final int cost = costOf("talent", 5);
        if (!consumeShards(player, cost)) {
            return;
        }
        talentManager.grantBonusPoints(player, true, 1).whenComplete((bonus, failure) ->
                talentManager.runOnOwnerThread(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        refundShards(player, cost);
                        player.sendMessage(messageManager.get("memory-talent-failed",
                                "&cA talentpont jóváírása meghiúsult; az Emlékszilánkokat visszakaptad."));
                        return;
                    }
                    remembered(player, messageManager.get("memory-redeemed-talent",
                            "&d✦ Egy régi lecke tér vissza — &f+1 kaszt-talentpont&d (&7/talent&d)."));
                }));
    }

    private void redeemSpec(final Player player) {
        if (specializationManager.hasMemorySpecUnlock(player)) {
            player.sendMessage(messageManager.get("memory-spec-already",
                    "&7Erre már visszaemlékeztél — a spec-választás kapuja nyitva áll (&f/spec&7)."));
            return;
        }
        final int cost = costOf("spec", 8);
        if (!consumeShards(player, cost)) {
            return;
        }
        specializationManager.grantMemorySpecUnlock(player);
        remembered(player, messageManager.get("memory-redeemed-spec",
                "&d✦ Visszaemlékeztél, ki voltál — a &fspecializáció-választás&d szint-kapuja feloldva (&7/spec&d)."));
    }

    private void redeemLore(final Player player) {
        final int cost = costOf("lore", 1);
        if (!consumeShards(player, cost)) {
            return;
        }
        final String fragment = MEMORY_FRAGMENTS.get(ThreadLocalRandom.current().nextInt(MEMORY_FRAGMENTS.size()));
        remembered(player, messageManager.get("memory-redeemed-lore", "&d✦ Egy töredék a múltadból:"));
        player.sendMessage(messageManager.get("memory-fragment", fragment));
    }

    private void refundShards(final Player player, final int amount) {
        final ItemStack refund = uniqueMaterials.create(SHARD_ID, amount);
        if (refund == null) {
            java.util.logging.Logger.getLogger("IceSMP").severe("Cannot refund memory shards: material is undefined");
            return;
        }
        final var leftovers = player.getInventory().addItem(refund);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private void remembered(final Player player, final String message) {
        player.sendMessage(message);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F);
        hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), org.bukkit.Particle.END_ROD,
                player.getLocation().add(0.0D, 1.2D, 0.0D), 20, 0.4D, 0.6D, 0.4D, 0.02D);
    }

    private int costOf(final String key, final int fallback) {
        return Math.max(1, configManager.getInt("memory-shards.costs." + key, fallback));
    }

    private int xpAmount() {
        return Math.max(1, configManager.getInt("memory-shards.xp-amount", 500));
    }

    private int countShards(final Player player) {
        int count = 0;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item != null && SHARD_ID.equals(uniqueMaterials.idOf(item))) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private boolean consumeShards(final Player player, final int cost) {
        if (countShards(player) < cost) {
            player.sendMessage(messageManager.get("memory-not-enough",
                    "&cEhhez az emlékhez &f%s&c Opálos Emlékszilánk kell (nálad: &f%s&c).", cost, countShards(player)));
            return false;
        }
        int remaining = cost;
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack item = contents[slot];
            if (item == null || !SHARD_ID.equals(uniqueMaterials.idOf(item))) {
                continue;
            }
            final int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
        }
        return true;
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            return List.of("xp", "talent", "spec", "lore");
        }
        return List.of();
    }
}
