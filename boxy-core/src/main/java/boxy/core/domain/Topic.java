package boxy.core.domain;

import java.time.Instant;

public record Topic(
        long id,
        long namespaceId,
        String name,
        int partitions,
        Instant createdAt,
        Instant lastModifiedAt) {
}
