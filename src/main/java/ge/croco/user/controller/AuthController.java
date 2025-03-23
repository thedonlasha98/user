package ge.croco.user.controller;

import ge.croco.user.model.JWTResponse;
import ge.croco.user.model.LoginRequest;
import ge.croco.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public JWTResponse login(@RequestBody LoginRequest login) {
        return authService.login(login.username(), login.password());
    }
}
