package hu.taliann.icesmp.items;

import net.kyori.adventure.key.Key;

import java.util.Map;

/**
 * A signature itemek egyedi, bootstrap-szinten regisztrált enchantjainak kulcsai —
 * a lore-nevek valódi enchant-sorként jelennek meg a tárgy tooltipjében (a
 * data-driven enchant-registry a klienssel szinkronizálódik, resource pack nélkül
 * is renderelődik). A VISELKEDÉST továbbra is a SignatureItemListener adja; az
 * enchant az identitás (név + glint) és a vanília anvil-szabályok hordozója.
 *
 * <p>Egy helyen él a signature-id → enchant-kulcs megfeleltetés, mert a
 * bootstrap (regisztráció) és a canonical identity render/migration is ezt használja.
 */
public final class SignatureEnchantKeys {

    /** signature-id → enchant-kulcs (icesmp namespace). */
    public static final Map<String, Key> BY_SIGNATURE = Map.of(
            "kallan_szeletelo", Key.key("icesmp", "jegfog"),
            "pyralingradi_tuzkopo", Key.key("icesmp", "vihartuz"),
            "verszavanna_agyara", Key.key("icesmp", "verszomj"),
            "glatziendorfi_jegvert", Key.key("icesmp", "fagypancel"),
            "fonix_tollkopeny", Key.key("icesmp", "fonixtoll"),
            "vasmuvek_csakanya", Key.key("icesmp", "erc_erzek"),
            "bokic_horgaszbot", Key.key("icesmp", "bokic_kegye"));

    private SignatureEnchantKeys() {
    }
}
