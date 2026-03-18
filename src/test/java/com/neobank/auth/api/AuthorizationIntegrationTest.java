package com.neobank.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.auth.api.dto.LoginRequest;
import com.neobank.auth.api.dto.RegisterRequest;
import com.neobank.auth.domain.RoleEntity;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.repository.RoleRepository;
import com.neobank.auth.repository.UserRepository;
import com.neobank.accounts.repository.AccountRepository;
import com.neobank.audit.repository.AuditEventRepository;
import com.neobank.ledger.repository.LedgerEntryRepository;
import com.neobank.ledger.repository.LedgerTransactionRepository;
import com.neobank.risk.repository.RiskEvaluationRepository;
import com.neobank.transfers.api.dto.ReverseTransferRequest;
import com.neobank.transfers.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authorization behavior.
 * Tests verify correct 401/403 responses and role-based access control.
 *
 * These tests require:
 * - PostgreSQL database running
 * - Flyway migrations applied
 * - Redis (if enabled in config)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthorizationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private RiskEvaluationRepository riskEvaluationRepository;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();

        riskEvaluationRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        ledgerTransactionRepository.deleteAll();
        transferRepository.deleteAll();
        accountRepository.deleteAll();
        auditEventRepository.deleteAll();
        userRepository.deleteAll();
        ensureRolesSeeded();

        // Register and get token for a regular user
        RegisterRequest userRequest = new RegisterRequest("user@neobank.com", "password123");
        MvcResult userRegisterResult = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String userResponse = userRegisterResult.getResponse().getContentAsString();
        userToken = objectMapper.readTree(userResponse).get("accessToken").asText();

        // Create admin user manually
        RoleEntity adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not found"));

        UserEntity adminUser = new UserEntity();
        adminUser.setEmail("admin@neobank.com");
        adminUser.setPasswordHash(passwordEncoder.encode("adminpass123"));
        adminUser.setEnabled(true);
        adminUser.setRoles(Set.of(adminRole));
        userRepository.save(adminUser);

        // Login as admin
        LoginRequest adminLoginRequest = new LoginRequest("admin@neobank.com", "adminpass123");
        MvcResult adminLoginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLoginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String adminResponse = adminLoginResult.getResponse().getContentAsString();
        adminToken = objectMapper.readTree(adminResponse).get("accessToken").asText();
    }

    private void ensureRolesSeeded() {
        if (roleRepository.findByName("ROLE_USER").isEmpty()) {
            RoleEntity roleUser = new RoleEntity();
            roleUser.setName("ROLE_USER");
            roleRepository.save(roleUser);
        }

        if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
            RoleEntity roleAdmin = new RoleEntity();
            roleAdmin.setName("ROLE_ADMIN");
            roleRepository.save(roleAdmin);
        }
    }

    // ==================== PUBLIC ENDPOINTS ====================

    @Test
    void testPublicEndpointRegisterWithoutToken() throws Exception {
        RegisterRequest request = new RegisterRequest("newuser@neobank.com", "password123");
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void testPublicEndpointLoginWithoutToken() throws Exception {
        LoginRequest request = new LoginRequest("user@neobank.com", "password123");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testPublicDocumentationEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    // ==================== AUTHENTICATED ENDPOINTS ====================

    @Test
    void testAuthenticatedEndpointGetMeWithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAuthenticatedEndpointGetMeWithInvalidToken_Returns401() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer invalid_token_xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAuthenticatedEndpointGetMeWithValidToken_Returns200() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@neobank.com"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    void testAuthenticatedEndpointGetMeReturnsUserRoles() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("user@neobank.com"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.roles.length()").value(1));
    }

    // ==================== ADMIN-ONLY ENDPOINTS ====================

    @Test
    void testAdminEndpointWithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/admin/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAdminEndpointWithUserToken_Returns403() throws Exception {
        mockMvc.perform(get("/admin/health")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdminEndpointWithAdminToken_Returns200() throws Exception {
        mockMvc.perform(get("/admin/health")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Admin panel is operational"));
    }

    @Test
    void testAdminEndpointWithInvalidToken_Returns401() throws Exception {
        mockMvc.perform(get("/admin/health")
                .header("Authorization", "Bearer invalid_token"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== ROLE VERIFICATION ====================

    @Test
    void testAdminUserHasAdminRoleInProfile() throws Exception {
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@neobank.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
    }

    // ==================== OBSERVABILITY ACCESS CONTROL ====================

    @Test
    void testActuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void testActuatorMetricsRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void testAuditEndpointRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/audit/events")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/audit/events")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void testCorrelationIdHeaderIsGeneratedAndPropagated() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + userToken)
                        .header("X-Correlation-Id", "corr-test-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-test-123"));
    }

    @Test
    void testLoginSuccessAndFailureGenerateAuditEvents() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@neobank.com", "password123"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@neobank.com", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        boolean hasSuccess = auditEventRepository.findAll().stream()
                .anyMatch(event -> "LOGIN_SUCCESS".equals(event.getEventType()) && "SUCCESS".equals(event.getOutcome()));
        boolean hasFailure = auditEventRepository.findAll().stream()
                .anyMatch(event -> "LOGIN_FAILURE".equals(event.getEventType()) && "FAILURE".equals(event.getOutcome()));

        org.junit.jupiter.api.Assertions.assertTrue(hasSuccess);
        org.junit.jupiter.api.Assertions.assertTrue(hasFailure);
    }

    @Test
    void testTransferReversalEndpointAuthorization() throws Exception {
        UUID unknownTransferId = UUID.randomUUID();

        mockMvc.perform(post("/transfers/{transferId}/reverse", unknownTransferId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReverseTransferRequest("auth test"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/transfers/{transferId}/reverse", unknownTransferId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReverseTransferRequest("auth test"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/transfers/{transferId}/reverse", unknownTransferId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReverseTransferRequest("auth test"))))
                .andExpect(status().isNotFound());
    }
}
