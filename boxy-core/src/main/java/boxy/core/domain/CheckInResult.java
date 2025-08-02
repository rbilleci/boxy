package boxy.core.domain;

import java.time.Instant;
import java.util.List;

public record CheckInResult(
        double heartbeatInterval,
        Instant heartbeatDeadline,
        Status status,
        List<Cursor> activeLeases) {

    public enum Status {
        ACCEPTED,
        REJECTED
    }

}