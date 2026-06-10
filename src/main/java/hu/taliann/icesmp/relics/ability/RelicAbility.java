package hu.taliann.icesmp.relics.ability;

public interface RelicAbility {

    String id();

    boolean execute(RelicAbilityContext context);
}

