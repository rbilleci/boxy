import boxy.core.DataSourceProvider;
import boxy.core.dao.ConsumerGroupDao;
import boxy.core.model.ConsumerGroup;

import javax.sql.DataSource;
import java.util.Optional;

public class TestConsumerGroup {
    public static void main(String[] args) {
        try {
            // Set up database connection properties
            System.setProperty("DB_HOST", "localhost");
            System.setProperty("DB_PORT", "3306");
            System.setProperty("DB_NAME", "events_db");
            System.setProperty("DB_USER", "root");
            System.setProperty("DB_PASSWORD", "password");

            // Get data source
            DataSource dataSource = DataSourceProvider.dataSource();
            
            // Create ConsumerGroupDao
            ConsumerGroupDao consumerGroupDao = new ConsumerGroupDao(dataSource);
            
            // Create a consumer group
            String tenant = "test-tenant";
            String name = "test-group";
            long id = consumerGroupDao.create(tenant, name);
            System.out.println("Created consumer group with ID: " + id);
            
            // Find the consumer group
            Optional<ConsumerGroup> consumerGroup = consumerGroupDao.find(tenant, name);
            if (consumerGroup.isPresent()) {
                ConsumerGroup cg = consumerGroup.get();
                System.out.println("Found consumer group:");
                System.out.println("  ID: " + cg.id());
                System.out.println("  Tenant: " + cg.tenant());
                System.out.println("  Name: " + cg.name());
                System.out.println("  Heartbeat Interval Default: " + cg.heartbeatIntervalDefault());
                System.out.println("  Heartbeat Deadline Multiplier: " + cg.heartbeatDeadlineMultiplier());
                System.out.println("  Active Workers Count: " + cg.activeWorkersCount());
                System.out.println("  Total Weight: " + cg.totalWeight());
                System.out.println("  Active Partitions Count: " + cg.activePartitionsCount());
                System.out.println("  Last Updated: " + cg.lastUpdated());
            } else {
                System.out.println("Consumer group not found!");
            }
            
            // Clean up
            DataSourceProvider.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}