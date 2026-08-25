package hu.taliann.icesmp.quest;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.items.MoneyPouchItemFactory;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.itemization.ItemIdentityService;
import hu.taliann.icesmp.listeners.ProfessionRecipeBookListener;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.pve.MobRank;
import hu.taliann.icesmp.pve.MobTemplateRegistry;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Opt-in real Paper/Folia probe for the bounded quest/item integrity surface. It uses the fully
 * assembled production managers and real ItemStack component registries, and is inert unless CI
 * supplies the dedicated JVM property.
 */
public final class QuestItemContentIntegrityPaperRuntimeProbe {

    public static final String PROPERTY = "icesmp.quest-item-content-integrity-runtime";
    public static final String PASS_MARKER =
            "ICESMP_QUEST_ITEM_CONTENT_INTEGRITY_RUNTIME_PROBE_PASS";
    public static final String FAIL_MARKER =
            "ICESMP_QUEST_ITEM_CONTENT_INTEGRITY_RUNTIME_PROBE_FAIL";

    private static final List<String> PROFESSION_OUTPUTS = List.of(
            "vadaszij", "mefonott_pajzs", "feszitett_szaru_ij", "celkereszt_szamszerij",
            "lancing", "lancnadrag", "pajzsdudor", "pancelozott_sisakrostely",
            "uszokeszlet", "vizallo_csizma", "melyvizi_horog", "teknos_sisak",
            "halaszkalap");

    private QuestItemContentIntegrityPaperRuntimeProbe() {
    }

