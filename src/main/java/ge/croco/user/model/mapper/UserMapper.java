package ge.croco.user.model.mapper;

import ge.croco.user.domain.User;
import ge.croco.user.model.UserDetails;
import ge.croco.user.model.UserRequest;

public class UserMapper {

    public static UserDetails toDetails(User user) {
        return UserDetails.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles())
                .build();
    }

    public static User toEntity(UserRequest request) {
        return User.builder()
                .username(request.username())
                .email(request.email())
                .password(request.password())
                .roles(request.roles())
                .build();
    }
}
