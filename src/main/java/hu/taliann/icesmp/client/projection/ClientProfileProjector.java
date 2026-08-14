package hu.taliann.icesmp.client.projection;

import hu.taliann.icesmp.client.protocol.ProfileStatePayload;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionCategory;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.gui.CharacterMenuContext;
import hu.taliann.icesmp.managers.AchievementManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.StatsManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A /profile GUI fejlécével és egyenleg-nézetével tartalmilag azonos PROFILE_STATE
 * leképezés — ugyanazokból a manager-hívásokból, mint a vanilla GUI, így a két felület
 * nem csúszhat szét. Kizárólag display-adat; a játékos saját régió-szálán hívandó
 * (élő player-állapotot olvas).
 */
public final class ClientProfileProjector {

    private ClientProfileProjector() {
    }

    public static ProfileStatePayload project(final Player player, final CharacterMenuContext ctx,
                                              final StatsManager statsManager,
                                              final AchievementManager achievementManager) {
        final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        final FactionType faction = ctx.factionManager().getChosenFaction(player.getUniqueId()).orElse(null);
        final JobType job = ctx.jobManager().getPrimaryJob(player);
        final SpecializationType classSpec = ctx.specializationManager().getClassSpecialization(player);
        final ProfessionType gathering = ctx.professionManager().getProfession(player, ProfessionCategory.GATHERING);
        final ProfessionType crafting = ctx.professionManager().getProfession(player, ProfessionCategory.CRAFTING);
        final ProfessionSpecializationType profSpec = ctx.specializationManager().getProfessionSpecialization(player);

        final List<ProfileStatePayload.Balance> balances = new ArrayList<>(FactionType.values().length);
        for (final FactionType type : FactionType.values()) {
            balances.add(new ProfileStatePayload.Balance(type.getDisplayName(),
                    ctx.currencyManager().formatBalance(ctx.currencyManager().getBalance(player, type))));
        }

        int achievementsTotal = 0;
        int achievementsEarned = 0;
        for (final AchievementManager.Achievement achievement : achievementManager.getAchievements()) {
            achievementsTotal++;
            if (achievementManager.isEarned(player, achievement.id())) {
                achievementsEarned++;
            }
        }

        return new ProfileStatePayload(
                player.getName(),
                faction == null ? "Menedék vendége"
                        : faction.getDisplayName() + " (" + faction.getFullName() + ")",
                faction == null ? "" : faction.name(),
                job == null ? "" : plain.serialize(job.getDisplayName()),
                job == null ? 0 : ctx.jobManager().getPrimaryLevel(player),
                JobManager.MAX_JOB_LEVEL,
                classSpec == null ? "" : plain.serialize(classSpec.getDisplayName()),
                gathering == null ? "" : plain.serialize(gathering.getDisplayName()),
                gathering == null ? 0 : ctx.professionManager().getLevel(player, gathering),
                crafting == null ? "" : plain.serialize(crafting.getDisplayName()),
                crafting == null ? 0 : ctx.professionManager().getLevel(player, crafting),
                profSpec == null ? "" : plain.serialize(profSpec.getDisplayName()),
                ctx.sinManager() != null && ctx.sinManager().isSinner(player),
                ctx.talentManager().getAvailablePoints(player, true),
                ctx.talentManager().getAvailablePoints(player, false),
                balances,
                new ProfileStatePayload.Stats(
                        statsManager.getKills(player.getUniqueId()),
                        statsManager.getDeaths(player.getUniqueId()),
                        statsManager.getMobKills(player.getUniqueId()),
                        statsManager.getSpellCasts(player.getUniqueId()),
                        statsManager.getQuestsCompleted(player.getUniqueId()),
                        statsManager.getRaidKills(player.getUniqueId())),
                achievementsEarned, achievementsTotal);
    }
}
