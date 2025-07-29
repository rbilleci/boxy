package boxy.core.domain;

public record Partition(
        long id,
        long topicId,
        int partitionNumber,
        long highWatermark) {
}
