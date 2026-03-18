package com.neobank.reconciliation.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.accounts.api.dto.CreateAccountRequest;
import com.neobank.accounts.domain.AccountEntity;
import com.neobank.accounts.domain.AccountType;
import com.neobank.accounts.repository.AccountRepository;
import com.neobank.audit.repository.AuditEventRepository;
import com.neobank.auth.api.dto.LoginRequest;
import com.neobank.auth.api.dto.RegisterRequest;
import com.neobank.auth.domain.RoleEntity;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.repository.RoleRepository;
import com.neobank.auth.repository.UserRepository;
import com.neobank.ledger.repository.LedgerEntryRepository;
import com.neobank.ledger.repository.LedgerTransactionRepository;
import com.neobank.reconciliation.domain.DiscrepancyType;
import com.neobank.reconciliation.repository.ReconciliationDiscrepancyRepository;
import com.neobank.reconciliation.repository.ReconciliationReportRepository;
import com.neobank.risk.repository.RiskEvaluationRepository;
import com.neobank.transfers.domain.TransferEntity;
import com.neobank.transfers.domain.TransferKind;
import com.neobank.transfers.domain.TransferStatus;
import com.neobank.transfers.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
class ReconciliationIntegrationTest {

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
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private RiskEvaluationRepository riskEvaluationRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private ReconciliationReportRepository reconciliationReportRepository;

    @Autowired
    private ReconciliationDiscrepancyRepository reconciliationDiscrepancyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken;
    private String otherToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();

        reconciliationDiscrepancyRepository.deleteAll();
        reconciliationReportRepository.deleteAll();
        riskEvaluationRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        ledgerTransactionRepository.deleteAll();
        transferRepository.deleteAllInBatch();
        accountRepository.deleteAll();
        auditEventRepository.deleteAll();
        userRepository.deleteAll();

        ensureRolesSeeded();

