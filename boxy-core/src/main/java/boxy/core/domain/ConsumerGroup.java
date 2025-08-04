package boxy.core.domain;

import java.time.Instant;

public record ConsumerGroup(
        long id,
        String name,
        Instant lastModifiedAt) {
}
