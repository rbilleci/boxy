package boxy.core.domain;

import java.time.Instant;

public record Event(
        long id,
        Instant timestamp,
        long partitionId,
        String data) {
}
