package boxy.core.domain;

import java.time.Instant;

public record Namespace(
        long id,
        String tenant,
        String name,
        Instant createdAt,
        Instant lastModifiedAt) {
}