    public static void maybeRun(final JavaPlugin plugin, final Object assembledCore) {
        if (!Boolean.getBoolean(PROPERTY)) return;
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            try {
                final QuestManager quests = readField(assembledCore,
                        "questManager", QuestManager.class);
                final MobTemplateRegistry mobs = readField(assembledCore,
                        "mobTemplateRegistry", MobTemplateRegistry.class);
                final UniqueMaterialFactory materials = readField(assembledCore,
                        "uniqueMaterialFactory", UniqueMaterialFactory.class);
                final ProfessionRecipeCatalog recipes = readField(assembledCore,
                        "professionRecipeCatalog", ProfessionRecipeCatalog.class);
                final ProfessionRecipeBookListener recipeBook = readField(assembledCore,
                        "professionRecipeBookListener", ProfessionRecipeBookListener.class);
                final MoneyPouchItemFactory pouches = readField(assembledCore,
                        "moneyPouchItemFactory", MoneyPouchItemFactory.class);
                final ItemIdentityService identity = readField(assembledCore,
                        "itemIdentityService", ItemIdentityService.class);

                verifyQuestCatalogAndGuestPreview(quests);
                verifyFactionResolution();
                verifyWorldBossRewards(mobs, materials);
                verifyProfessionOutputIdentity(recipes, recipeBook);
                verifyMoneyPouchOpacity(pouches);
                verifySignaturePresentation(identity);
                plugin.getLogger().info(PASS_MARKER + " platform="
                        + Bukkit.getServer().getName() + " minecraft=" + Bukkit.getMinecraftVersion());
            } catch (final Throwable failure) {
                plugin.getLogger().severe(FAIL_MARKER + ": " + failure);
                failure.printStackTrace();
            } finally {
                Bukkit.shutdown();
            }
        }, 1L);
    }

    private static void verifyQuestCatalogAndGuestPreview(final QuestManager quests) {
        check(quests.getQuestIds().size() == 195, "runtime quest denominator is not 195");
        final Player guest = playerFixture();
        int rewardBearing = 0;
        int guestOwn = 0;
        int capstones = 0;
        int authoredDaily = 0;
        int professionGated = 0;
        for (final String id : quests.getQuestIds()) {
            final ConfigurationSection quest = quests.getQuestSection(id);
            check(quest != null, "missing runtime quest section: " + id);
            final ConfigurationSection rewards = quest.getConfigurationSection("rewards");
            if (rewards != null && !rewards.getKeys(false).isEmpty()) {
                rewardBearing++;
                final List<String> preview = quests.describeRewards(guest, id);
                check(!preview.isEmpty(), "configured reward has no guest preview: " + id);
                if ("OWN".equalsIgnoreCase(rewards.getString("currency.type", ""))
                        && quest.getString("requires-faction", "").isBlank()) {
                    guestOwn++;
                    check(preview.stream().anyMatch(line -> line.contains("Creutzér")),
                            "guest OWN preview is not Creutzér: " + id);
                }
            } else {
                check(quests.describeRewards(guest, id).isEmpty(),
                        "rewardless quest renders an empty category payload: " + id);
            }
            if ("SPECIALIZATION".equalsIgnoreCase(quest.getString("category", ""))) {
                capstones++;
                final ConfigurationSection objectives =
                        quest.getConfigurationSection("objectives");
                check(objectives != null && objectives.getKeys(false).size() >= 2,
                        "generic single-objective capstone survived: " + id);
            }
            if ("DAILY".equalsIgnoreCase(quest.getString("category", ""))) authoredDaily++;
            if (!quest.getString("requires-profession", "").isBlank()) professionGated++;
        }
        check(rewardBearing == 160, "runtime reward-bearing quest count is not 160");
        check(guestOwn == 96, "runtime guest-safe OWN matrix is not 96");
        check(capstones == 35, "runtime capstone matrix is not 35");
        check(authoredDaily == 17, "runtime authored daily count is not 17");
        check(professionGated == 9, "runtime profession-gated quest count is not 9");

        final String herald = quests.getQuestSection("onboarding_herald")
                .getString("description", "");
        final String handoff = quests.getQuestSection("onboarding_utmutatas")
                .getString("description", "");
        check(herald.contains("Menedék"), "onboarding does not name the legitimate guest state");
        check(handoff.contains("/menu") && handoff.contains("/profile"),
                "onboarding handoff does not teach the canonical navigation pair");
    }

    private static void verifyFactionResolution() {
        check(QuestCurrencyResolver.resolve("OWN", Optional.of(FactionType.RED))
                        == CurrencyType.RED, "RED OWN mismatch");
        check(QuestCurrencyResolver.resolve("OWN", Optional.of(FactionType.BLUE))
                        == CurrencyType.BLUE, "BLUE OWN mismatch");
        check(QuestCurrencyResolver.resolve("OWN", Optional.of(FactionType.DARK))
                        == CurrencyType.DARK, "DARK OWN mismatch");
        check(QuestCurrencyResolver.resolve("OWN", Optional.of(FactionType.NEUTRAL))
                        == CurrencyType.NEUTRAL, "NEUTRAL OWN mismatch");
        check(QuestCurrencyResolver.resolve("OWN", Optional.empty())
                        == CurrencyType.NEUTRAL, "guest OWN mismatch");
    }

    private static void verifyWorldBossRewards(final MobTemplateRegistry mobs,
                                               final UniqueMaterialFactory materials) {
        final Set<String> rewards = new HashSet<>();
        int bosses = 0;
        for (final var entry : mobs.all().entrySet()) {
            final var template = entry.getValue();
            if (template.rank() != MobRank.WORLD_BOSS) continue;
            bosses++;
            check(!template.bossSpecificReward().isBlank(),
                    "world boss reward identity missing: " + entry.getKey());
            check(rewards.add(template.bossSpecificReward()),
                    "world boss reward identity is not distinct: " + entry.getKey());
            final ItemStack reward = materials.create(template.bossSpecificReward(), 1);
            check(reward != null && reward.hasItemMeta()
                            && reward.getItemMeta().hasDisplayName(),
                    "world boss reward cannot be constructed: " + entry.getKey());
        }
        check(bosses == 10 && rewards.size() == 10,
                "runtime world-boss reward matrix is not 10/10");
    }

    private static void verifyProfessionOutputIdentity(final ProfessionRecipeCatalog recipes,
                                                       final ProfessionRecipeBookListener recipeBook) {
        final Player crafter = playerFixture();
        for (final String id : PROFESSION_OUTPUTS) {
            final ProfessionRecipeCatalog.Recipe recipe = recipes.get(id);
            check(recipe != null, "profession output recipe missing: " + id);
            final ItemStack item = recipeBook.buildDeferredReward(crafter, recipe);
            check(item != null && item.hasItemMeta() && item.getItemMeta().displayName() != null,
                    "profession output lost its display component: " + id);
            final String actual = PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
            final String expected = PlainTextComponentSerializer.plainText().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(recipe.displayName()));
            check(actual.equals(expected) || actual.endsWith(" " + expected),
                    "profession output lost its authored name: " + id
                    + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void verifyMoneyPouchOpacity(final MoneyPouchItemFactory pouches) {
        final ItemStack pouch = pouches.create(CurrencyType.NEUTRAL, 37L);
        check(pouches.isPouch(pouch) && pouches.getValue(pouch) == 37L
                        && pouches.getCurrency(pouch) == CurrencyType.NEUTRAL,
                "Money Pouch did not retain its creation-time values");
        final String lore = pouch.getItemMeta().lore() == null ? ""
                : pouch.getItemMeta().lore().stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce("", (left, right) -> left + '\n' + right);
        check(!lore.contains("37") && !lore.contains("Creutzér") && !lore.contains("–"),
                "unopened Money Pouch leaked currency, amount or range");
    }

    private static void verifySignaturePresentation(final ItemIdentityService identity) {
        final ItemStack bow = identity.create("napfogyatkozas_fokusz",
                "runtime:quest-item-integrity", "server", null);
        check(bow != null && bow.getType() == Material.BOW && bow.hasItemMeta(),
                "Napfogyatkozás stable ID no longer constructs a bow");
        final String display = PlainTextComponentSerializer.plainText()
                .serialize(bow.getItemMeta().displayName());
        check("Napfogyatkozás Íja".equals(display),
                "Napfogyatkozás player-facing bow name mismatch: " + display);
    }

    private static Player playerFixture() {
        final UUID id = UUID.fromString("6a61239c-a209-44d0-b063-7f18b7ab62b7");
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> "RuntimeCrafter";
                    case "isOnline" -> true;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    case "toString" -> "QuestItemRuntimePlayer[" + id + ']';
                    default -> primitiveDefault(method.getReturnType());
                });
    }

    private static Object primitiveDefault(final Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static <T> T readField(final Object owner, final String name,
                                   final Class<T> type) {
        try {
            final Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (final ReflectiveOperationException failure) {
            throw new IllegalStateException("runtime core field unavailable: " + name, failure);
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
