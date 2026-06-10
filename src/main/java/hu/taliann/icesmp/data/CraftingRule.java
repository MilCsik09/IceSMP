package hu.taliann.icesmp.data;

import org.bukkit.Material;

import java.util.Set;

/**
 * A single config-driven crafting restriction rule.
 *
 * @param id the rule identifier from config
 * @param materials the result materials this rule applies to
 * @param requiredJob the job required to craft (null means any job is accepted)
 * @param requiredLevel the minimum job level required
 */
public record CraftingRule(
        String id,
        Set<Material> materials,
        JobType requiredJob,
        int requiredLevel
) {

    public boolean appliesTo(final Material material) {
        return material != null && materials.contains(material);
    }
}
