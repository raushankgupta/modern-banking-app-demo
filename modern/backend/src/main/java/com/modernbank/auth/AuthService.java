package com.modernbank.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.modernbank.error.AuthenticationFailedException;
import com.modernbank.error.ValidationException;
import com.modernbank.security.TokenService;

/** Employee login: ACTIVE employee + BCrypt password check, as the legacy LoginFrame did with plaintext. */
@Service
public class AuthService {

    public record LoginResult(String token, Instant expiresAt, String employeeName, String username) {
    }

    private record Employee(long id, String username, String passwordHash, String name) {
    }

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    /** Checked for unknown users so that response time does not reveal whether a username exists. */
    private final String dummyHash;

    public AuthService(JdbcClient jdbc, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("unknown-user-timing-equalizer");
    }

    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new ValidationException("Please enter the username.", "username");
        }
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Please enter the password.", "password");
        }

        Optional<Employee> employee = jdbc.sql(
                "SELECT EMPLOYEE_ID, USERNAME, PASSWORD, EMPLOYEE_NAME FROM EMPLOYEES "
                        + "WHERE USERNAME = :username AND STATUS = 'ACTIVE'")
                .param("username", username.trim())
                .query((rs, n) -> new Employee(rs.getLong("EMPLOYEE_ID"), rs.getString("USERNAME"),
                        rs.getString("PASSWORD"), rs.getString("EMPLOYEE_NAME")))
                .optional();

        if (employee.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw new AuthenticationFailedException();
        }
        Employee e = employee.get();
        if (!isBcryptHash(e.passwordHash()) || !passwordEncoder.matches(password, e.passwordHash())) {
            throw new AuthenticationFailedException();
        }

        TokenService.IssuedToken token = tokenService.issue(e.id(), e.username(), e.name());
        return new LoginResult(token.token(), token.expiresAt(), e.name(), e.username());
    }

    /** Anything else (for example an un-hashed legacy value or an unreplaced placeholder) never authenticates. */
    private static boolean isBcryptHash(String value) {
        return value != null && value.startsWith("$2") && value.length() == 60;
    }
}
