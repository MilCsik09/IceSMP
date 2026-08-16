package hu.taliann.icesmp.client.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * RECIPE_PAGE: a recept-katalógus egy szakma-szűrt lapja a vanilla recept-könyv
 * (ProfessionRecipeGUI) csempéivel azonos tartalommal — szintfeltétel, tervrajz-állapot
 * (megtanulva / loot-only / NPC-mob-drop), frakció-kötés, hozzávalók have/need bontásban
 * (vanilla + unique), affix-jelzés és craftolhatóság. A have-értékek a kérés
 * pillanatának inventory-állapotát tükrözik. Craft-action szándékosan nincs a
 * protokollban: a tényleges craft a vanilla recept-könyv felületén marad.
 */
public record RecipePagePayload(String professionId, int page, int pageCount, int totalRecipes,
                                List<Entry> entries) {

    /**
     * Egy recept-csempe; a {@code factionLabel} üres, ha a recept nem frakció-kötött.
     *
     * <p>A {@code kind} a recept-fajta (gyakorlo/hozam/egyedi/lanc/ritkasag). A vanilla
     * recept-könyv a gyakorló receptet külön kiírja, mert az szándékosan vanilla-értékű —
     * enélkül a natív felületen rossz üzletnek látszana, és a két felület tartalma elcsúszna.
     */
    public record Entry(String recipeId, String displayName, String category, String kind,
                        int requiredLevel, boolean levelOk, boolean blueprint, boolean learned,
                        boolean lootOnly, String factionLabel, boolean affix,
                        int resultAmount, boolean craftable, List<Ingredient> ingredients) {
        public Entry {
            ingredients = List.copyOf(ingredients);
        }
    }

    /** Egy hozzávaló-sor: megjelenítési név + szükséges/meglévő darabszám. */
    public record Ingredient(String displayName, int need, int have, boolean unique) {
    }

    public RecipePagePayload {
        entries = List.copyOf(entries);
    }

    public byte[] encode() {
        return ClientMessageCodec.encodePayload(out -> {
            ClientMessageCodec.writeString(out, professionId);
            out.writeInt(page);
            out.writeInt(pageCount);
            out.writeInt(totalRecipes);
            writeCount(out, entries.size());
            for (final Entry entry : entries) {
                ClientMessageCodec.writeString(out, entry.recipeId());
                ClientMessageCodec.writeString(out, entry.displayName());
                ClientMessageCodec.writeString(out, entry.category());
                ClientMessageCodec.writeString(out, entry.kind());
                out.writeInt(entry.requiredLevel());
                out.writeBoolean(entry.levelOk());
                out.writeBoolean(entry.blueprint());
                out.writeBoolean(entry.learned());
                out.writeBoolean(entry.lootOnly());
                ClientMessageCodec.writeString(out, entry.factionLabel());
                out.writeBoolean(entry.affix());
                out.writeInt(entry.resultAmount());
                out.writeBoolean(entry.craftable());
                writeCount(out, entry.ingredients().size());
                for (final Ingredient ingredient : entry.ingredients()) {
                    ClientMessageCodec.writeString(out, ingredient.displayName());
                    out.writeInt(ingredient.need());
                    out.writeInt(ingredient.have());
                    out.writeBoolean(ingredient.unique());
                }
            }
        });
    }

    public static RecipePagePayload decode(final byte[] payload) throws ClientProtocolException {
        return ClientMessageCodec.decodePayload(payload, in -> {
            final String professionId = ClientMessageCodec.readString(in);
            final int page = in.readInt();
            final int pageCount = in.readInt();
            final int totalRecipes = in.readInt();
            final int entryCount = readCount(in);
            final List<Entry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                final String recipeId = ClientMessageCodec.readString(in);
                final String displayName = ClientMessageCodec.readString(in);
                final String category = ClientMessageCodec.readString(in);
                final String kind = ClientMessageCodec.readString(in);
                final int requiredLevel = in.readInt();
                final boolean levelOk = in.readBoolean();
                final boolean blueprint = in.readBoolean();
                final boolean learned = in.readBoolean();
                final boolean lootOnly = in.readBoolean();
                final String factionLabel = ClientMessageCodec.readString(in);
                final boolean affix = in.readBoolean();
                final int resultAmount = in.readInt();
                final boolean craftable = in.readBoolean();
                final int ingredientCount = readCount(in);
                final List<Ingredient> ingredients = new ArrayList<>(ingredientCount);
                for (int j = 0; j < ingredientCount; j++) {
                    ingredients.add(new Ingredient(
                            ClientMessageCodec.readString(in),
                            in.readInt(), in.readInt(), in.readBoolean()));
                }
                entries.add(new Entry(recipeId, displayName, category, kind, requiredLevel, levelOk,
                        blueprint, learned, lootOnly, factionLabel, affix, resultAmount,
                        craftable, ingredients));
            }
            return new RecipePagePayload(professionId, page, pageCount, totalRecipes, entries);
        });
    }

    private static void writeCount(final DataOutputStream out, final int count) throws IOException {
        if (count > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new IOException("list exceeds protocol limit");
        }
        out.writeInt(count);
    }

    private static int readCount(final DataInputStream in) throws IOException, ClientProtocolException {
        final int count = in.readInt();
        if (count < 0 || count > ClientProtocol.MAX_LIST_ELEMENTS) {
            throw new ClientProtocolException("list length out of bounds: " + count);
        }
        return count;
    }
}
