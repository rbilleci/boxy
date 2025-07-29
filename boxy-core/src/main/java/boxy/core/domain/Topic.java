package boxy.core.domain;

public record Topic(
        long id,
        String tenant,
        String name,
        int partitions) {
}
