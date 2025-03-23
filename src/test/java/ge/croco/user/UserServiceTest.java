package ge.croco.user;

import ge.croco.user.enums.Role;
import ge.croco.user.model.UserDetails;
import ge.croco.user.model.UserRequest;
import ge.croco.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
public class UserServiceTest {

    @Container
    private static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpassword");

    @Container
    private static final KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));

    @Container
    private static final GenericContainer<?> hazelcastContainer = new GenericContainer<>(DockerImageName.parse("hazelcast/hazelcast:5.3.5"))
            .withExposedPorts(5701);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("hazelcast.network.tcpip.enabled", () -> "true");
        registry.add("hazelcast.network.tcpip.members", () -> hazelcastContainer.getHost() + ":" + hazelcastContainer.getMappedPort(5701));

    }

    @Autowired
    private UserService userService;

    @Test
    void testSaveAndGetUser() {
        // Test saving and retrieving a user
        UserDetails result = userService.createUser(
                new UserRequest(
                        "lashabolga",
                        "lasha@gmail.com",
                        "lasha111",
                        Set.of(Role.ADMIN)
                )
        );

        // Assert the result
        assertEquals("lashabolga", result.getUsername());
    }
}