package ge.croco.user.service;

import ge.croco.user.model.UpdateMeRequest;
import ge.croco.user.model.UserDetails;
import ge.croco.user.model.UserRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService
{
    UserDetails createUser(UserRequest user);

    UserDetails updateUser(Long id, UserRequest userRequest);

    void deleteUser(Long id);

    List<UserDetails> getUsers(Pageable pageable);

    UserDetails getUser(Long id);

    UserDetails updateMe(Long id, UpdateMeRequest user);
}
