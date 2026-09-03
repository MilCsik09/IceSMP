package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Pure resolver for damage, exhaustion and AI decisions. */
public final class FactionPassivePolicy {

    /** DARK's fixed everyday cost; Blood Moon and dungeon encounters are exempt. */
    public static final double DARK_NORMAL_HEALING_MULTIPLIER = 0.70D;

    public enum DamageChannel {
        RED_FIRE,
        RED_FIRE_TICK,
        RED_ENTITY_FIRE,
        RED_LAVA,
        RED_HOT_FLOOR,
        ICE_SMP_FIRE_MAGIC,
        BLUE_FREEZE,
        BLUE_DROWNING,
        NEUTRAL_FALL,
        DARK_WITHER
    }

    public enum ContentContext {
        CORRUPTION,
        DUNGEON,
        INVASION,
        WORLD_BOSS,
        EVENT_MOB,
        QUEST_MOB,
        CROWN_CURSE
    }

    public enum TargetDecision {
        ALLOW,
        CANCEL_NEUTRAL_TRUCE,
        CANCEL_ENDERMAN_STARE,
        CANCEL_DARK_AMBIENT,
        CANCEL_DARK_WILD,
        CANCEL_WHISPER_WILD
    }

    public record TargetContext(
            boolean adminOrScriptedForce,
            Set<ContentContext> contentContexts,
            boolean provokedOrRetaliating,
            boolean bloodMoon,
            boolean night,
            boolean undead,
            boolean ambientUndead,
            boolean neutralMob,
            boolean enderman,
            boolean spontaneousEndermanStare,
            boolean spontaneousNeutralAggro,
            boolean spontaneousUndeadAggro,
            boolean whisperer) {

        public TargetContext {
            contentContexts = contentContexts == null || contentContexts.isEmpty()
                    ? EnumSet.noneOf(ContentContext.class)
                    : EnumSet.copyOf(contentContexts);
        }
    }

    public double damageMultiplier(final FactionMembership membership,
                                   final DamageChannel channel,
                                   final FactionPassiveSettings settings) {
        if (!settings.enabled() || !membership.isEligibleForFactionBenefits()) {
            return 1.0D;
        }
        if (membership.isMember(FactionType.RED) && settings.red().enabled()) {
            return switch (channel) {
                case RED_FIRE -> settings.red().fireDamageMultiplier();
                case RED_FIRE_TICK -> settings.red().fireTickDamageMultiplier();
                case RED_ENTITY_FIRE -> settings.red().entityFireDamageMultiplier();
                case RED_LAVA -> settings.red().lavaDamageMultiplier();
                case RED_HOT_FLOOR -> settings.red().hotFloorDamageMultiplier();
                case ICE_SMP_FIRE_MAGIC -> settings.red().affectIceSmpFireMagic()
                        ? settings.red().fireMagicDamageMultiplier() : 1.0D;
                default -> 1.0D;
            };
        }
        if (membership.isMember(FactionType.BLUE) && settings.blue().enabled()) {
            return switch (channel) {
                case BLUE_FREEZE -> settings.blue().freezeDamageMultiplier();
                case BLUE_DROWNING -> settings.blue().drowningDamageMultiplier();
                default -> 1.0D;
            };
        }
        if (membership.isMember(FactionType.NEUTRAL) && settings.neutral().enabled()
                && channel == DamageChannel.NEUTRAL_FALL) {
            return settings.neutral().fallDamageMultiplier();
        }
        if (membership.isMember(FactionType.DARK) && settings.dark().enabled()
                && settings.dark().witherDamageEnabled()
                && channel == DamageChannel.DARK_WITHER) {
            return settings.dark().witherDamageMultiplier();
        }
        return 1.0D;
    }

    public double witherDurationMultiplier(final FactionMembership membership,
                                           final FactionPassiveSettings settings) {
        if (!settings.enabled() || !settings.dark().enabled() || !settings.dark().witherDurationEnabled()
                || !membership.isMember(FactionType.DARK)) {
            return 1.0D;
        }
        return settings.dark().witherDurationMultiplier();
    }

    public double healingMultiplier(final FactionMembership membership,
                                    final boolean highStakesExempt) {
        if (highStakesExempt || !membership.isMember(FactionType.DARK)) {
            return 1.0D;
        }
        return DARK_NORMAL_HEALING_MULTIPLIER;
    }

    public double blueExhaustionSaveChance(final FactionMembership membership,
                                           final String exhaustionReason,
                                           final FactionPassiveSettings settings) {
        if (!settings.enabled() || !settings.blue().enabled()
                || !membership.isMember(FactionType.BLUE) || exhaustionReason == null) {
            return 0.0D;
        }
        final String normalized = exhaustionReason.toUpperCase(Locale.ROOT);
        return settings.blue().affectedExhaustionReasons().contains(normalized)
                ? settings.blue().naturalExhaustionSaveChance() : 0.0D;
    }

    /** Revalidates a queued ambient-undead alert against live membership/config/state. */
    public boolean canAlertDarkUndead(final FactionMembership membership,
                                      final boolean retaliationActive,
                                      final Set<ContentContext> contentContexts,
                                      final FactionPassiveSettings settings) {
        final Set<ContentContext> contexts = contentContexts == null ? Set.of() : contentContexts;
        return retaliationActive
                && settings.enabled()
                && settings.dark().enabled()
                && settings.dark().ambientUndead().enabled()
                && settings.dark().ambientUndead().breakOnDamage()
                && membership.isMember(FactionType.DARK)
                && !hasExcludedContent(contexts, settings.dark().exclusions())
                && !(contexts.contains(ContentContext.CROWN_CURSE)
                && settings.dark().exclusions().crownCurse());
    }

