package boxy.persistence.model;

public record Partition(
        long id,
        long topicId,
        int partitionNumber,
        long highWatermark) {
}
