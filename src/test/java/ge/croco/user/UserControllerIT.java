package ge.croco.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.croco.user.domain.User;
import ge.croco.user.enums.EventType;
import ge.croco.user.enums.Role;
import ge.croco.user.exception.UserAlreadyExistsException;
import ge.croco.user.model.*;
import ge.croco.user.repository.UserRepository;
import ge.croco.user.service.UserService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static ge.croco.user.config.CacheConfig.USER_CACHE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
@Sql(scripts = "/scripts/truncate_data.sql",executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class UserControllerIT {

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

    private static final String KAFKA_TOPIC = "users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private CacheManager cacheManager;

    private static ConsumerRecord<String, String> getLatestRecordForTopic(Consumer<String, String> consumer, String topic) {
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        AtomicReference<ConsumerRecord<String, String>> record = new AtomicReference<>();
        records.records(topic).iterator().forEachRemaining(record::set);
        return record.get();
    }

    private static @NotNull Consumer<String, String> registerKafkaConsumer() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                kafkaContainer.getBootstrapServers(), "testGroup", "true");
        consumerProps.put("auto.offset.reset", "earliest");
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer()
        );

        return cf.createConsumer();
    }

    @Test
    @Order(1)
    public void createUser_with_ADMIN_Role_UserCreated_SendToKafka() throws Exception {
        //test data
        String username = "lashabolga";
        String password = "TestPassword1!";
        String email = "test@test.com";
        Role adminRole = Role.ADMIN;

        //register kafka consumer
        Consumer<String, String> consumer = registerKafkaConsumer();
        consumer.subscribe(Collections.singletonList(KAFKA_TOPIC));

        ResultActions result = mockMvc.perform(MockMvcRequestBuilders.post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                        new UserRequest(
                                username,
                                email,
                                password,
                                Set.of(adminRole))))
        );

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles").value(adminRole.name()));

        //kafka check
        ConsumerRecord<String, String> record = getLatestRecordForTopic(consumer, KAFKA_TOPIC);
        UserEvent userEvent = objectMapper.readValue(record.value(), UserEvent.class);
        assertThat(userEvent.getUsername()).isEqualTo(username);
        assertThat(userEvent.getEmail()).isEqualTo(email);
        assertThat(userEvent.getRoles()).isEqualTo(Set.of(adminRole));
        assertThat(userEvent.getEventType()).isEqualTo(EventType.USER_CREATED);

        System.out.println("createUser_with_ADMIN_Role_UserCreated_SendToKafka passed successfully");
    }

    @Test
    @Order(2)
    public void createUser_with_InvalidParameter_BadRequest() throws Exception {
        //test data
        String username = null;
        String password = "testpassword";
        String email = "test.test.com";
        Role adminRole = Role.ADMIN;

        ResultActions result = mockMvc.perform(MockMvcRequestBuilders.post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                        new UserRequest(
                                username,
                                email,
                                password,
                                Set.of(adminRole))))
        );

        result.andExpect(status().isBadRequest());
        System.out.println("createUser_with_InvalidParameter_BadRequest passed successfully");
    }

    @Test
    @Order(3)
    public void createUser_with_DuplicatedUsername_CONFLICT() throws Exception {

        String username = "lashabolga";
        String password = "TestPassword1!";
        String email = "test@test.com";
        Role adminRole = Role.ADMIN;

        userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(password)
                .build());


        ResultActions result = mockMvc.perform(MockMvcRequestBuilders.post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                        new UserRequest(
                                username,
                                email,
                                password,
                                Set.of(adminRole))))
        );

        result.andExpect(status().isConflict())
                .andExpect(error ->
                        Assertions.assertInstanceOf(UserAlreadyExistsException.class, error.getResolvedException()));
        System.out.println("createUser_with_DuplicatedUsername_CONFLICT passed successfully");
    }

    @Test
    @Order(4)
    public void getUserByADMIN_ROLE_ReturnUser() throws Exception {
        String username = "testtest1";
        String password = "TestPassword1!";
        String email = "test1@test.com";
        Role adminRole = Role.ADMIN;

        UserDetails userDetails = userService.createUser(new UserRequest(username, email, password, Set.of(adminRole)));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(username, password)))
                ).andExpect(status().isOk())
                .andReturn();
        JWTResponse jwt = objectMapper.readValue(mvcResult.getResponse().getContentAsByteArray(), JWTResponse.class);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/users/" + userDetails.getId())
                        .header("Authorization", "Bearer " + jwt.token())
                ).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").value(userDetails.getId()));

        //check if cached data
        UserDetails cachedUser = (UserDetails) cacheManager.getCache(USER_CACHE).get(userDetails.getId()).get();
        Assertions.assertEquals(userDetails, cachedUser);

        System.out.println("getUserByADMIN_ROLE_ReturnUser passed successfully");
    }

    @Test
    @Order(5)
    public void getUserByUSER_ROLE_forOtherUser_FORBIDDEN() throws Exception {
        String username = "testtest1";
        String password = "TestPassword1!";
        String email = "test1@test.com";
        Role userRole = Role.USER;

        UserDetails userDetails = userService.createUser(new UserRequest(username, email, password, Set.of(userRole)));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(username, password)))
                ).andExpect(status().isOk())
                .andReturn();
        JWTResponse jwt = objectMapper.readValue(mvcResult.getResponse().getContentAsByteArray(), JWTResponse.class);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/users/" + (userDetails.getId()+ 1))
                        .header("Authorization", "Bearer " + jwt.token())
                ).andExpect(status().isForbidden());

        System.out.println("getUserByUSER_ROLE_forOtherUser_FORBIDDEN passed successfully");
    }

    @Test
    @Order(5)
    public void getUserByUSER_ROLE_WITH_OWN_ID_returnUser() throws Exception {
        String username = "testtest1";
        String password = "TestPassword1!";
        String email = "test1@test.com";
        Role userRole = Role.USER;

        UserDetails userDetails = userService.createUser(new UserRequest(username, email, password, Set.of(userRole)));

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(username, password)))
                ).andExpect(status().isOk())
                .andReturn();
        JWTResponse jwt = objectMapper.readValue(mvcResult.getResponse().getContentAsByteArray(), JWTResponse.class);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/users/" + userDetails.getId())
                        .header("Authorization", "Bearer " + jwt.token())
                ).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").value(userDetails.getId()));

        //check if cached data
        UserDetails cachedUser = (UserDetails) cacheManager.getCache(USER_CACHE).get(userDetails.getId()).get();
        Assertions.assertEquals(userDetails, cachedUser);

        System.out.println("getUserByUSER_ROLE_WITH_OWN_ID_returnUser passed successfully");
    }

    @Test
    @Order(4)
    public void updateUserByADMIN_ROLE_Success() throws Exception {
        String adminUsername = "testtest1";
        String adminPassword = "TestPassword1!";
        String adminEmail = "test1@test.com";
        Role adminRole = Role.ADMIN;

        String userUsername = "testtest12";
        String userPassword = "TestPassword12!";
        String userEmail = "test12@test.com";
        Role userRole = Role.USER;

        String changedUsername = "testtest123";
        String changedPassword = "TestPassword123!";
        String changedUserEmail = "test123@test.com";
        Role changedUserRole = Role.MODERATOR;

        //register kafka consumer
        Consumer<String, String> consumer = registerKafkaConsumer();
        consumer.subscribe(Collections.singletonList(KAFKA_TOPIC));

        UserDetails adminUser = userService.createUser(new UserRequest(adminUsername, adminEmail, adminPassword, Set.of(adminRole)));
        UserDetails user = userService.createUser(new UserRequest(userUsername, userEmail, userPassword, Set.of(userRole)));


        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(adminUsername, adminPassword)))
                ).andExpect(status().isOk())
                .andReturn();
        JWTResponse jwt = objectMapper.readValue(mvcResult.getResponse().getContentAsByteArray(), JWTResponse.class);

        mockMvc.perform(MockMvcRequestBuilders.put("/api/users/" + user.getId())
                        .header("Authorization", "Bearer " + jwt.token())
                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(new UserRequest(changedUsername, changedUserEmail, changedPassword, Set.of(changedUserRole))))
                ).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(changedUsername));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new LoginRequest(changedUsername, changedPassword)))
        ).andExpect(status().isOk());

        //check if evict cached data
        Cache.ValueWrapper cachedUser = cacheManager.getCache(USER_CACHE).get(user.getId());
        Assertions.assertNull(cachedUser);

        ConsumerRecord<String, String> record = getLatestRecordForTopic(consumer, KAFKA_TOPIC);
        UserEvent userEvent = objectMapper.readValue(record.value(), UserEvent.class);
        Assertions.assertEquals(changedUsername, userEvent.getUsername());
        Assertions.assertEquals(userEmail, userEvent.getEmail());
        Assertions.assertEquals(changedUserEmail, userEvent.getEmail());
        Assertions.assertEquals(changedUserRole, userEvent.getRoles().iterator().next());
        Assertions.assertEquals(EventType.USER_UPDATED, userEvent.getEventType());

        System.out.println("getUserByADMIN_ROLE_ReturnUser passed successfully");
    }

}
