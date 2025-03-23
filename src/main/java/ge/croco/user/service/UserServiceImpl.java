package ge.croco.user.service;

import ge.croco.user.domain.User;
import ge.croco.user.enums.EventType;
import ge.croco.user.exception.UserAlreadyExistsException;
import ge.croco.user.exception.UserNotFoundException;
import ge.croco.user.model.UpdateMeRequest;
import ge.croco.user.model.UserDetails;
import ge.croco.user.model.UserEvent;
import ge.croco.user.model.UserRequest;
import ge.croco.user.model.mapper.UserMapper;
import ge.croco.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String USERS_CACHE = "users";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public UserDetails createUser(UserRequest userRequest) {
        if (userRepository.existsByUsername(userRequest.username())) {
            throw new UserAlreadyExistsException(userRequest.username());
        }
        if (userRepository.existsByEmail(userRequest.email())) {
            throw new UserAlreadyExistsException(userRequest.email());
        }
        User user = UserMapper.toEntity(userRequest);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return saveUser(EventType.USER_CREATED, user);
    }

    @Override
    @CacheEvict(value = USERS_CACHE, key = "#id")
    public UserDetails updateUser(Long id, UserRequest userRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        checkUpdateUsername(user.getUsername(), userRequest.username());
        checkUpdateEmail(user.getEmail(), userRequest.email());

        user.setUsername(userRequest.username());
        user.setEmail(userRequest.email());
        user.setPassword(passwordEncoder.encode(userRequest.password()));
        user.setRoles(userRequest.roles());

        return saveUser(EventType.USER_UPDATED, user);
    }

    @Override
    @CacheEvict(value = USERS_CACHE, key = "#id")
    public void deleteUser(Long id) {
        userRepository.findById(id).ifPresent(user -> {
            userRepository.delete(user);
            sendUserEvent(EventType.USER_DELETED, UserMapper.toDetails(user));
        });
    }

    @Override
    public List<UserDetails> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .stream()
                .map(UserMapper::toDetails)
                .toList();
    }

    @Override
    @Cacheable(value = USERS_CACHE, key = "#id")
    public UserDetails getUser(Long id) {
        System.out.println("getUser: " + id);
        return userRepository.findById(id)
                .map(UserMapper::toDetails)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    @CacheEvict(value = USERS_CACHE, key = "#id")
    public UserDetails updateMe(Long id, UpdateMeRequest userMe) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));

        checkUpdateUsername(user.getUsername(), userMe.username());
        checkUpdateEmail(user.getEmail(), userMe.email());

        user.setUsername(userMe.username());
        user.setEmail(userMe.email());
        user.setPassword(passwordEncoder.encode(userMe.password()));

        return saveUser(EventType.USER_UPDATED, user);
    }

    private UserDetails saveUser(EventType eventType, User user) {
        userRepository.save(user);
        UserDetails userDetails = UserMapper.toDetails(user);
        sendUserEvent(eventType, userDetails);

        return userDetails;
    }

    private void sendUserEvent(EventType eventType, UserDetails user) {
        UserEvent userEvent = new UserEvent(
                eventType,
                Instant.now(),
                user
        );
        log.info("Sending user event: {}", userEvent);
        try {
            kafkaTemplate.send("users", userEvent.getUsername(), userEvent).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to send user event: {}", userEvent, e);
        }
    }

    private void checkUpdateUsername(String oldUsername, String newUsername  ) {
        if (!oldUsername.equals(newUsername)) {
            if (!userRepository.existsByUsername(newUsername)) {
                throw new UserAlreadyExistsException(newUsername);
            }
        }
    }

    private void checkUpdateEmail(String oldEmail, String newEmail  ) {
        if (!oldEmail.equals(newEmail)) {
            if (!userRepository.existsByUsername(newEmail)) {
                throw new UserAlreadyExistsException(newEmail);
            }
        }
    }
}
