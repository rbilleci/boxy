package boxy.core.it;

import boxy.core.repository.NamespaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class NamespaceIT extends BaseIT {

    private NamespaceRepository namespaceRepository;

    @BeforeEach
    void setup() {
        namespaceRepository = new NamespaceRepository(dataSource);
    }

    @Test
    void rename_updatesPathForDescendants() {
        namespaceRepository.create("a");
        namespaceRepository.create("a/b");
        namespaceRepository.create("a/b/c");

        namespaceRepository.rename("a/b", "x");

        assertThat(namespaceRepository.find("a/x")).isPresent();
        assertThat(namespaceRepository.find("a/b")).isNotPresent();
        assertThat(namespaceRepository.find("a/x/c")).isPresent();
    }

    @Test
    void move_updatesParentAndPaths() {
        namespaceRepository.create("p1");
        namespaceRepository.create("p1/child");
        namespaceRepository.create("p1/child/grand");
        namespaceRepository.create("p2");

        namespaceRepository.move("p1/child", "p2");

        assertThat(namespaceRepository.find("p2/child")).isPresent();
        assertThat(namespaceRepository.find("p1/child")).isNotPresent();
        assertThat(namespaceRepository.find("p2/child/grand")).isPresent();
    }

    @Test
    void delete_removesSubtree() {
        namespaceRepository.create("root");
        namespaceRepository.create("root/child");
        namespaceRepository.create("root/child/grand");

        namespaceRepository.delete("root");

        assertThat(namespaceRepository.find("root")).isNotPresent();
        assertThat(namespaceRepository.find("root/child")).isNotPresent();
        assertThat(namespaceRepository.find("root/child/grand")).isNotPresent();
    }
}
