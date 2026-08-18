package hu.taliann.icesmp.itemization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Conservative, deterministic preview. Consumption is owned by the transactional adapter. */
public final class ItemSalvageService {

    public enum Status { ALLOWED, FORBIDDEN, LEGACY_DISABLED, MALFORMED, ESCROWED, ALREADY_APPLIED }

    public record Tuning(int baseDust, int perRarityDust, int runeDustPerRune,
                         int signatureDust, int maximumDust) {
        public Tuning {
            if (baseDust < 0 || perRarityDust < 0 || runeDustPerRune < 0 || signatureDust < 0
                    || maximumDust < 0) throw new IllegalArgumentException("negative salvage tuning");
        }
    }

    public record Preview(Status status, UUID itemId, Map<String, Integer> outputs,
                          int estimatedInputValue, int estimatedOutputValue) {
        public Preview {
            outputs = Map.copyOf(outputs == null ? Map.of() : outputs);
            if (estimatedInputValue < 0 || estimatedOutputValue < 0
                    || estimatedOutputValue > estimatedInputValue) {
                throw new IllegalArgumentException("salvage cannot be profitable");
            }
        }

        public boolean allowed() { return status == Status.ALLOWED; }
    }

    public Preview preview(final ItemTemplate template, final ItemInstance item,
                           final Tuning tuning, final int conservativeInputValue,
                           final boolean escrowed) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(tuning, "tuning");
        if (escrowed) return denied(Status.ESCROWED, item.itemId(), conservativeInputValue);
        if (item.states().contains(ItemState.LEGACY)) {
            return denied(Status.LEGACY_DISABLED, item.itemId(), conservativeInputValue);
        }
        if (item.origin().sourceTag().startsWith("admin")
                || item.origin().sourceTag().startsWith("dev")) {
            return denied(Status.FORBIDDEN, item.itemId(), conservativeInputValue);
        }
        if (template.salvagePolicy() == ItemTemplate.SalvagePolicy.FORBIDDEN
                || template.bindPolicy() == ItemTemplate.BindPolicy.ACCOUNT) {
            return denied(Status.FORBIDDEN, item.itemId(), conservativeInputValue);
        }
        final long rawDust = (long) tuning.baseDust()
                + (long) template.rarity().ordinal() * tuning.perRarityDust()
                + item.ascension().stageIndex();
        final int dust = (int) Math.min(tuning.maximumDust(), Math.max(0L, rawDust));
        final LinkedHashMap<String, Integer> outputs = new LinkedHashMap<>();
        if (dust > 0) outputs.put("salvage_dust", dust);
        final int runeDust = (int) Math.min(tuning.maximumDust(),
                (long) item.runes().size() * tuning.runeDustPerRune());
        if (runeDust > 0) outputs.put("rune_dust", runeDust);
        final int signatureDust = Math.min(tuning.maximumDust(), tuning.signatureDust());
        if (template.salvagePolicy() == ItemTemplate.SalvagePolicy.SIGNATURE_MATERIALS
                && signatureDust > 0) {
            outputs.put("signature_dust", signatureDust);
        }
        final long outputValue = outputs.values().stream().mapToLong(Integer::longValue).sum();
        if (outputValue > conservativeInputValue) {
            throw new IllegalStateException("configured salvage yield exceeds conservative input value");
        }
        return new Preview(Status.ALLOWED, item.itemId(), outputs,
                conservativeInputValue, (int) outputValue);
    }

    private static Preview denied(final Status status, final UUID itemId, final int inputValue) {
        return new Preview(status, itemId, Map.of(), Math.max(0, inputValue), 0);
    }
}
