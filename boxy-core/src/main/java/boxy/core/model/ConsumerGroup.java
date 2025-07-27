package boxy.core.model;

public record ConsumerGroup(
        long id,
        String tenant,
        String name,
        double heartbeatIntervalDefault,
        double heartbeatDeadlineMultiplier) {
}