    /**
     * Precedence: forced target → marked combat content → crown curse → retaliation →
     * Blood Moon → ambient citizenship → wild passive → vanilla.
     */
    public TargetDecision resolveTarget(final FactionMembership membership,
                                        final TargetContext context,
                                        final FactionPassiveSettings settings,
                                        final double randomSample) {
        if (!settings.enabled()) {
            return TargetDecision.ALLOW;
        }
        if (context.adminOrScriptedForce()) {
            return TargetDecision.ALLOW;
        }
        if (membership.isMember(FactionType.DARK)) {
            if (hasExcludedContent(context.contentContexts(), settings.dark().exclusions())) {
                return TargetDecision.ALLOW;
            }
            if (context.contentContexts().contains(ContentContext.CROWN_CURSE)
                    && settings.dark().exclusions().crownCurse()) {
                return TargetDecision.ALLOW;
            }
        } else if (!context.contentContexts().isEmpty()) {
            return TargetDecision.ALLOW;
        }
        if (context.provokedOrRetaliating()) {
            if (membership.isMember(FactionType.NEUTRAL) && settings.neutral().enabled()) {
                if (context.enderman() && !settings.neutral().allowEndermanRetaliation()) {
                    return TargetDecision.CANCEL_ENDERMAN_STARE;
                }
                if (context.neutralMob() && settings.neutral().passiveMobTruceEnabled()
                        && !settings.neutral().breakOnDamage()) {
                    return TargetDecision.CANCEL_NEUTRAL_TRUCE;
                }
            }
            if (membership.isMember(FactionType.DARK) && settings.dark().enabled()
                    && context.ambientUndead()
                    && settings.dark().ambientUndead().enabled()
                    && context.bloodMoon()
                    && settings.dark().ambientUndead().disabledDuringBloodMoon()) {
                return TargetDecision.ALLOW;
            }
            if (membership.isMember(FactionType.DARK) && settings.dark().enabled()
                    && context.ambientUndead()
                    && settings.dark().ambientUndead().enabled()
                    && !settings.dark().ambientUndead().breakOnDamage()) {
                return TargetDecision.CANCEL_DARK_AMBIENT;
            }
            final FactionPassiveSettings.Whisper whisper = settings.whisper();
            if (membership.isEligibleForFactionBenefits() && !membership.isMember(FactionType.DARK)
                    && context.whisperer() && context.undead() && whisper.enabled()
                    && !whisper.breakOnDamage()
                    && (!whisper.nightOnly() || context.night())
                    && (!context.bloodMoon() || !whisper.disabledDuringBloodMoon())
                    && randomSample < whisper.targetCancelChance()) {
                return TargetDecision.CANCEL_WHISPER_WILD;
            }
            return TargetDecision.ALLOW;
        }

        if (membership.isEligibleForFactionBenefits()
                && membership.isMember(FactionType.DARK)
                && settings.dark().enabled() && context.undead()) {
            if (context.spontaneousUndeadAggro()
                    && context.ambientUndead() && settings.dark().ambientUndead().enabled()) {
                if (context.bloodMoon()
                        && settings.dark().ambientUndead().disabledDuringBloodMoon()) {
                    return TargetDecision.ALLOW;
                }
                return TargetDecision.CANCEL_DARK_AMBIENT;
            }
            final FactionPassiveSettings.WildUndead wild = settings.dark().wildUndead();
            if (context.spontaneousUndeadAggro() && wild.enabled()
                    && (!context.bloodMoon() || !wild.disabledDuringBloodMoon())
                    && (!wild.nightOnly() || context.night())
                    && randomSample < wild.targetCancelChance()) {
                return TargetDecision.CANCEL_DARK_WILD;
            }
        }

        if (membership.isEligibleForFactionBenefits()
                && membership.isMember(FactionType.NEUTRAL)
                && settings.neutral().enabled()) {
            if (context.enderman() && context.spontaneousEndermanStare()
                    && settings.neutral().ignoreEndermanStareAggro()) {
                return TargetDecision.CANCEL_ENDERMAN_STARE;
            }
            if (context.neutralMob() && context.spontaneousNeutralAggro()
                    && settings.neutral().passiveMobTruceEnabled()) {
                return TargetDecision.CANCEL_NEUTRAL_TRUCE;
            }
        }

        final FactionPassiveSettings.Whisper whisper = settings.whisper();
        if (membership.isEligibleForFactionBenefits() && !membership.isMember(FactionType.DARK)
                && context.whisperer() && context.undead() && context.spontaneousUndeadAggro()
                && whisper.enabled()
                && (!whisper.nightOnly() || context.night())
                && (!context.bloodMoon() || !whisper.disabledDuringBloodMoon())
                && randomSample < whisper.targetCancelChance()) {
            return TargetDecision.CANCEL_WHISPER_WILD;
        }
        return TargetDecision.ALLOW;
    }

    public static boolean hasExcludedContent(final Set<ContentContext> contexts,
                                             final FactionPassiveSettings.Exclusions exclusions) {
        return contexts.contains(ContentContext.CORRUPTION) && exclusions.corruption()
                || contexts.contains(ContentContext.DUNGEON) && exclusions.dungeon()
                || contexts.contains(ContentContext.INVASION) && exclusions.invasion()
                || contexts.contains(ContentContext.WORLD_BOSS) && exclusions.worldBoss()
                || contexts.contains(ContentContext.EVENT_MOB) && exclusions.eventMobs()
                || contexts.contains(ContentContext.QUEST_MOB) && exclusions.questMobs();
    }
}
