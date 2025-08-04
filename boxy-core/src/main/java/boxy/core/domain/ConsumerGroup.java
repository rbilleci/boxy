package boxy.core.domain;

import java.time.Instant;

public record ConsumerGroup(
        long id,
        String name,
        double heartbeatInterval,
        int activePartitions,
        int activeConsumers,
        double activeConsumersWeight,
        Instant lastModifiedAt) {
}
