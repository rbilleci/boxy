package boxy.core.domain;

public record HeartbeatPolicy(
        int id,
        double heartbeatDeadlineMultiplier,
        double heartbeatIntervalBaseline,
        double heartbeatIntervalLimit,
        double heartbeatTargetQps) {
}
