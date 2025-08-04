package boxy.core.domain;

public record HeartbeatPolicy(
        double heartbeatDeadlineMultiplier,
        double heartbeatIntervalBaseline,
        double heartbeatIntervalLimit,
        double heartbeatTargetQps) {
}