        userToken = registerAndGetToken("user@neobank.com", "password123");
        otherToken = registerAndGetToken("other@neobank.com", "password123");
        adminToken = createAdminAndGetToken("admin@neobank.com", "adminpass123");
    }

    @Test
    void healthyScenarioPersistsCompletedReportWithoutDiscrepancies() throws Exception {
        createAccountAndGetId(userToken, "USD");
        createAccountAndGetId(otherToken, "USD");

        MvcResult runResult = mockMvc.perform(post("/reconciliation/run")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.discrepanciesFound").value(0))
                .andReturn();

        UUID reportId = UUID.fromString(objectMapper.readTree(runResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        assertEquals(1, reconciliationReportRepository.count());
        assertEquals(0, reconciliationDiscrepancyRepository.count());

        mockMvc.perform(get("/reconciliation/reports")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reportId.toString()));

        mockMvc.perform(get("/reconciliation/reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId.toString()));

        mockMvc.perform(get("/reconciliation/reports/{reportId}/discrepancies", reportId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void detectsAccountVsLedgerBalanceMismatch() throws Exception {
        UUID accountId = createAccountAndGetId(userToken, "USD");
        setBalance(accountId, new BigDecimal("25.00"));

        UUID reportId = runAndGetReportId();

        List<DiscrepancyType> discrepancyTypes = reconciliationDiscrepancyRepository
                .findByReportIdOrderByCreatedAtAsc(reportId)
                .stream()
                .map(d -> d.getType())
                .toList();

        assertTrue(discrepancyTypes.contains(DiscrepancyType.ACCOUNT_LEDGER_BALANCE_MISMATCH));
    }

    @Test
    void detectsCompletedTransferWithoutLedger() throws Exception {
        UUID sourceAccountId = createAccountAndGetId(userToken, "USD");
        UUID targetAccountId = createAccountAndGetId(otherToken, "USD");
        UUID initiatorId = userRepository.findByEmailIgnoreCase("user@neobank.com").orElseThrow().getId();

        TransferEntity transfer = new TransferEntity();
        transfer.setSourceAccountId(sourceAccountId);
        transfer.setTargetAccountId(targetAccountId);
        transfer.setAmount(new BigDecimal("10.00"));
        transfer.setCurrency("USD");
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setKind(TransferKind.STANDARD);
        transfer.setOriginalTransferId(null);
        transfer.setInitiatedByUserId(initiatorId);
        transfer.setIdempotencyKey("reconciliation-missing-ledger");
        transfer.setProcessedAt(Instant.now());
        transferRepository.saveAndFlush(transfer);

        UUID reportId = runAndGetReportId();

        List<DiscrepancyType> discrepancyTypes = reconciliationDiscrepancyRepository
                .findByReportIdOrderByCreatedAtAsc(reportId)
                .stream()
                .map(d -> d.getType())
                .toList();

        assertTrue(discrepancyTypes.contains(DiscrepancyType.COMPLETED_TRANSFER_WITHOUT_LEDGER));
    }

    @Test
    void detectsReversalLinkInconsistencyAndMissingReversalLedger() throws Exception {
        UUID sourceAccountId = createAccountAndGetId(userToken, "USD");
        UUID targetAccountId = createAccountAndGetId(otherToken, "USD");
        UUID userId = userRepository.findByEmailIgnoreCase("user@neobank.com").orElseThrow().getId();
        UUID adminId = userRepository.findByEmailIgnoreCase("admin@neobank.com").orElseThrow().getId();

        TransferEntity originalWithoutReversal = new TransferEntity();
        originalWithoutReversal.setSourceAccountId(sourceAccountId);
        originalWithoutReversal.setTargetAccountId(targetAccountId);
        originalWithoutReversal.setAmount(new BigDecimal("5.00"));
        originalWithoutReversal.setCurrency("USD");
        originalWithoutReversal.setStatus(TransferStatus.REVERSED);
        originalWithoutReversal.setKind(TransferKind.STANDARD);
        originalWithoutReversal.setInitiatedByUserId(userId);
        originalWithoutReversal.setProcessedAt(Instant.now());
        transferRepository.saveAndFlush(originalWithoutReversal);

        TransferEntity originalNotReversed = new TransferEntity();
        originalNotReversed.setSourceAccountId(sourceAccountId);
        originalNotReversed.setTargetAccountId(targetAccountId);
        originalNotReversed.setAmount(new BigDecimal("7.00"));
        originalNotReversed.setCurrency("USD");
        originalNotReversed.setStatus(TransferStatus.COMPLETED);
        originalNotReversed.setKind(TransferKind.STANDARD);
        originalNotReversed.setInitiatedByUserId(userId);
        originalNotReversed.setProcessedAt(Instant.now());
        transferRepository.saveAndFlush(originalNotReversed);

        TransferEntity invalidReversal = new TransferEntity();
        invalidReversal.setSourceAccountId(targetAccountId);
        invalidReversal.setTargetAccountId(sourceAccountId);
        invalidReversal.setAmount(new BigDecimal("7.00"));
        invalidReversal.setCurrency("USD");
        invalidReversal.setStatus(TransferStatus.COMPLETED);
        invalidReversal.setKind(TransferKind.REVERSAL);
        invalidReversal.setOriginalTransferId(originalNotReversed.getId());
        invalidReversal.setInitiatedByUserId(adminId);
        invalidReversal.setProcessedAt(Instant.now());
        transferRepository.saveAndFlush(invalidReversal);

        UUID reportId = runAndGetReportId();

        List<DiscrepancyType> discrepancyTypes = reconciliationDiscrepancyRepository
                .findByReportIdOrderByCreatedAtAsc(reportId)
                .stream()
                .map(d -> d.getType())
                .toList();

        assertTrue(discrepancyTypes.contains(DiscrepancyType.REVERSAL_LINK_INCONSISTENCY));
        assertTrue(discrepancyTypes.contains(DiscrepancyType.REVERSAL_WITHOUT_LEDGER));
    }

    @Test
    void detectsDuplicateReversalWhenConstraintIsBypassedInTest() throws Exception {
        UUID sourceAccountId = createAccountAndGetId(userToken, "USD");
        UUID targetAccountId = createAccountAndGetId(otherToken, "USD");
        UUID userId = userRepository.findByEmailIgnoreCase("user@neobank.com").orElseThrow().getId();
        UUID adminId = userRepository.findByEmailIgnoreCase("admin@neobank.com").orElseThrow().getId();

        TransferEntity original = new TransferEntity();
        original.setSourceAccountId(sourceAccountId);
        original.setTargetAccountId(targetAccountId);
        original.setAmount(new BigDecimal("9.00"));
        original.setCurrency("USD");
        original.setStatus(TransferStatus.REVERSED);
        original.setKind(TransferKind.STANDARD);
        original.setInitiatedByUserId(userId);
        original.setProcessedAt(Instant.now());
        transferRepository.saveAndFlush(original);

        jdbcTemplate.execute("DROP INDEX IF EXISTS uq_transfers_single_reversal_per_original");

        try {
            TransferEntity reversalA = new TransferEntity();
            reversalA.setSourceAccountId(targetAccountId);
            reversalA.setTargetAccountId(sourceAccountId);
            reversalA.setAmount(new BigDecimal("9.00"));
            reversalA.setCurrency("USD");
            reversalA.setStatus(TransferStatus.COMPLETED);
            reversalA.setKind(TransferKind.REVERSAL);
            reversalA.setOriginalTransferId(original.getId());
            reversalA.setInitiatedByUserId(adminId);
            reversalA.setProcessedAt(Instant.now());
            transferRepository.saveAndFlush(reversalA);

            TransferEntity reversalB = new TransferEntity();
            reversalB.setSourceAccountId(targetAccountId);
            reversalB.setTargetAccountId(sourceAccountId);
            reversalB.setAmount(new BigDecimal("9.00"));
            reversalB.setCurrency("USD");
            reversalB.setStatus(TransferStatus.COMPLETED);
            reversalB.setKind(TransferKind.REVERSAL);
            reversalB.setOriginalTransferId(original.getId());
            reversalB.setInitiatedByUserId(adminId);
            reversalB.setProcessedAt(Instant.now());
            transferRepository.saveAndFlush(reversalB);

            UUID reportId = runAndGetReportId();

            List<DiscrepancyType> discrepancyTypes = reconciliationDiscrepancyRepository
                    .findByReportIdOrderByCreatedAtAsc(reportId)
                    .stream()
                    .map(d -> d.getType())
                    .toList();

            assertTrue(discrepancyTypes.contains(DiscrepancyType.DUPLICATE_REVERSAL));
        } finally {
            jdbcTemplate.execute("DELETE FROM transfers WHERE kind = 'REVERSAL'");
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_transfers_single_reversal_per_original ON transfers(original_transfer_id) WHERE kind = 'REVERSAL'");
        }
    }

    @Test
    void reconciliationEndpointsAreAdminOnly() throws Exception {
        mockMvc.perform(post("/reconciliation/run")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/reconciliation/reports")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/reconciliation/run"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/reconciliation/run")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    private UUID runAndGetReportId() throws Exception {
        MvcResult result = mockMvc.perform(post("/reconciliation/run")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(response.get("id").asText());
    }

    private void setBalance(UUID accountId, BigDecimal balance) {
        AccountEntity account = accountRepository.findById(accountId).orElseThrow();
        account.setBalance(balance);
        accountRepository.save(account);
    }

    private UUID createAccountAndGetId(String token, String currency) throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(AccountType.CHECKING, currency);

        MvcResult createResult = mockMvc.perform(post("/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText());
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
}

