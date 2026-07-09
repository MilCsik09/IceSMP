package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.AbundanceManager;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.CaravanManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.EscortManager;
import hu.taliann.icesmp.managers.MeteorEventManager;
import hu.taliann.icesmp.managers.ServerChallengeManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.ExchangeRateService;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.FactionTreasuryManager;
import hu.taliann.icesmp.managers.KingManager;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.managers.SeasonManager;
import hu.taliann.icesmp.managers.AchievementManager;
import hu.taliann.icesmp.managers.SoulShardManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.utils.MessageManager;

/**
 * Read-only manager bundle for the command-menu GUIs ({@link CommandMenus}). The
 * menus only READ state from these managers for display; every mutating action
 * is delegated to the already-validated commands via {@code player.performCommand}.
 */
public record CommandMenuContext(
        MessageManager messageManager,
        FactionManager factionManager,
        CurrencyManager currencyManager,
        ExchangeRateService exchangeRateService,
        FactionTreasuryManager treasuryManager,
        KingManager kingManager,
        RaidManager raidManager,
        QuestManager questManager,
        SeasonManager seasonManager,
        BloodMoonManager bloodMoonManager,
        WorldBossManager worldBossManager,
        CaravanManager caravanManager,
        EscortManager escortManager,
        AbundanceManager abundanceManager,
        ServerChallengeManager serverChallengeManager,
        MeteorEventManager meteorEventManager,
        SoulShardManager soulShardManager,
        SpecializationManager specializationManager,
        RelicManager relicManager,
        StatsManager statsManager,
        AchievementManager achievementManager,
        ConfigManager configManager) {
}
