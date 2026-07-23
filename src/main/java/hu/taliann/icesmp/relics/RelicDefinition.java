package hu.taliann.icesmp.relics;

import org.bukkit.Material;

import java.util.List;

public interface RelicDefinition {

    String id();

    String displayName();

    String displayColor();


    List<String> lore();

    Material material();

    default String description() {
        return "";
    }
}

