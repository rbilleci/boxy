package boxy.core.domain;

public record Event(
        long id,
        long partitionId,
        String data) {
}
