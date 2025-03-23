package ge.croco.user.model;

import ge.croco.user.annotation.ValidPassword;
import ge.croco.user.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UserRequest(@NotEmpty String username,
                          @Email String email,
                          @ValidPassword String password,
                          Set<Role> roles)
{}
