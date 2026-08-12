package hu.taliann.icesmp.playerprofile.application;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public final class DeathEscrowDeliveryPlan {
    private DeathEscrowDeliveryPlan() { }

    public record Item(int index, String deliveryId, String encodedItem) { }

    public static List<Item> missing(final PlayerProfileDeathEscrowStore.Batch batch,
                                     final Set<String> presentDeliveryIds) {
        return IntStream.range(0, batch.encodedItems().size())
                .mapToObj(index -> new Item(index, deliveryId(batch.receiptId(), index),
                        batch.encodedItems().get(index)))
                .filter(item -> !presentDeliveryIds.contains(item.deliveryId()))
                .toList();
    }

    public static String deliveryId(final String receiptId, final int index) {
        if (index < 0) throw new IllegalArgumentException("negative escrow item index");
        return receiptId + ':' + index;
    }
}
