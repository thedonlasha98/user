package ge.croco.user.enums;

public enum Role {
    ADMIN,
    USER,
    MODERATOR;

    public String getName() {
        return "ROLE_" + this;
    }
}
