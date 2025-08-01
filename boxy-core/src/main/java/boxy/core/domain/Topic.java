package boxy.core.domain;

public record Topic(
        long id,
        long namespaceId,
        String name,
        int partitions) {
}
