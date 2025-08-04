package boxy.core.domain;

public record LeasePolicy(
        int id,
        int activeConsumersLimit,
        int leaseReleasePeriod) {
}
