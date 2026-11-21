package com.modernbank.auth;

import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record LoginRequest(String username, String password) {
        /** Keeps the password out of logs and exception messages. */
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=***]";
        }
    }

    public record LoginResponse(String token, String tokenType, Instant expiresAt, String employeeName,
            String username) {
    }

    public record MeResponse(String username, String employeeName) {
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return new LoginResponse(result.token(), "Bearer", result.expiresAt(), result.employeeName(),
                result.username());
    }

    /** Returns the signed-in employee; lets the frontend restore a session from a stored token. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(jwt.getSubject(), jwt.getClaimAsString("name"));
    }
}
