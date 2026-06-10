package hu.taliann.icesmp.data;

import java.util.Map;
import java.util.UUID;

public record Wallet(UUID owner, Map<FactionType, Double> balances) {
}

