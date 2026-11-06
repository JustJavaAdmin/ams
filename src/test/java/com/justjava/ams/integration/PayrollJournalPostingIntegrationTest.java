package com.justjava.ams.integration;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.FiscalPeriod;
import com.justjava.ams.accountant.entity.GeneralLedger;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import com.justjava.ams.accountant.repository.GeneralLedgerRepository;
import com.justjava.ams.accountant.service.GeneralLedgerService;
import com.justjava.ams.auditor.dto.AuditLogDTO;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.integration.entity.ClientOrganizationEntitlement;
import com.justjava.ams.integration.dto.PayrollJournalLineRequest;
import com.justjava.ams.integration.dto.PayrollJournalRequest;
import com.justjava.ams.integration.dto.PayrollJournalResponse;
import com.justjava.ams.integration.entity.PayrollJournal;
import com.justjava.ams.integration.repository.PayrollJournalIdempotencyRepository;
import com.justjava.ams.integration.repository.PayrollJournalRepository;
import com.justjava.ams.integration.service.PayrollJournalPostingService;
import com.justjava.ams.integration.service.PayrollJournalPostingWorker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.aop.support.AopUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class PayrollJournalPostingIntegrationTest {
    private static final String CLIENT_ID = "jjeip-ams";
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 10, 31);

    @Autowired
    private PayrollJournalPostingService postingService;

    @Autowired
    private PayrollJournalPostingWorker postingWorker;

    @Autowired
    private PayrollJournalRepository journalRepository;

    @Autowired
    private PayrollJournalIdempotencyRepository idempotencyRepository;

    @Autowired
    private GeneralLedgerRepository generalLedgerRepository;

    @Autowired
    private GeneralLedgerService generalLedgerService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private AuditLogService auditLogService;

    private final List<Long> organizationsToClean = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        for (Long organizationId : organizationsToClean) {
            transaction.executeWithoutResult(status -> {
                jdbcTemplate.update("""
                        delete from audit_logs
                        where organization_id = ? and entity_type = 'PayrollJournal'
                          and entity_id in (select id from payroll_journals where organization_id = ?)
                        """, organizationId, organizationId);
                jdbcTemplate.update("""
                        delete from general_ledger
                        where source_type = 'PAYROLL_JOURNAL'
                          and source_id in (select id from payroll_journals where organization_id = ?)
                        """, organizationId);
                jdbcTemplate.update("""
                        delete from payroll_journal_lines
                        where payroll_journal_id in (select id from payroll_journals where organization_id = ?)
                        """, organizationId);
                jdbcTemplate.update("delete from payroll_journal_idempotency where organization_id = ?", organizationId);
                jdbcTemplate.update("delete from payroll_journals where organization_id = ?", organizationId);
                jdbcTemplate.update("delete from client_organization_entitlements where organization_id = ?", organizationId);
                jdbcTemplate.update("delete from module_controls where organization_id = ?", organizationId);
                jdbcTemplate.update("delete from fiscal_periods where organization_id = ?", organizationId);
                jdbcTemplate.update("delete from chart_of_accounts where organization_id = ?", organizationId);
                jdbcTemplate.update("delete from organizations where id = ?", organizationId);
            });
        }

        organizationsToClean.clear();
    }

    @Test
    void validJournalPersistsPostedProvenanceAndDuplicateDoesNotCreateRows() {
        Fixture fixture = fixture();
        PayrollJournalRequest request = request(fixture.accountCode());
        String key = key(request);

        PayrollJournalResponse first = postingService.post(
                fixture.organizationId(), key, request, caller());
        long ledgerCount = payrollLedgerCount(first.journalId());

        PayrollJournalResponse duplicate = postingService.post(
                fixture.organizationId(), key, request, caller());

        assertThat(first.status()).isEqualTo("POSTED");
        assertThat(first.duplicate()).isFalse();
        assertThat(first.totalDebit()).isEqualByComparingTo("100.00");
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.journalId()).isEqualTo(first.journalId());
        assertThat(payrollLedgerCount(first.journalId())).isEqualTo(ledgerCount);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from general_ledger
                where source_type = 'PAYROLL_JOURNAL' and source_id = ? and fiscal_period_id is not null
                """, Long.class, first.journalId())).isEqualTo(2);
        List<GeneralLedger> ledgerRows = generalLedgerRepository.findBySourceTypeAndSourceId(
                GeneralLedger.SourceType.PAYROLL_JOURNAL, first.journalId());
        assertThat(ledgerRows).hasSize(2).allSatisfy(entry -> {
            assertThat(entry.getJournalNumber()).isEqualTo("PAY-" + request.getJournalReference());
            assertThat(entry.getJournalNumber()).doesNotStartWith("MJ-");
            assertThat(entry.getPostingBatchId()).startsWith(
                    "PAYROLL_JOURNAL-" + first.journalId() + "-");
            assertThat(entry.getPostedBy()).isEqualTo(CLIENT_ID);
            assertThat(entry.getCreatedByUser()).isNull();
        });
        assertThat(ledgerRows.stream().map(GeneralLedger::getPostingBatchId).distinct()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from payroll_journal_lines
                where payroll_journal_id = ?
                """, Long.class, first.journalId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select balance from chart_of_accounts where organization_id = ? and account_code = ?
                """, BigDecimal.class, fixture.organizationId(), fixture.accountCode()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(journalRepository.findById(first.journalId()))
                .get()
                .satisfies(journal -> {
                    assertThat(journal.getClientId()).isEqualTo(CLIENT_ID);
                    assertThat(journal.getCurrency()).isEqualTo("NGN");
                    assertThat(journal.getPayrollPeriodId()).isEqualTo("PAYROLL-PERIOD-1");
                    assertThat(journal.getPostedAt()).isNotNull();
                    assertThat(journal.getRequestHash()).hasSize(64);
                    assertThat(journal.getIdempotencyKey()).isEqualTo(key);
                    assertThat(jdbcTemplate.queryForObject("""
                            select count(*) from audit_logs
                            where organization_id = ? and entity_type = 'PayrollJournal' and entity_id = ?
                            """, Long.class, fixture.organizationId(), journal.getId())).isEqualTo(1);
                });
    }

    @Test
    void payrollLedgerDtoMappingHandlesNullCreatedByUser() {
        Fixture fixture = fixture();
        PayrollJournalRequest request = request(fixture.accountCode());
        PayrollJournalResponse posted = postingService.post(
                fixture.organizationId(), key(request), request, caller());
        GeneralLedger entry = generalLedgerRepository.findBySourceTypeAndSourceId(
                GeneralLedger.SourceType.PAYROLL_JOURNAL, posted.journalId()).get(0);

        var dto = generalLedgerService.getEntry(entry.getId());

        assertThat(entry.getCreatedByUser()).isNull();
        assertThat(dto.getSourceType()).isEqualTo("PAYROLL_JOURNAL");
        assertThat(dto.getCreatedByUserId()).isNull();
    }

    @Test
    void changedRequestUnderSameKeyConflictsWithoutChangingStoredPosting() {
        Fixture fixture = fixture();
        PayrollJournalRequest request = request(fixture.accountCode());
        String key = key(request);
        PayrollJournalResponse original = postingService.post(
                fixture.organizationId(), key, request, caller());
        PayrollJournalRequest changed = request(fixture.accountCode());
        changed.setJournalReference(request.getJournalReference());
        changed.getLines().get(0).setDebitAmount(new BigDecimal("101.00"));
        changed.getLines().get(1).setCreditAmount(new BigDecimal("101.00"));

        assertThatThrownBy(() -> postingService.post(
                fixture.organizationId(), key, changed, caller()))
                .isInstanceOf(com.justjava.ams.integration.exception.IntegrationApiException.class)
                .hasMessageContaining("different request");
        assertThat(journalCount(fixture.organizationId())).isEqualTo(1);
        assertThat(payrollLedgerCount(original.journalId())).isEqualTo(2);
    }

    @Test
    void tenSimultaneousIdenticalRequestsCreateExactlyOnePosting() throws Exception {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        Fixture fixture = fixture();
        PayrollJournalRequest request = request(fixture.accountCode());
        String key = key(request);
        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ConcurrentResponse>> results = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    MvcResult result = mockMvc.perform(post("/api/integrations/organizations/{organizationId}/payroll-journals",
                                    fixture.organizationId())
                                    .header("Idempotency-Key", key)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestJson(request))
                                    .with(jwt().jwt(token -> token
                                            .claim("azp", CLIENT_ID)
                                            .claim("scope", "payroll-journal:post"))))
                            .andReturn();
                    if (result.getResponse().getStatus() != 200 && result.getResponse().getStatus() != 201) {
                        throw new AssertionError("Unexpected concurrent response status: "
                                + result.getResponse().getStatus());
                    }
                    var json = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(result.getResponse().getContentAsByteArray());
                    return new ConcurrentResponse(
                            json.get("journalId").asLong(),
                            json.get("duplicate").asBoolean(),
                            result.getResponse().getStatus());
                }));
            }
            ready.await();
            start.countDown();
            List<ConcurrentResponse> responses = new ArrayList<>();
            for (Future<ConcurrentResponse> result : results) {
                responses.add(result.get());
            }

            assertThat(responses).hasSize(requestCount);
            assertThat(responses.stream().filter(response -> !response.duplicate())).hasSize(1);
            assertThat(responses.stream().map(ConcurrentResponse::journalId).distinct()).hasSize(1);
            assertThat(journalCount(fixture.organizationId())).isEqualTo(1);
            assertThat(payrollLedgerCount(responses.get(0).journalId())).isEqualTo(2);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void failureAfterJournalAndLinesAreWrittenRollsBackClaimJournalAndLedgerRows() {
        Fixture fixture = fixture();
        PayrollJournalRequest request = request(fixture.accountCode());
        String key = key(request);
        String hash = "0".repeat(64);

        assertThatThrownBy(() -> postingWorker.post(
                fixture.organizationId(), key, hash, CLIENT_ID, request,
                Long.MAX_VALUE, new BigDecimal("100.00"), new BigDecimal("100.00"),
                List.of(fixture.account(), fixture.account())))
                .isInstanceOf(RuntimeException.class);

        assertThat(idempotencyRepository.findByOrganizationIdAndIdempotencyKey(
                fixture.organizationId(), key)).isEmpty();
        assertThat(journalRepository.findByOrganizationIdAndSourceSystemAndExternalReferenceAndRevision(
                fixture.organizationId(), "JUSTHR", request.getJournalReference(), "ORIGINAL")).isEmpty();
        assertThat(generalLedgerRepository.findBySourceTypeAndSourceId(
                GeneralLedger.SourceType.PAYROLL_JOURNAL, -1L)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from general_ledger
                where source_type = 'PAYROLL_JOURNAL'
                  and account_id in (select id from chart_of_accounts where organization_id = ?)
                """, Long.class, fixture.organizationId())).isZero();
    }

    @Test
    void auditWriteFailureRollsBackClaimJournalLinesAndLedgerRows() {
        Fixture fixture = fixture();
        PayrollJournalRequest request = request(fixture.accountCode());
        String key = key(request);
        doThrow(new IllegalStateException("simulated audit write failure"))
                .when(auditLogService).createAuditLog(eq(fixture.organizationId()), any(AuditLogDTO.class));

        assertThatThrownBy(() -> postingService.post(
                fixture.organizationId(), key, request, caller()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated audit write failure");

        assertThat(idempotencyRepository.findByOrganizationIdAndIdempotencyKey(
                fixture.organizationId(), key)).isEmpty();
        assertThat(journalRepository.findByOrganizationIdAndSourceSystemAndExternalReferenceAndRevision(
                fixture.organizationId(), "JUSTHR", request.getJournalReference(), "ORIGINAL")).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from payroll_journal_lines
                where payroll_journal_id in (select id from payroll_journals where organization_id = ?)
                """, Long.class, fixture.organizationId())).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from general_ledger
                where source_type = 'PAYROLL_JOURNAL'
                  and account_id in (select id from chart_of_accounts where organization_id = ?)
                """, Long.class, fixture.organizationId())).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_logs
                where organization_id = ? and entity_type = 'PayrollJournal'
                """, Long.class, fixture.organizationId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payroll_journals where organization_id = ?", Long.class,
                fixture.organizationId())).isZero();
    }

    @Test
    void liveDatabaseHasPayrollIdempotencyUniqueIndexes() {
        List<String> indexes = jdbcTemplate.queryForList("""
                select indexname from pg_indexes
                where schemaname = current_schema()
                  and tablename in ('payroll_journal_idempotency', 'payroll_journals')
                  and indexdef ilike 'create unique index%'
                """, String.class);

        assertThat(indexes).contains(
                "uk_pji_org_key", "uk_pji_business_key", "uk_payroll_journal_business_key");
        String sourceTypeCheck = jdbcTemplate.queryForObject("""
                select pg_get_constraintdef(oid) from pg_constraint
                where conrelid = 'general_ledger'::regclass
                  and conname = 'general_ledger_source_type_check'
                """, String.class);
        assertThat(sourceTypeCheck).contains("'PAYROLL_JOURNAL'");
        assertThat(jdbcTemplate.queryForObject("""
                select numeric_precision from information_schema.columns
                where table_schema = current_schema() and table_name = 'general_ledger' and column_name = 'amount'
                """, Integer.class)).isEqualTo(19);
        assertThat(jdbcTemplate.queryForObject("""
                select numeric_scale from information_schema.columns
                where table_schema = current_schema() and table_name = 'general_ledger' and column_name = 'amount'
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void lineValidationRejectsUnbalancedTwoSidedNegativeAndTooFewLines() {
        Fixture fixture = fixture();
        PayrollJournalRequest unbalanced = request(fixture.accountCode());
        unbalanced.getLines().get(1).setCreditAmount(new BigDecimal("99.00"));
        assertCode(fixture, unbalanced, "JOURNAL_UNBALANCED");

        PayrollJournalRequest bothSidesPositive = request(fixture.accountCode());
        bothSidesPositive.getLines().get(0).setCreditAmount(BigDecimal.ONE);
        assertCode(fixture, bothSidesPositive, "VALIDATION_ERROR");

        PayrollJournalRequest negative = request(fixture.accountCode());
        negative.getLines().get(0).setDebitAmount(new BigDecimal("-1.00"));
        assertCode(fixture, negative, "VALIDATION_ERROR");

        PayrollJournalRequest tooFew = request(fixture.accountCode());
        tooFew.setLines(List.of(tooFew.getLines().get(0)));
        assertCode(fixture, tooFew, "VALIDATION_ERROR");
    }

    @Test
    void accountValidationRejectsUnknownInactiveAndForeignCodesAndCollectsAllFailures() {
        Fixture fixture = fixture();
        String inactiveCode = addAccount(fixture.organizationId(), false);
        PayrollJournalRequest inactive = request(fixture.accountCode());
        inactive.getLines().get(0).setAccountCode(inactiveCode);
        assertCode(fixture, inactive, "ACCOUNT_NOT_FOUND_OR_INACTIVE");

        Fixture otherOrganization = fixture();
        PayrollJournalRequest foreign = request(fixture.accountCode());
        foreign.getLines().get(0).setAccountCode(otherOrganization.accountCode());
        assertCode(fixture, foreign, "ACCOUNT_NOT_FOUND_OR_INACTIVE");

        PayrollJournalRequest twoUnknown = request(fixture.accountCode());
        twoUnknown.getLines().get(0).setAccountCode("UNKNOWN-A");
        twoUnknown.getLines().get(1).setAccountCode("UNKNOWN-B");
        var exception = rejected(fixture, twoUnknown);
        assertThat(exception.getCode()).isEqualTo("ACCOUNT_NOT_FOUND_OR_INACTIVE");
        assertThat(exception.getDetails()).hasSize(2);
    }

    @Test
    void periodRevisionHeaderCurrencyAndAmountRulesAreEnforced() {
        Fixture fixture = fixture();
        PayrollJournalRequest closedPeriod = request(fixture.accountCode());
        jdbcTemplate.update("update fiscal_periods set status = 'CLOSED', closed = true where organization_id = ?",
                fixture.organizationId());
        assertCode(fixture, closedPeriod, "FISCAL_PERIOD_CLOSED");

        Fixture noPeriodFixture = fixture();
        PayrollJournalRequest noPeriod = request(noPeriodFixture.accountCode());
        noPeriod.setPeriodStart(LocalDate.of(2027, 1, 1));
        noPeriod.setPeriodEnd(LocalDate.of(2027, 1, 31));
        assertCode(noPeriodFixture, noPeriod, "FISCAL_PERIOD_CLOSED");

        Fixture validationFixture = fixture();
        PayrollJournalRequest unsupportedRevision = request(validationFixture.accountCode());
        unsupportedRevision.setRevision("ADJUSTMENT");
        assertCode(validationFixture, unsupportedRevision, "UNSUPPORTED_REVISION");

        PayrollJournalRequest invalidCurrency = request(validationFixture.accountCode());
        invalidCurrency.setCurrency("ZZZ");
        assertCode(validationFixture, invalidCurrency, "VALIDATION_ERROR");

        PayrollJournalRequest extraPrecision = request(validationFixture.accountCode());
        extraPrecision.getLines().get(0).setDebitAmount(new BigDecimal("100.001"));
        assertCode(validationFixture, extraPrecision, "VALIDATION_ERROR");

        PayrollJournalRequest tooManyLines = request(validationFixture.accountCode());
        tooManyLines.setLines(new ArrayList<>(java.util.Collections.nCopies(201, tooManyLines.getLines().get(0))));
        assertCode(validationFixture, tooManyLines, "TOO_MANY_LINES");

        PayrollJournalRequest valid = request(validationFixture.accountCode());
        assertThatThrownBy(() -> postingService.post(
                validationFixture.organizationId(), "wrong-key", valid, caller()))
                .isInstanceOf(com.justjava.ams.integration.exception.IntegrationApiException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void transactionWorkerIsProxiedAndLookupReturnsPostedJournal() {
        assertThat(AopUtils.isAopProxy(postingWorker)).isTrue();
        Fixture fixture = fixture();
        PayrollJournalRequest request = request(fixture.accountCode());
        String key = key(request);
        PayrollJournalResponse posted = postingService.post(
                fixture.organizationId(), key, request, caller());

        PayrollJournalResponse found = postingService.findByKey(fixture.organizationId(), key);
        assertThat(found.journalId()).isEqualTo(posted.journalId());
        assertThat(found.status()).isEqualTo("POSTED");
        assertThatThrownBy(() -> postingService.findByKey(fixture.organizationId(), "missing-key"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        try {
            mockMvc.perform(get("/api/integrations/organizations/{organizationId}/payroll-journals/by-key",
                            fixture.organizationId())
                            .param("idempotencyKey", key)
                            .with(jwt().jwt(token -> token
                                    .claim("azp", CLIENT_ID)
                                    .claim("scope", "payroll-journal:read"))))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                            "$.journalId").value(posted.journalId()));
            mockMvc.perform(get("/api/integrations/organizations/{organizationId}/payroll-journals/by-key",
                            fixture.organizationId())
                            .param("idempotencyKey", "missing-key")
                            .with(jwt().jwt(token -> token
                                    .claim("azp", CLIENT_ID)
                                    .claim("scope", "payroll-journal:read"))))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                            "$.code").value("NOT_FOUND"));
        } catch (Exception ex) {
            throw new AssertionError("Lookup endpoint failed", ex);
        }
    }

    @Test
    void unentitledAndNonexistentOrganizationsReturnTheSameForbiddenResponse() throws Exception {
        Fixture entitledFixture = fixture();
        Fixture unentitledFixture = fixture();
        jdbcTemplate.update("delete from client_organization_entitlements where organization_id = ?",
                unentitledFixture.organizationId());
        PayrollJournalRequest request = request(entitledFixture.accountCode());
        String key = key(request);
        var caller = jwt().jwt(token -> token
                .claim("azp", CLIENT_ID)
                .claim("scope", "payroll-journal:post"));

        MvcResult unentitled = mockMvc.perform(post(
                        "/api/integrations/organizations/{organizationId}/payroll-journals",
                        unentitledFixture.organizationId())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(request))
                        .with(caller))
                .andReturn();
        MvcResult nonexistent = mockMvc.perform(post(
                        "/api/integrations/organizations/{organizationId}/payroll-journals",
                        Long.MAX_VALUE)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(request))
                        .with(caller))
                .andReturn();

        assertThat(unentitled.getResponse().getStatus())
                .as(unentitled.getResponse().getContentAsString()).isEqualTo(403);
        assertThat(nonexistent.getResponse().getStatus())
                .as(nonexistent.getResponse().getContentAsString()).isEqualTo(403);
        assertThat(nonexistent.getResponse().getContentAsString())
                .isEqualTo(unentitled.getResponse().getContentAsString());
    }

    @Test
    void generalLedgerLimitUsesJournalTotalAndReturnsRetryable422() {
        Fixture fixture = fixture();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Organization organization = entityManager.find(Organization.class, fixture.organizationId());
            entityManager.persist(ModuleControl.builder()
                    .organization(organization)
                    .moduleType(ModuleControl.ModuleType.GENERAL_LEDGER)
                    .enabled(true)
                    .allowUserConfiguration(false)
                    .requiresApproval(false)
                    .enableAuditTrail(true)
                    .enableNotifications(true)
                    .maxTransactionAmountLimit(99)
                    .build());
        });

        var exception = rejected(fixture, request(fixture.accountCode()));
        assertThat(exception.getCode()).isEqualTo("TRANSACTION_LIMIT_EXCEEDED");
        assertThat(exception.isRetryable()).isTrue();
    }

    private Fixture fixture() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Organization organization = Organization.builder()
                    .name("AMS Stage2 Payroll Integration Test " + suffix)
                    .registrationNumber("PAY-" + suffix)
                    .active(true)
                    .build();
            entityManager.persist(organization);
            entityManager.persist(ClientOrganizationEntitlement.builder()
                    .clientId(CLIENT_ID)
                    .organization(organization)
                    .active(true)
                    .createdBy("payroll-integration-test")
                    .build());

            FiscalPeriod period = FiscalPeriod.builder()
                    .organization(organization)
                    .year(PERIOD_END.getYear())
                    .quarter(4)
                    .startDate(PERIOD_END.withDayOfMonth(1).atStartOfDay())
                    .endDate(PERIOD_END.atTime(23, 59, 59))
                    .status(FiscalPeriod.PeriodStatus.OPEN)
                    .closed(false)
                    .build();
            entityManager.persist(period);

            ChartOfAccounts account = ChartOfAccounts.builder()
                    .organization(organization)
                    .accountCode("5000-" + suffix.substring(0, 8))
                    .accountName("Payroll account")
                    .accountType(ChartOfAccounts.AccountType.EXPENSE)
                    .accountSubtype(ChartOfAccounts.AccountSubtype.OPERATING_EXPENSE)
                    .normalBalance(ChartOfAccounts.DebitCredit.DEBIT)
                    .active(true)
                    .balance(BigDecimal.ZERO)
                    .build();
            entityManager.persist(account);
            entityManager.flush();
            return new Fixture(organization.getId(), account.getId(), account.getAccountCode(), account);
        });
        organizationsToClean.add(fixture.organizationId());
        return fixture;
    }

    private PayrollJournalRequest request(String accountCode) {
        PayrollJournalRequest request = new PayrollJournalRequest();
        request.setSourceSystem("JUSTHR");
        request.setJournalReference("PAY-" + UUID.randomUUID());
        request.setRevision("ORIGINAL");
        request.setPayrollPeriodId("PAYROLL-PERIOD-1");
        request.setPeriodStart(PERIOD_END.withDayOfMonth(1));
        request.setPeriodEnd(PERIOD_END);
        request.setCurrency("NGN");
        request.setNarration("Payroll test posting");
        PayrollJournalLineRequest debit = line(accountCode, "100.00", "0.00");
        PayrollJournalLineRequest credit = line(accountCode, "0.00", "100.00");
        request.setLines(new ArrayList<>(List.of(debit, credit)));
        return request;
    }

    private PayrollJournalLineRequest line(String accountCode, String debit, String credit) {
        PayrollJournalLineRequest line = new PayrollJournalLineRequest();
        line.setAccountCode(accountCode);
        line.setAccountName("Payroll account");
        line.setLineType("PAYROLL");
        line.setDebitAmount(new BigDecimal(debit));
        line.setCreditAmount(new BigDecimal(credit));
        return line;
    }

    private String key(PayrollJournalRequest request) {
        return request.getSourceSystem() + ":" + request.getJournalReference() + ":" + request.getRevision();
    }

    private String requestJson(PayrollJournalRequest request) {
        String accountCode = request.getLines().get(0).getAccountCode();
        return """
                {
                  "sourceSystem":"JUSTHR",
                  "journalReference":"%s",
                  "revision":"ORIGINAL",
                  "payrollPeriodId":"PAYROLL-PERIOD-1",
                  "periodStart":"2026-10-01",
                  "periodEnd":"2026-10-31",
                  "currency":"NGN",
                  "narration":"Payroll test posting",
                  "lines":[
                    {"accountCode":"%s","accountName":"Payroll account","lineType":"PAYROLL","debitAmount":100.00,"creditAmount":0.00},
                    {"accountCode":"%s","accountName":"Payroll account","lineType":"PAYROLL","debitAmount":0.00,"creditAmount":100.00}
                  ]
                }
                """.formatted(request.getJournalReference(), accountCode, accountCode);
    }

    private Jwt caller() {
        return Jwt.withTokenValue("integration-test")
                .header("alg", "none")
                .claim("azp", CLIENT_ID)
                .subject(CLIENT_ID)
                .build();
    }

    private long payrollLedgerCount(Long journalId) {
        return generalLedgerRepository.findBySourceTypeAndSourceId(
                GeneralLedger.SourceType.PAYROLL_JOURNAL, journalId).size();
    }

    private long journalCount(Long organizationId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from payroll_journals where organization_id = ?", Long.class, organizationId);
    }

    private void assertCode(Fixture fixture, PayrollJournalRequest request, String code) {
        assertThat(rejected(fixture, request).getCode()).isEqualTo(code);
    }

    private com.justjava.ams.integration.exception.IntegrationApiException rejected(
            Fixture fixture, PayrollJournalRequest request) {
        try {
            postingService.post(fixture.organizationId(), key(request), request, caller());
        } catch (com.justjava.ams.integration.exception.IntegrationApiException ex) {
            return ex;
        }
        throw new AssertionError("Expected validation rejection with a safe integration error");
    }

    private String addAccount(Long organizationId, boolean active) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            Organization organization = entityManager.find(Organization.class, organizationId);
            String code = (active ? "A-" : "I-") + UUID.randomUUID().toString().substring(0, 8);
            ChartOfAccounts account = ChartOfAccounts.builder()
                    .organization(organization)
                    .accountCode(code)
                    .accountName("Additional payroll account")
                    .accountType(ChartOfAccounts.AccountType.EXPENSE)
                    .accountSubtype(ChartOfAccounts.AccountSubtype.OPERATING_EXPENSE)
                    .normalBalance(ChartOfAccounts.DebitCredit.DEBIT)
                    .active(active)
                    .balance(BigDecimal.ZERO)
                    .build();
            entityManager.persist(account);
            entityManager.flush();
            return code;
        });
    }

    private record Fixture(Long organizationId, Long accountId, String accountCode, ChartOfAccounts account) {
    }

    private record ConcurrentResponse(Long journalId, boolean duplicate, int status) {
    }
}
