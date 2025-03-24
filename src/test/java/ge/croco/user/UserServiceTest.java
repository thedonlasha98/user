package ge.croco.user;

import ge.croco.user.domain.User;
import ge.croco.user.enums.Role;
import ge.croco.user.exception.UserAlreadyExistsException;
import ge.croco.user.model.UpdateMeRequest;
import ge.croco.user.model.UserRequest;
import ge.croco.user.repository.UserRepository;
import ge.croco.user.service.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void createUserWithExistingEmail_ThrowsException() {
        UserRequest userRequest = new UserRequest(
                "lashabolga",
                "lasha@gmail.com",
                "lasha111",
                Set.of(Role.ADMIN)
        );
        //Mock existsByEmail
        when(userRepository.existsByEmail(userRequest.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(userRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("User " + userRequest.email() + " already exists");
    }

    @Test
    void updateMeWithExistingUsername_ThrowsException() {
        Long userId = 1L;
        UpdateMeRequest updateMeRequest = new UpdateMeRequest(
                "lashabolga",
                "lasha@gmail.com",
                "lasha111"
        );

        when(userRepository.findById(userId)).thenReturn(
                Optional.of(User.builder()
                        .id(userId)
                        .username("lashabolga1")
                        .email("lasha@gmail.com")
                        .build())
        );

        when(userRepository.existsByUsername(updateMeRequest.username())).thenReturn(true);

        assertThatThrownBy(() -> userService.updateMe(1L, updateMeRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("User " + updateMeRequest.username() + " already exists");
    }
}