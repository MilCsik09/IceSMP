package hu.taliann.icesmp.data;

import org.bukkit.Material;

import java.util.Set;

/**
 * A single config-driven crafting restriction rule. A rule may carry a class (job)
 * requirement, a profession requirement, or both; every present requirement must be met.
 *
 * @param id the rule identifier from config
 * @param materials the result materials this rule applies to
 * @param hasJobRequirement whether a class requirement is present
 * @param requiredJob the class required to craft (null with hasJobRequirement means any class)
 * @param requiredJobLevel the minimum class level required
 * @param requiredProfession the profession required to craft (null means no profession requirement)
 * @param requiredProfessionLevel the minimum profession level required
 */
public record CraftingRule(
        String id,
        Set<Material> materials,
        boolean hasJobRequirement,
        JobType requiredJob,
        int requiredJobLevel,
        ProfessionType requiredProfession,
        int requiredProfessionLevel
) {

    public boolean appliesTo(final Material material) {
        return material != null && materials.contains(material);
    }
}
