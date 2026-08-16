package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.managers.TalentManager;
import hu.taliann.icesmp.utils.MessageManager;

/**
 * Shared dependency bundle for the character menu hub and its sub-menus
 * (profile, kaszt, specializáció, szakma, talentek, képesség-fa). Constructed
 * once in {@code IceSMPCore} and passed to the menus so back-navigation and
 * cross-menu routing don't need to re-thread every manager through each call.
 */
public record CharacterMenuContext(
        MessageManager messageManager,
        JobManager jobManager,
        SpecializationManager specializationManager,
        ProfessionManager professionManager,
        TalentManager talentManager,
        FactionManager factionManager,
        CurrencyManager currencyManager,
        SinManager sinManager,
        CatalystItemFactory catalystItemFactory,
        SpellRegistry spellRegistry,
        PetManager petManager,
        ResourceManager resourceManager,
        hu.taliann.icesmp.classrelic.ClassRelicService classRelicService,
        ConfigManager configManager,
        hu.taliann.icesmp.managers.RespecService respecService) {
}
