package boxy.core.domain;

import java.time.Instant;

public record Namespace(
        long id,
        Long parentId,
        String name,
        String path,
        Instant createdAt,
        Instant lastModifiedAt) {
}
