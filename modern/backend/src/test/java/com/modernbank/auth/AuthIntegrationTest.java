package com.modernbank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernbank.TestDatabase;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthIntegrationTest {

    private static final String TEST_SECRET = "test-only-jwt-secret-0123456789-abcdefghij";

    private static Path dbFile;

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void createDatabase() throws IOException, SQLException {
        dbFile = TestDatabase.create(tempDir);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("bank.db.path", () -> dbFile.toString());
        registry.add("bank.jwt.secret", () -> TEST_SECRET);
        registry.add("bank.jwt.expiry-minutes", () -> "60");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    private MvcResult login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private String adminToken() throws Exception {
        MvcResult result = login("admin", "admin123");
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    @Test
    void adminLoginReturns200AndToken() throws Exception {
        MvcResult result = login("admin", "admin123");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("token").asText()).isNotBlank();
        assertThat(json.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(json.get("employeeName").asText()).isEqualTo("Branch Administrator");
        assertThat(json.get("username").asText()).isEqualTo("admin");
        assertThat(Instant.parse(json.get("expiresAt").asText())).isAfter(Instant.now().plus(55, ChronoUnit.MINUTES));
    }

    @Test
    void seededAdminPasswordIsStoredAsBcryptNotPlaintext() {
        String stored = jdbc.sql("SELECT PASSWORD FROM EMPLOYEES WHERE USERNAME = 'admin'")
                .query(String.class).single();

        assertThat(stored).isNotEqualTo("admin123").hasSize(60).startsWith("$2");
        assertThat(passwordEncoder.matches("admin123", stored)).isTrue();
    }

    @Test
    void wrongPasswordReturns401WithGenericMessage() throws Exception {
        MvcResult result = login("admin", "wrong-password");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("code").asText()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(json.get("message").asText()).isEqualTo("Invalid username or password.");
    }

    @Test
    void unknownUserReturnsTheSame401AsWrongPassword() throws Exception {
        MvcResult wrongPassword = login("admin", "wrong-password");
        MvcResult unknownUser = login("nobody", "admin123");

        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownUser.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    @Test
    void inactiveEmployeeReturns401EvenWithCorrectPassword() throws Exception {
        jdbc.sql("INSERT INTO EMPLOYEES (USERNAME, PASSWORD, EMPLOYEE_NAME, STATUS) "
                + "VALUES ('retired', :hash, 'Retired Teller', 'INACTIVE')")
                .param("hash", passwordEncoder.encode("retired123")).update();

        MvcResult result = login("retired", "retired123");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).contains("Invalid username or password.");
    }

    @Test
    void employeeWithNonBcryptStoredPasswordCannotLogIn() throws Exception {
        jdbc.sql("INSERT INTO EMPLOYEES (USERNAME, PASSWORD, EMPLOYEE_NAME, STATUS) "
                + "VALUES ('legacyuser', 'plaintext1', 'Legacy User', 'ACTIVE')").update();

        assertThat(login("legacyuser", "plaintext1").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void blankCredentialsReturn400() throws Exception {
        assertThat(login("", "admin123").getResponse().getStatus()).isEqualTo(400);
        assertThat(login("admin", "").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void protectedEndpointWithValidTokenReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.employeeName").value("Branch Administrator"));
    }

    @Test
    void garbageTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tokenSignedWithAnotherKeyReturns401() throws Exception {
        String forged = tokenSignedWith("a-completely-different-secret-key-0123456789", Instant.now().plus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String expired = tokenSignedWith(TEST_SECRET, Instant.now().minus(10, ChronoUnit.MINUTES));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void healthEndpointStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void passwordIsNeverReturnedOrLogged(CapturedOutput output) throws Exception {
        MvcResult ok = login("admin", "admin123");
        MvcResult bad = login("admin", "super-secret-wrong-pw");

        String storedHash = jdbc.sql("SELECT PASSWORD FROM EMPLOYEES WHERE USERNAME = 'admin'")
                .query(String.class).single();
        for (MvcResult r : new MvcResult[] { ok, bad }) {
            String body = r.getResponse().getContentAsString();
            assertThat(body).doesNotContain("admin123").doesNotContain("super-secret-wrong-pw")
                    .doesNotContain(storedHash).doesNotContainIgnoringCase("password\":");
        }
        assertThat(output.getAll()).doesNotContain("admin123").doesNotContain("super-secret-wrong-pw")
                .doesNotContain(storedHash);
    }

    private static String tokenSignedWith(String secret, Instant expiresAt) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant issuedAt = expiresAt.minus(1, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("modern-banking").subject("admin")
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("name", "Branch Administrator").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
