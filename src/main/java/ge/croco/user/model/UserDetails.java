package ge.croco.user.model;

import ge.croco.user.enums.Role;
import lombok.*;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class UserDetails {
    private Long id;
    private String username;
    private String email;
    private Set<Role> roles;
}
