package ge.croco.user.model;

import ge.croco.user.annotation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

public record UpdateMeRequest(@NotEmpty String username,
                              @Email String email,
                              @ValidPassword String password) {
}
