package boxy.core.domain;

import java.time.Instant;

public record Namespace(
        long id,
        String name,
        Long parentId,
        String path,
        Instant createdAt,
        Instant lastModifiedAt) {
}
