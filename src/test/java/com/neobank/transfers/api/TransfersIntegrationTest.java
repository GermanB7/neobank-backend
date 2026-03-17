package com.neobank.transfers.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.accounts.api.dto.CreateAccountRequest;
import com.neobank.accounts.domain.AccountEntity;
import com.neobank.accounts.domain.AccountStatus;
import com.neobank.accounts.domain.AccountType;
import com.neobank.accounts.repository.AccountRepository;
import com.neobank.auth.api.dto.LoginRequest;
import com.neobank.auth.api.dto.RegisterRequest;
import com.neobank.auth.domain.RoleEntity;
import com.neobank.auth.domain.UserEntity;
import com.neobank.auth.repository.RoleRepository;
import com.neobank.auth.repository.UserRepository;
import com.neobank.ledger.domain.EntrySide;
import com.neobank.ledger.domain.LedgerEntryEntity;
import com.neobank.ledger.domain.LedgerTransactionEntity;
import com.neobank.ledger.repository.LedgerEntryRepository;
import com.neobank.ledger.repository.LedgerTransactionRepository;
import com.neobank.transfers.api.dto.CreateTransferRequest;
import com.neobank.transfers.domain.TransferEntity;
import com.neobank.transfers.domain.TransferStatus;
import com.neobank.transfers.repository.TransferRepository;
import com.neobank.risk.domain.RiskDecision;
import com.neobank.risk.domain.RiskEvaluationEntity;
import com.neobank.risk.repository.RiskEvaluationRepository;
import com.neobank.audit.domain.AuditEventEntity;
import com.neobank.audit.repository.AuditEventRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TransfersIntegrationTest {

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
    private TransferRepository transferRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RiskEvaluationRepository riskEvaluationRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private String ownerToken;
    private String otherUserToken;
    private String unrelatedUserToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();

        auditEventRepository.deleteAll();
        riskEvaluationRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        ledgerTransactionRepository.deleteAll();
        transferRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        ensureRolesSeeded();

        ownerToken = registerAndGetToken("owner@neobank.com", "password123");
        otherUserToken = registerAndGetToken("other@neobank.com", "password123");
        unrelatedUserToken = registerAndGetToken("unrelated@neobank.com", "password123");
        adminToken = createAdminAndGetToken("admin@neobank.com", "adminpass123");
    }

    @Test
    void createTransferSuccessUpdatesBalancesAndPersistsCompletedTransfer() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("150.00"));
        setBalance(target, new BigDecimal("25.00"));

        CreateTransferRequest request = new CreateTransferRequest(
                source,
                target,
                new BigDecimal("50.00"),
                "rent",
                "idem-success-1"
        );

        MvcResult createResult = mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(50.00))
                .andReturn();

        UUID transferId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        AccountEntity sourceAfter = accountRepository.findById(source).orElseThrow();
        AccountEntity targetAfter = accountRepository.findById(target).orElseThrow();
        assertEquals(0, sourceAfter.getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(0, targetAfter.getBalance().compareTo(new BigDecimal("75.00")));

        mockMvc.perform(get("/transfers/{transferId}", transferId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transferId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        RiskEvaluationEntity evaluation = riskEvaluationRepository.findTopByTransferIdOrderByCreatedAtDesc(transferId)
                .orElseThrow();
        assertEquals(RiskDecision.ALLOW, evaluation.getDecision());
        assertEquals("NONE", evaluation.getTriggeredRules());
        assertEquals(0, evaluation.getAmount().compareTo(new BigDecimal("50.00")));

        boolean hasCompletedAudit = auditEventRepository.findAll().stream()
                .anyMatch(event -> "TRANSFER_COMPLETED".equals(event.getEventType())
                        && transferId.toString().equals(event.getResourceId()));
        assertTrue(hasCompletedAudit);
    }

    @Test
    void createTransferRejectsInvalidBusinessRules() throws Exception {
        UUID ownerSource = createAccountAndGetId(ownerToken, "USD");
        UUID ownerUsdTarget = createAccountAndGetId(ownerToken, "USD");
        UUID ownerEurTarget = createAccountAndGetId(ownerToken, "EUR");
        UUID otherSource = createAccountAndGetId(otherUserToken, "USD");

        setBalance(ownerSource, new BigDecimal("10.00"));
        setBalance(ownerUsdTarget, new BigDecimal("0.00"));
        setBalance(ownerEurTarget, new BigDecimal("0.00"));
        setBalance(otherSource, new BigDecimal("200.00"));

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                ownerSource,
                                ownerSource,
                                new BigDecimal("1.00"),
                                null,
                                "idem-source-eq-target"
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                ownerSource,
                                ownerUsdTarget,
                                new BigDecimal("20.00"),
                                null,
                                "idem-insufficient"
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                otherSource,
                                ownerUsdTarget,
                                new BigDecimal("1.00"),
                                null,
                                "idem-owner-check"
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                ownerSource,
                                ownerEurTarget,
                                new BigDecimal("1.00"),
                                null,
                                "idem-currency"
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                ownerSource,
                                ownerUsdTarget,
                                new BigDecimal("0.00"),
                                null,
                                "idem-zero"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransferRejectsWhenAnyAccountIsNotActive() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("100.00"));
        setBalance(target, new BigDecimal("5.00"));

        AccountEntity targetEntity = accountRepository.findById(target).orElseThrow();
        targetEntity.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(targetEntity);

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                source,
                                target,
                                new BigDecimal("10.00"),
                                null,
                                "idem-status"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferEndpointsWithoutTokenReturn401() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                new BigDecimal("1.00"),
                                null,
                                null
                        ))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/transfers/{transferId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/transfers/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readAccessRulesAllowInitiatorAndAdminButRejectOtherUser() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("90.00"));

        UUID transferId = createTransferAndGetId(ownerToken, source, target, new BigDecimal("10.00"), "idem-read-1");

        mockMvc.perform(get("/transfers/{transferId}", transferId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/transfers/{transferId}", transferId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/transfers/{transferId}", transferId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void sameIdempotencyKeyDoesNotDuplicateTransferOrBalances() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("100.00"));

        CreateTransferRequest request = new CreateTransferRequest(
                source,
                target,
                new BigDecimal("15.00"),
                "invoice-77",
                "idem-retry-777"
        );

        MvcResult first = mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String firstTransferId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String secondTransferId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();
        assertEquals(firstTransferId, secondTransferId);
        assertEquals(1, transferRepository.count());

        AccountEntity sourceAfter = accountRepository.findById(source).orElseThrow();
        AccountEntity targetAfter = accountRepository.findById(target). orElseThrow();
        assertEquals(0, sourceAfter.getBalance().compareTo(new BigDecimal("85.00")));
        assertEquals(0, targetAfter.getBalance().compareTo(new BigDecimal("15.00")));
    }

    @Test
    void sameIdempotencyKeyWithNullReferenceDoesNotDuplicateOrFail() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("40.00"));

        CreateTransferRequest request = new CreateTransferRequest(
                source,
                target,
                new BigDecimal("10.00"),
                null,
                "idem-null-reference-1"
        );

        MvcResult first = mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String firstTransferId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String secondTransferId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

        assertEquals(firstTransferId, secondTransferId);
        assertEquals(1, transferRepository.count());

        AccountEntity sourceAfter = accountRepository.findById(source).orElseThrow();
        AccountEntity targetAfter = accountRepository.findById(target).orElseThrow();
        assertEquals(0, sourceAfter.getBalance().compareTo(new BigDecimal("30.00")));
        assertEquals(0, targetAfter.getBalance().compareTo(new BigDecimal("10.00")));
    }

    @Test
    void createTransferPersistsBalancedLedgerTransaction() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("200.00"));

        UUID transferId = createTransferAndGetId(ownerToken, source, target, new BigDecimal("35.00"), "idem-ledger-1");

        LedgerTransactionEntity ledgerTransaction = ledgerTransactionRepository.findByRelatedTransferId(transferId)
                .orElseThrow();
        List<LedgerEntryEntity> entries = ledgerEntryRepository.findByLedgerTransactionIdOrderByCreatedAtAsc(ledgerTransaction.getId());

        assertEquals(2, entries.size());

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        boolean foundSourceCredit = false;
        boolean foundTargetDebit = false;

        for (LedgerEntryEntity entry : entries) {
            assertEquals("USD", entry.getCurrency());
            if (entry.getSide() == EntrySide.DEBIT) {
                debits = debits.add(entry.getAmount());
            } else {
                credits = credits.add(entry.getAmount());
            }
            if (entry.getAccountId().equals(source) && entry.getSide() == EntrySide.CREDIT) {
                foundSourceCredit = true;
            }
            if (entry.getAccountId().equals(target) && entry.getSide() == EntrySide.DEBIT) {
                foundTargetDebit = true;
            }
        }

        assertEquals(0, debits.compareTo(new BigDecimal("35.00")));
        assertEquals(0, credits.compareTo(new BigDecimal("35.00")));
        assertTrue(foundSourceCredit);
        assertTrue(foundTargetDebit);
    }

    @Test
    void ledgerReadEndpointsEnforceOwnerAdminAndForbiddenRules() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("120.00"));

        UUID transferId = createTransferAndGetId(ownerToken, source, target, new BigDecimal("20.00"), "idem-ledger-access-1");
        UUID ledgerTransactionId = ledgerTransactionRepository.findByRelatedTransferId(transferId)
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/ledger/accounts/{accountId}/entries", source)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ledger/accounts/{accountId}/entries", source)
                        .header("Authorization", "Bearer " + unrelatedUserToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/ledger/accounts/{accountId}/entries", source)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ledger/transactions/transfer/{transferId}", transferId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ledger/transactions/transfer/{transferId}", transferId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ledger/transactions/{ledgerTransactionId}", ledgerTransactionId)
                        .header("Authorization", "Bearer " + unrelatedUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTransferDoesNotPartiallyUpdateBalances() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("20.00"));
        setBalance(target, new BigDecimal("30.00"));

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                source,
                                target,
                                new BigDecimal("25.00"),
                                null,
                                "idem-rollback-1"
                        ))))
                .andExpect(status().isBadRequest());

        AccountEntity sourceAfter = accountRepository.findById(source).orElseThrow();
        AccountEntity targetAfter = accountRepository.findById(target).orElseThrow();
        assertEquals(0, sourceAfter.getBalance().compareTo(new BigDecimal("20.00")));
        assertEquals(0, targetAfter.getBalance().compareTo(new BigDecimal("30.00")));
        assertEquals(0, transferRepository.count());
        assertEquals(0, ledgerTransactionRepository.count());
        assertEquals(0, ledgerEntryRepository.count());
    }

    @Test
    void ledgerFailureRollsBackTransferAndBalances() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("75.00"));
        setBalance(target, new BigDecimal("10.00"));

        // Force ledger validation failure after transfer completion step, so transaction must rollback fully.
        setCurrency(source, " ");
        setCurrency(target, " ");

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                source,
                                target,
                                new BigDecimal("5.00"),
                                null,
                                "idem-ledger-failure-1"
                        ))))
                .andExpect(status().isBadRequest());

        AccountEntity sourceAfter = accountRepository.findById(source).orElseThrow();
        AccountEntity targetAfter = accountRepository.findById(target).orElseThrow();
        assertEquals(0, sourceAfter.getBalance().compareTo(new BigDecimal("75.00")));
        assertEquals(0, targetAfter.getBalance().compareTo(new BigDecimal("10.00")));
        assertEquals(0, transferRepository.count());
        assertEquals(0, ledgerTransactionRepository.count());
        assertEquals(0, ledgerEntryRepository.count());
    }

    @Test
    void ledgerEndpointsAreReadOnly() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("50.00"));

        UUID transferId = createTransferAndGetId(ownerToken, source, target, new BigDecimal("5.00"), "idem-ledger-readonly-1");
        UUID ledgerTransactionId = ledgerTransactionRepository.findByRelatedTransferId(transferId)
                .orElseThrow()
                .getId();

        mockMvc.perform(put("/ledger/transactions/{ledgerTransactionId}", ledgerTransactionId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void listMyTransfersReturnsOnlyCurrentUserTransfers() throws Exception {
        UUID ownerSource = createAccountAndGetId(ownerToken, "USD");
        UUID ownerTarget = createAccountAndGetId(otherUserToken, "USD");
        UUID otherSource = createAccountAndGetId(otherUserToken, "USD");

        setBalance(ownerSource, new BigDecimal("60.00"));
        setBalance(otherSource, new BigDecimal("60.00"));

        createTransferAndGetId(ownerToken, ownerSource, ownerTarget, new BigDecimal("10.00"), "idem-list-owner");
        createTransferAndGetId(otherUserToken, otherSource, ownerTarget, new BigDecimal("5.00"), "idem-list-other");

        MvcResult meResult = mockMvc.perform(get("/transfers/me")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode transfers = objectMapper.readTree(meResult.getResponse().getContentAsString());
        assertEquals(1, transfers.size());
    }

    @Test
    void riskRejectsTransferAboveMaxAmountWithoutMutatingBalancesOrLedger() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("5000.00"));
        setBalance(target, new BigDecimal("10.00"));

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                source,
                                target,
                                new BigDecimal("1500.00"),
                                "risk-amount",
                                "idem-risk-amount-1"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("MAX_TRANSFER_AMOUNT_EXCEEDED")));

        AccountEntity sourceAfter = accountRepository.findById(source).orElseThrow();
        AccountEntity targetAfter = accountRepository.findById(target).orElseThrow();
        assertEquals(0, sourceAfter.getBalance().compareTo(new BigDecimal("5000.00")));
        assertEquals(0, targetAfter.getBalance().compareTo(new BigDecimal("10.00")));
        assertEquals(0, transferRepository.count());
        assertEquals(0, ledgerTransactionRepository.count());
        assertEquals(0, ledgerEntryRepository.count());

        RiskEvaluationEntity evaluation = riskEvaluationRepository.findBySourceAccountIdOrderByCreatedAtDesc(source)
                .stream()
                .findFirst()
                .orElseThrow();
        assertEquals(RiskDecision.REJECT, evaluation.getDecision());
        assertEquals("MAX_TRANSFER_AMOUNT_EXCEEDED", evaluation.getReason());
        assertTrue(evaluation.getTriggeredRules().contains("AMOUNT_LIMIT"));

        AuditEventEntity rejectedEvent = auditEventRepository.findAll().stream()
                .filter(event -> "TRANSFER_REJECTED_BY_RISK".equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertEquals("REJECTED", rejectedEvent.getOutcome());
    }

    @Test
    void riskRejectsTransferWhenDailyLimitExceeded() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID targetA = createAccountAndGetId(otherUserToken, "USD");
        UUID targetB = createAccountAndGetId(unrelatedUserToken, "USD");
        setBalance(source, new BigDecimal("6000.00"));
        setCreatedAt(source, Instant.now().minusSeconds(3 * 24 * 3600L));

        seedCompletedTransfer(source, targetA, new BigDecimal("2500.00"), "seed-daily-limit");

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                source,
                                targetB,
                                new BigDecimal("700.00"),
                                "risk-daily",
                                "idem-risk-daily-reject-1"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("DAILY_LIMIT_EXCEEDED")));

        AccountEntity sourceAfter = accountRepository.findById(source).orElseThrow();
        assertEquals(0, sourceAfter.getBalance().compareTo(new BigDecimal("6000.00")));

        RiskEvaluationEntity evaluation = riskEvaluationRepository.findBySourceAccountIdOrderByCreatedAtDesc(source)
                .stream()
                .findFirst()
                .orElseThrow();
        assertEquals(RiskDecision.REJECT, evaluation.getDecision());
        assertEquals("DAILY_LIMIT_EXCEEDED", evaluation.getReason());
        assertTrue(evaluation.getTriggeredRules().contains("DAILY_LIMIT"));
    }

    @Test
    void riskRejectsTransferWhenVelocityLimitExceeded() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID targetA = createAccountAndGetId(otherUserToken, "USD");
        UUID targetB = createAccountAndGetId(unrelatedUserToken, "USD");
        UUID targetC = createAccountAndGetId(adminToken, "USD");
        setBalance(source, new BigDecimal("1000.00"));

        createTransferAndGetId(ownerToken, source, targetA, new BigDecimal("100.00"), "idem-risk-velocity-ok-1");
        createTransferAndGetId(ownerToken, source, targetB, new BigDecimal("100.00"), "idem-risk-velocity-ok-2");

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                source,
                                targetC,
                                new BigDecimal("100.00"),
                                "risk-velocity",
                                "idem-risk-velocity-reject-1"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("VELOCITY_LIMIT_EXCEEDED")));

        RiskEvaluationEntity evaluation = riskEvaluationRepository.findBySourceAccountIdOrderByCreatedAtDesc(source)
                .stream()
                .findFirst()
                .orElseThrow();
        assertEquals(RiskDecision.REJECT, evaluation.getDecision());
        assertEquals("VELOCITY_LIMIT_EXCEEDED", evaluation.getReason());
        assertTrue(evaluation.getTriggeredRules().contains("VELOCITY_LIMIT"));
    }

    @Test
    void riskRejectsHighValueTransferFromNewAccount() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("3000.00"));

        mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                source,
                                target,
                                new BigDecimal("800.00"),
                                "risk-new-account",
                                "idem-risk-new-account-1"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("NEW_ACCOUNT_HIGH_VALUE_TRANSFER")));

        AccountEntity sourceAfter = accountRepository.findById(source).orElseThrow();
        AccountEntity targetAfter = accountRepository.findById(target).orElseThrow();
        assertEquals(0, sourceAfter.getBalance().compareTo(new BigDecimal("3000.00")));
        assertEquals(0, targetAfter.getBalance().compareTo(new BigDecimal("0.00")));
        assertEquals(0, transferRepository.count());
        assertEquals(0, ledgerTransactionRepository.count());
        assertEquals(0, ledgerEntryRepository.count());

        RiskEvaluationEntity evaluation = riskEvaluationRepository.findBySourceAccountIdOrderByCreatedAtDesc(source)
                .stream()
                .findFirst()
                .orElseThrow();
        assertEquals(RiskDecision.REJECT, evaluation.getDecision());
        assertEquals("NEW_ACCOUNT_HIGH_VALUE_TRANSFER", evaluation.getReason());
        assertTrue(evaluation.getTriggeredRules().contains("NEW_ACCOUNT_HIGH_VALUE"));
    }

    @Test
    void riskEndpointsAllowAdminAndRejectRegularUser() throws Exception {
        UUID source = createAccountAndGetId(ownerToken, "USD");
        UUID target = createAccountAndGetId(otherUserToken, "USD");
        setBalance(source, new BigDecimal("200.00"));

        UUID transferId = createTransferAndGetId(ownerToken, source, target, new BigDecimal("50.00"), "idem-risk-endpoint-1");
        RiskEvaluationEntity evaluation = riskEvaluationRepository.findTopByTransferIdOrderByCreatedAtDesc(transferId)
                .orElseThrow();
        assertNotNull(evaluation.getId());

        mockMvc.perform(get("/risk/evaluations/{riskEvaluationId}", evaluation.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(evaluation.getId().toString()))
                .andExpect(jsonPath("$.decision").value("ALLOW"));

        mockMvc.perform(get("/risk/evaluations/{riskEvaluationId}", evaluation.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/risk/transfers/{transferId}", transferId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(transferId.toString()));
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

    private UUID createTransferAndGetId(
            String token,
            UUID source,
            UUID target,
            BigDecimal amount,
            String idempotencyKey
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(
                                source,
                                target,
                                amount,
                                null,
                                idempotencyKey
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText());
    }

    private void setBalance(UUID accountId, BigDecimal balance) {
        AccountEntity account = accountRepository.findById(accountId).orElseThrow();
        account.setBalance(balance);
        accountRepository.save(account);
    }

    private void setCurrency(UUID accountId, String currency) {
        AccountEntity account = accountRepository.findById(accountId).orElseThrow();
        account.setCurrency(currency);
        accountRepository.save(account);
    }

    private void setCreatedAt(UUID accountId, Instant createdAt) {
        AccountEntity account = accountRepository.findById(accountId).orElseThrow();
        account.setCreatedAt(createdAt);
        accountRepository.save(account);
    }

    private void seedCompletedTransfer(UUID source, UUID target, BigDecimal amount, String idempotencyKey) {
        UUID ownerId = userRepository.findByEmailIgnoreCase("owner@neobank.com").orElseThrow().getId();

        TransferEntity transfer = new TransferEntity();
        transfer.setSourceAccountId(source);
        transfer.setTargetAccountId(target);
        transfer.setAmount(amount);
        transfer.setCurrency("USD");
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setInitiatedByUserId(ownerId);
        transfer.setIdempotencyKey(idempotencyKey);
        transfer.setProcessedAt(Instant.now().minusSeconds(2 * 3600L));

        transferRepository.saveAndFlush(transfer);
    }
}
