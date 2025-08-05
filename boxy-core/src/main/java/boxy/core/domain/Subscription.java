package boxy.core.domain;

import java.time.Instant;

public record Subscription(
        long id,
        String name,
        Instant lastModifiedAt) {
}
