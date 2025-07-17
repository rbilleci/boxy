package boxy.persistence.model;

public record ConsumerGroup(
        long id,
        String tenant,
        String name) {
}
