package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.ProfessionCategory;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileService;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.ProfessionProfileState;
import hu.taliann.icesmp.playerprofile.domain.section.ProfessionSection;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * WoW-style profession manager backed exclusively by PlayerProfile.
 *
 * <p>The two primary slots, profession XP/level mirrors and learned recipes live in the
 * {@link ProfessionSection}. Reads use the session-fenced repository cache; mutations use
 * conditional section CAS and only trigger Bukkit feedback after durable commit.</p>
 */
public final class ProfessionManager implements PlayerStateCleanup {

    public static final int MAX_PROFESSION_LEVEL = 50;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    /** Setter-injected optional localized level-up feedback. */
    private volatile hu.taliann.icesmp.utils.MessageManager messageManager;

    public ProfessionManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
    }

    public void setMessageManager(final hu.taliann.icesmp.utils.MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    public ProfessionType getProfession(final Player player, final ProfessionCategory category) {
        Objects.requireNonNull(player, "player");
        return ProfessionProfileState.selected(section(player.getUniqueId()), category);
    }

    public boolean hasProfession(final Player player, final ProfessionType professionType) {
        Objects.requireNonNull(player, "player");
        return ProfessionProfileState.hasProfession(section(player.getUniqueId()), professionType);
    }

    public List<ProfessionType> getActiveProfessions(final Player player) {
        final List<ProfessionType> active = new ArrayList<>();
        final ProfessionType gathering = getProfession(player, ProfessionCategory.GATHERING);
        if (gathering != null) {
            active.add(gathering);
        }
        final ProfessionType crafting = getProfession(player, ProfessionCategory.CRAFTING);
        if (crafting != null) {
            active.add(crafting);
        }
        for (final ProfessionType professionType : ProfessionType.values()) {
            if (professionType.getCategory() == ProfessionCategory.SECONDARY) {
                active.add(professionType);
            }
        }
        return List.copyOf(active);
    }

    public CompletionStage<Boolean> selectProfession(final Player player,
                                                     final ProfessionType professionType) {
        if (!isPrimary(professionType)) {
            return CompletableFuture.completedFuture(false);
        }
        final UUID playerId = player.getUniqueId();
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.PROFESSIONS, ProfessionSection.class,
                current -> {
                    final ProfessionProfileState.Mutation mutation =
                            ProfessionProfileState.select(current, professionType);
                    return mutation.changed()
                            ? PlayerProfileService.ConditionalMutation.changed(mutation.section(), true)
                            : PlayerProfileService.ConditionalMutation.unchanged(false);
                }).thenApply(changed -> {
                    if (changed) {
                        runOnOwnerThread(player, () -> {
                            if (player.isOnline()) {
                                AdvancementService.award(player, "profession_pick");
                            }
                        });
                    }
                    return changed;
                });
    }

    public CompletionStage<Boolean> setProfession(final Player player,
                                                  final ProfessionType professionType) {
        if (!isPrimary(professionType)) {
            return CompletableFuture.completedFuture(false);
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                player.getUniqueId(), ProfileSectionId.PROFESSIONS, ProfessionSection.class,
                current -> {
                    final ProfessionProfileState.Mutation mutation =
                            ProfessionProfileState.set(current, professionType);
                    return mutation.changed()
                            ? PlayerProfileService.ConditionalMutation.changed(mutation.section(), true)
                            : PlayerProfileService.ConditionalMutation.unchanged(false);
                });
    }

    public CompletionStage<Boolean> clearProfession(final Player player,
                                                    final ProfessionCategory category) {
        if (category == null || category == ProfessionCategory.SECONDARY) {
            return CompletableFuture.completedFuture(false);
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                player.getUniqueId(), ProfileSectionId.PROFESSIONS, ProfessionSection.class,
                current -> {
                    final ProfessionProfileState.Mutation mutation =
                            ProfessionProfileState.clear(current, category);
                    return mutation.changed()
                            ? PlayerProfileService.ConditionalMutation.changed(mutation.section(), true)
                            : PlayerProfileService.ConditionalMutation.unchanged(false);
                });
    }

    public int getXp(final Player player, final ProfessionType professionType) {
        if (professionType == null) {
            return 0;
        }
        return Math.toIntExact(ProfessionProfileState.experience(
                section(player.getUniqueId()), professionType));
    }

    public CompletionStage<XpChange> setXp(final Player player,
                                           final ProfessionType professionType,
                                           final int xp) {
        if (professionType == null) {
            return CompletableFuture.completedFuture(XpChange.rejected());
        }
        final int normalized = Math.max(0, xp);
        return mutateXp(player, professionType,
                current -> ProfessionProfileState.setExperience(current, professionType,
                        normalized, baseXp(), incrementPerLevel(), MAX_PROFESSION_LEVEL));
    }

    public CompletionStage<XpChange> addXp(final Player player,
                                           final ProfessionType professionType,
                                           final int amount) {
        if (amount <= 0 || professionType == null) {
            return CompletableFuture.completedFuture(XpChange.rejected());
        }
        return mutateXp(player, professionType,
                current -> ProfessionProfileState.addExperience(current, professionType,
                        amount, baseXp(), incrementPerLevel(), MAX_PROFESSION_LEVEL));
    }

    /** Awards activity XP only when the player actively practices the profession. */
    public CompletionStage<XpChange> addXpFor(final Player player,
                                              final ProfessionType professionType,
                                              final int amount) {
        if (!hasProfession(player, professionType)) {
            return CompletableFuture.completedFuture(XpChange.rejected());
        }
        return addXp(player, professionType, amount).thenApply(change -> {
            if (change.changed() && change.level() > change.previousLevel()) {
                runOnOwnerThread(player, () -> applyLevelUpFeedback(player, professionType, change));
            }
            return change;
        });
    }

    public static String getRankName(final int level) {
        if (level >= MAX_PROFESSION_LEVEL) {
            return "Legendás Mester";
        }
        if (level >= 40) {
            return "Nagymester";
        }
        if (level >= 30) {
            return "Mester";
        }
        if (level >= 20) {
            return "Legény";
        }
        if (level >= 10) {
            return "Segéd";
        }
        return "Inas";
    }

    public int getLevel(final Player player, final ProfessionType professionType) {
        if (professionType == null) {
            return 1;
        }
        return ProfessionProfileState.level(section(player.getUniqueId()), professionType,
                baseXp(), incrementPerLevel(), MAX_PROFESSION_LEVEL);
    }

    /**
     * XP-bontás a progressz-kijelzéshez: az aktuális szintbe már beleölt XP és a
     * szintlépés teljes költsége (0 = max szint). A bejárásnak bitre egyeznie kell a
     * {@link ProfessionProfileState#levelForExperience} szint-formulájával, különben a
     * kijelzett progressz elcsúszna a tényleges szintléptetéstől.
     */
    public XpBreakdown xpBreakdown(final Player player, final ProfessionType professionType) {
        if (professionType == null) {
            return new XpBreakdown(0L, 0L);
        }
        final long base = Math.max(1L, baseXp());
        final long increment = Math.max(0L, incrementPerLevel());
        long remaining = ProfessionProfileState.experience(
                section(player.getUniqueId()), professionType);
        int level = 1;
        while (level < MAX_PROFESSION_LEVEL) {
            final long levelCost = base + (level - 1L) * increment;
            if (remaining < levelCost) {
                return new XpBreakdown(remaining, levelCost);
            }
            remaining -= levelCost;
            level++;
        }
        return new XpBreakdown(remaining, 0L);
    }

    public boolean hasLearnedRecipe(final Player player, final String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return false;
        }
        final String normalized = recipeId.trim().toLowerCase(Locale.ROOT);
        return section(player.getUniqueId()).recipes().contains(normalized);
    }

    public CompletionStage<Boolean> learnRecipe(final Player player, final String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                player.getUniqueId(), ProfileSectionId.PROFESSIONS, ProfessionSection.class,
                current -> {
                    final ProfessionProfileState.RecipeMutation mutation =
                            ProfessionProfileState.learnRecipe(current, recipeId);
                    return mutation.changed()
                            ? PlayerProfileService.ConditionalMutation.changed(mutation.section(), true)
                            : PlayerProfileService.ConditionalMutation.unchanged(false);
                });
    }

    public void runOnOwnerThread(final Player player, final Runnable action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        player.getScheduler().run(plugin, ignored -> action.run(), null);
    }

    public void cleanup(final UUID playerId) {
        // PlayerProfile lifecycle owns durable state and cache invalidation.
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }

    private CompletionStage<XpChange> mutateXp(
            final Player player,
            final ProfessionType professionType,
            final java.util.function.Function<ProfessionSection,
                    ProfessionProfileState.ExperienceMutation> mutation) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                player.getUniqueId(), ProfileSectionId.PROFESSIONS, ProfessionSection.class,
                current -> {
                    final ProfessionProfileState.ExperienceMutation result = mutation.apply(current);
                    final XpChange change = new XpChange(result.changed(), result.previousExperience(),
                            result.experience(), result.previousLevel(), result.level());
                    return result.changed()
                            ? PlayerProfileService.ConditionalMutation.changed(result.section(), change)
                            : PlayerProfileService.ConditionalMutation.unchanged(change);
                });
    }

    private ProfessionSection section(final UUID playerId) {
        return PlayerProfileAuthority.current().requireSection(
                playerId, ProfileSectionId.PROFESSIONS, ProfessionSection.class);
    }

    int baseXp() {
        return Math.max(1, configManager.getInt("professions.leveling.base-xp", 100));
    }

    int incrementPerLevel() {
        return Math.max(0, configManager.getInt(
                "professions.leveling.increment-per-level", 15));
    }

    private static boolean isPrimary(final ProfessionType professionType) {
        return professionType != null
                && professionType.getCategory() != ProfessionCategory.SECONDARY;
    }

    private void applyLevelUpFeedback(final Player player,
                                      final ProfessionType professionType,
                                      final XpChange change) {
        if (!player.isOnline()) {
            return;
        }
        final hu.taliann.icesmp.utils.MessageManager messages = messageManager;
        if (messages == null) {
            return;
        }
        final String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(professionType.getDisplayName());
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.9F, 1.3F);
        player.sendMessage(messages.getMessage("profession-level-up",
                "<green>⚒ Szakma-szintlépés: <white>{profession}</white> — <gold>{level}. szint</gold> <gray>({rank})</gray></green>",
                java.util.Map.of("profession", name,
                        "level", String.valueOf(change.level()),
                        "rank", getRankName(change.level()))));
        if (change.level() >= MAX_PROFESSION_LEVEL) {
            AdvancementService.award(player, "profession_master");
        }
        if (!getRankName(change.previousLevel()).equals(getRankName(change.level()))) {
            player.sendMessage(messages.getMessage("profession-rank-up",
                    "<gold>🏅 Új fokozat: <white>{rank}</white>! A céh elismeri a munkádat.</gold>",
                    java.util.Map.of("rank", getRankName(change.level()))));
        }
    }

    public record XpChange(boolean changed, long previousExperience, long experience,
                           int previousLevel, int level) {
        public static XpChange rejected() {
            return new XpChange(false, 0L, 0L, 1, 1);
        }
    }

    /** Progressz-nézet: az aktuális szintbe beleölt XP + a szintlépés teljes költsége. */
    public record XpBreakdown(long intoLevel, long forNextLevel) {
    }
}
