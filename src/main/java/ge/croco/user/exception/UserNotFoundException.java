package ge.croco.user.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long userId) {
        super("User with id " + userId + " not found!");
    }

    public UserNotFoundException(String user) {
        super("User : " + user + " not found!");
    }
}
