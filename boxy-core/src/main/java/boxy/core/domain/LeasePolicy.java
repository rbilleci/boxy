package boxy.core.domain;

public record LeasePolicy(
        int activeConsumersLimit,
        int leaseReleasePeriod) {
}
