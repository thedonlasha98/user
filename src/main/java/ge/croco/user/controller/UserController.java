package ge.croco.user.controller;

import ge.croco.user.domain.CustomUserDetails;
import ge.croco.user.model.UpdateMeRequest;
import ge.croco.user.model.UserDetails;
import ge.croco.user.model.UserRequest;
import ge.croco.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(value = "hasAnyRole('ADMIN','MODERATOR')")
    public List<UserDetails> getUsers(Pageable pageable) {
        return userService.getUsers(pageable);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(value = "hasRole('ADMIN') or principal.id == #id")
    public UserDetails getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDetails createUser(@RequestBody @Valid UserRequest user) {
        return userService.createUser(user);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(value = "hasRole('ADMIN')")
    public UserDetails updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest user) {
        return userService.updateUser(id, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(value = "hasRole('ADMIN')")
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }

    @PutMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public UserDetails updateMe(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @RequestBody @Valid UpdateMeRequest user) {
        return userService.updateMe(userDetails.getId(), user);
    }

}
