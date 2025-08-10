package boxy.core.domain;

public record Event(
        long id,
        Long sequence,
        long partitionId,
        String data) {
}
