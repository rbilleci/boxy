package boxy.persistence.model;

public record Topic(
        long id,
        String tenant,
        String name,
        int partitions) {
}
