package hu.taliann.icesmp.data;

import java.util.Locale;

/**
 * A varázslat-iskolák — mindegyikhez saját, bootstrap-regisztrált damage-type tartozik
 * (icesmp:&lt;kulcs&gt;), így a spellek sebzése iskolánként megkülönböztethető, és
 * iskola-specifikus ellenállás adható rájuk. Két réteg védekezik ellenük:
 * a Rúnavért (generikus, MINDEN iskolára hat) és az iskola-counter signature-enchantok
 * (Fagypáncél → fagymágia, Főnixtoll → tűzmágia — a {@link #resistEnchantId()} köti).
 *
 * <p>A spell→iskola besorolás configból jön (spells.yml, spell-schools: by-spell felülírás
 * → a caster kasztjának by-class defaultja → ŐSMÁGIA), így MINDEN spell egyedivé hangolható
 * restart nélkül.
 */
public enum SpellSchool {

    TUZ("tuz_magia", "tűzmágia", "fonixtoll"),
    FAGY("fagy_magia", "fagymágia", "fagypancel"),
    SZENT("szent_magia", "szent mágia", "ej_fatyol"),
    ARNYEK("arnyek_magia", "árnyékmágia", "arnyuzo"),
    TERMESZET("termeszet_magia", "természetmágia", "meregfojto"),
    VIHAR("vihar_magia", "viharmágia", "viharfogo"),
    KAOSZ("kaosz_magia", "káoszmágia", "kaosz_zabla"),
    OSMAGIA("magia", "ősmágia", null);

    private final String typeId;
    private final String displayName;
    private final String resistEnchantId;

    SpellSchool(final String typeId, final String displayName, final String resistEnchantId) {
        this.typeId = typeId;
        this.displayName = displayName;
        this.resistEnchantId = resistEnchantId;
    }

    /** A damage-type kulcs value-része (namespace: icesmp). */
    public String getTypeId() {
        return typeId;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Az iskola-counter enchant kulcs-value-ja (icesmp namespace), vagy null. */
    public String resistEnchantId() {
        return resistEnchantId;
    }

    /** Config-értékből (enum-név vagy type-id, kis/nagybetű-független); null = nincs találat. */
    public static SpellSchool fromInput(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (final SpellSchool school : values()) {
            if (school.name().toLowerCase(Locale.ROOT).equals(normalized) || school.typeId.equals(normalized)) {
                return school;
            }
        }
        return null;
    }

    /** A damage-type kulcs value-ja alapján (a resist-listener visszafejtése); null = nem iskola. */
    public static SpellSchool fromTypeId(final String typeId) {
        for (final SpellSchool school : values()) {
            if (school.typeId.equals(typeId)) {
                return school;
            }
        }
        return null;
    }
}
