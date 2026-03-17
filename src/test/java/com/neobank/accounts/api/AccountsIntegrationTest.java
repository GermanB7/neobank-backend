package com.neobank.accounts.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.accounts.api.dto.CreateAccountRequest;
import com.neobank.accounts.domain.AccountType;
import com.neobank.accounts.repository.AccountRepository;
import com.neobank.audit.domain.AuditEventEntity;
import com.neobank.audit.repository.AuditEventRepository;
import com.neobank.auth.api.dto.LoginRequest;
import com.neobank.auth.api.dto.RegisterRequest;
import com.neobank.auth.domain.RoleEntity;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.repository.RoleRepository;
import com.neobank.auth.repository.UserRepository;
import com.neobank.ledger.repository.LedgerEntryRepository;
import com.neobank.ledger.repository.LedgerTransactionRepository;
import com.neobank.risk.repository.RiskEvaluationRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountsIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private RiskEvaluationRepository riskEvaluationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String ownerToken;
    private String otherUserToken;
    private String adminToken;
    private UUID ownerId;

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
        auditEventRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        ensureRolesSeeded();

        ownerToken = registerAndGetToken("owner@neobank.com", "password123");
        otherUserToken = registerAndGetToken("other@neobank.com", "password123");
        adminToken = createAdminAndGetToken("admin@neobank.com", "adminpass123");

        ownerId = getCurrentUserId(ownerToken);
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

    @Test
    void createAccountSetsInitialStateAndOwner() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(AccountType.CHECKING, "USD");

        MvcResult result = mockMvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.type").value("CHECKING"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andReturn();

        String accountId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        AuditEventEntity accountCreatedEvent = auditEventRepository.findAll().stream()
                .filter(event -> "ACCOUNT_CREATED".equals(event.getEventType()) && accountId.equals(event.getResourceId()))
                .findFirst()
                .orElseThrow();

        assertEquals("SUCCESS", accountCreatedEvent.getOutcome());
    }

    @Test
    void myAccountsReturnsOnlyAuthenticatedUserAccounts() throws Exception {
        UUID ownerAccountA = createAccountAndGetId(ownerToken, AccountType.CHECKING, "USD");
        UUID ownerAccountB = createAccountAndGetId(ownerToken, AccountType.SAVINGS, "EUR");
        UUID otherAccount = createAccountAndGetId(otherUserToken, AccountType.CHECKING, "USD");

        MvcResult result = mockMvc.perform(get("/accounts/me")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode accounts = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(2, accounts.size());
        for (JsonNode account : accounts) {
            assertEquals(ownerId.toString(), account.get("ownerId").asText());
            assertTrue(!account.get("id").asText().equals(otherAccount.toString()));
        }
        assertTrue(containsAccount(accounts, ownerAccountA));
        assertTrue(containsAccount(accounts, ownerAccountB));
    }

    @Test
    void getAccountByIdAllowsOwner() throws Exception {
        UUID accountId = createAccountAndGetId(ownerToken, AccountType.CHECKING, "USD");

        mockMvc.perform(get("/accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()));
    }

    @Test
    void getAccountByIdAllowsAdminOverride() throws Exception {
        UUID accountId = createAccountAndGetId(ownerToken, AccountType.CHECKING, "USD");

        mockMvc.perform(get("/accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()));
    }

    @Test
    void getAccountByIdRejectsDifferentNonAdminUser() throws Exception {
        UUID accountId = createAccountAndGetId(ownerToken, AccountType.CHECKING, "USD");

        mockMvc.perform(get("/accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBalanceAllowsOwner() throws Exception {
        UUID accountId = createAccountAndGetId(ownerToken, AccountType.CHECKING, "USD");

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getBalanceAllowsAdminOverride() throws Exception {
        UUID accountId = createAccountAndGetId(ownerToken, AccountType.SAVINGS, "EUR");

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void getBalanceRejectsDifferentNonAdminUser() throws Exception {
        UUID accountId = createAccountAndGetId(ownerToken, AccountType.CHECKING, "USD");

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountEndpointsWithoutTokenReturn401() throws Exception {
        UUID anyAccountId = UUID.randomUUID();

        mockMvc.perform(get("/accounts/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/accounts/{accountId}", anyAccountId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/accounts/{accountId}/balance", anyAccountId))
                .andExpect(status().isUnauthorized());
    }

    private String registerAndGetToken(String email, String password) throws Exception {
        RegisterRequest request = new RegisterRequest(email, password);

        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private String createAdminAndGetToken(String email, String password) throws Exception {
        RoleEntity adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not found"));

        UserEntity adminUser = new UserEntity();
        adminUser.setEmail(email);
        adminUser.setPasswordHash(passwordEncoder.encode(password));
        adminUser.setEnabled(true);
        adminUser.setRoles(Set.of(adminRole));
        userRepository.save(adminUser);

        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private UUID getCurrentUserId(String token) throws Exception {
        MvcResult meResult = mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String idText = objectMapper.readTree(meResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        return UUID.fromString(idText);
    }

    private UUID createAccountAndGetId(String token, AccountType type, String currency) throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(type, currency);

        MvcResult createResult = mockMvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String idText = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        return UUID.fromString(idText);
    }

    private boolean containsAccount(JsonNode accounts, UUID accountId) {
        for (JsonNode account : accounts) {
            if (accountId.toString().equals(account.get("id").asText())) {
                return true;
            }
        }
        return false;
    }
}
