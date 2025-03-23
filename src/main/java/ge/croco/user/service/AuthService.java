package ge.croco.user.service;

import ge.croco.user.model.JWTResponse;

public interface AuthService {
    JWTResponse login(String username, String password);
}
