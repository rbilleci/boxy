package boxy.core.domain;

import java.time.Instant;

/**
 * Represents a namespace for organizing topics and consumers.
 *
 * <p>Namespaces form a hierarchical path (e.g., "tenant-a/payments"). Each namespace
 * can contain topics and child namespaces. The path is hashed for efficient lookup.
 *
 * @param id Unique namespace identifier (auto-generated)
 * @param parentId Parent namespace ID (null for root)
 * @param name Single namespace component (e.g., "payments" for path "tenant-a/payments")
 * @param path Full hierarchical path (e.g., "tenant-a/payments")
 * @param createdAt When the namespace was created
 * @param lastModifiedAt When the namespace was last updated
 */
public record Namespace(
        long id,
        Long parentId,
        String name,
        String path,
        Instant createdAt,
        Instant lastModifiedAt) {
}
