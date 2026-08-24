package com.justjava.ams.mvp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BankReconciliationAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bankStatementLineCanBeAutoMatchedAndReconciliationCompleted() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate transactionDate = LocalDate.now();

        long organizationId = postJson(
                "/api/organizations",
                Map.of(
                        "name", "Bank Reconciliation Org " + suffix,
                        "registrationNumber", "BR-REG-" + suffix,
                        "taxId", "BR-TAX-" + suffix,
                        "active", true),
                financeAdmin(),
                status().isCreated())
                .get("id").asLong();

        long branchId = postJson(
                "/api/branches",
                Map.of(
                        "organizationId", organizationId,
                        "name", "Bank Branch " + suffix,
                        "code", "BR-" + suffix,
                        "active", true),
                financeAdmin(),
                status().isCreated())
                .get("id").asLong();

        long cashAccountId = postJson(
                "/api/financeAdmin/chartOfAccounts/org/" + organizationId,
                Map.of(
                        "accountCode", "101-" + suffix,
                        "accountName", "Operating Bank " + suffix,
                        "accountType", "ASSET",
                        "accountSubtype", "CURRENT_ASSET",
                        "normalBalance", "DEBIT"),
                financeAdmin(),
                status().isCreated())
                .get("id").asLong();

        long revenueAccountId = postJson(
                "/api/financeAdmin/chartOfAccounts/org/" + organizationId,
                Map.of(
                        "accountCode", "401-" + suffix,
                        "accountName", "Revenue " + suffix,
                        "accountType", "REVENUE",
                        "accountSubtype", "REVENUE",
                        "normalBalance", "CREDIT"),
                financeAdmin(),
                status().isCreated())
                .get("id").asLong();

        long bankAccountId = postJson(
                "/api/accountant/bank-accounts/org/" + organizationId,
                Map.of(
                        "chartAccountId", cashAccountId,
                        "bankName", "Acceptance Bank",
                        "accountNumber", "000-" + suffix,
                        "accountHolder", "Bank Reconciliation Org " + suffix,
                        "currency", "NGN",
                        "balance", BigDecimal.ZERO),
                accountant(),
                status().isCreated())
                .get("id").asLong();

        postJson(
                "/api/financeAdmin/fiscalPeriods/org/" + organizationId,
                Map.of(
                        "year", transactionDate.getYear(),
                        "quarter", quarterFor(transactionDate),
                        "startDate", transactionDate.minusDays(1).toString(),
                        "endDate", transactionDate.plusDays(1).toString()),
                financeAdmin(),
                status().isCreated());

        long journalId = postJson(
                "/api/accountant/manual-journals/org/" + organizationId,
                Map.of(
                        "branchId", branchId,
                        "description", "Bank receipt " + suffix,
                        "journalDate", transactionDate.toString()),
                accountant(),
                status().isCreated())
                .get("id").asLong();

        postJson(
                "/api/accountant/manual-journals/" + journalId + "/lines",
                Map.of(
                        "chartOfAccountId", cashAccountId,
                        "debitAmount", new BigDecimal("100.00"),
                        "creditAmount", BigDecimal.ZERO,
                        "narration", "Bank deposit",
                        "lineSequence", 1),
                accountant(),
                status().isCreated());

        postJson(
                "/api/accountant/manual-journals/" + journalId + "/lines",
                Map.of(
                        "chartOfAccountId", revenueAccountId,
                        "debitAmount", BigDecimal.ZERO,
                        "creditAmount", new BigDecimal("100.00"),
                        "narration", "Revenue",
                        "lineSequence", 2),
                accountant(),
                status().isCreated());

        patchJson(
                "/api/accountant/manual-journals/" + journalId + "/submit",
                Map.of("submittedBy", "bank-accountant"),
                accountant(),
                status().isOk());
        patchJson(
                "/api/cfo/manual-journals/" + journalId + "/approve",
                Map.of("approvedBy", "bank-cfo", "approvalNote", "Bank reconciliation acceptance"),
                cfo(),
                status().isOk());
        patchJson(
                "/api/accountant/manual-journals/" + journalId + "/post",
                Map.of("postedBy", "bank-accountant"),
                accountant(),
                status().isOk());

        JsonNode reconciliation = postJson(
                "/api/accountant/bank-reconciliations/org/" + organizationId,
                Map.of(
                        "bankAccountId", bankAccountId,
                        "statementDate", transactionDate.toString(),
                        "openingBalance", BigDecimal.ZERO,
                        "closingBalance", new BigDecimal("100.00"),
                        "statementLines", List.of(Map.of(
                                "transactionDate", transactionDate.toString(),
                                "amount", new BigDecimal("100.00"),
                                "description", "Bank deposit"))),
                accountant(),
                status().isCreated());

        assertThat(reconciliation.get("status").asText()).isEqualTo("DRAFT");
        assertThat(reconciliation.get("importedLineCount").asInt()).isEqualTo(1);
        assertThat(reconciliation.get("matchedLineCount").asInt()).isEqualTo(1);
        assertThat(reconciliation.get("unresolvedDifference").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reconciliation.get("statementLines").get(0).get("matchStatus").asText()).isEqualTo("AUTO_MATCHED");
        assertThat(reconciliation.get("statementLines").get(0).hasNonNull("matchedGeneralLedgerId")).isTrue();
        long statementLineId = reconciliation.get("statementLines").get(0).get("id").asLong();
        long matchedGeneralLedgerId = reconciliation.get("statementLines").get(0).get("matchedGeneralLedgerId").asLong();

        JsonNode unmatched = patchJson(
                "/api/accountant/bank-reconciliations/" + reconciliation.get("id").asLong() + "/lines/" + statementLineId + "/unmatch",
                Map.of(),
                accountant(),
                status().isOk());
        assertThat(unmatched.get("matchedLineCount").asInt()).isZero();
        assertThat(unmatched.get("statementLines").get(0).get("matchStatus").asText()).isEqualTo("UNMATCHED");

        patchJson(
                "/api/accountant/bank-reconciliations/" + reconciliation.get("id").asLong() + "/complete",
                Map.of(),
                accountant(),
                status().isConflict());

        JsonNode candidates = getJson(
                "/api/accountant/bank-reconciliations/" + reconciliation.get("id").asLong() + "/lines/" + statementLineId + "/candidates",
                accountant(),
                status().isOk());
        assertThat(arrayContains(candidates, row -> row.get("id").asLong() == matchedGeneralLedgerId)).isTrue();

        JsonNode manuallyMatched = patchJson(
                "/api/accountant/bank-reconciliations/" + reconciliation.get("id").asLong() + "/lines/" + statementLineId + "/match",
                Map.of("generalLedgerId", matchedGeneralLedgerId),
                accountant(),
                status().isOk());
        assertThat(manuallyMatched.get("matchedLineCount").asInt()).isEqualTo(1);
        assertThat(manuallyMatched.get("statementLines").get(0).get("matchStatus").asText()).isEqualTo("MANUAL_MATCHED");

        JsonNode completed = patchJson(
                "/api/accountant/bank-reconciliations/" + reconciliation.get("id").asLong() + "/complete",
                Map.of(),
                accountant(),
                status().isOk());
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.get("reconciledBy").asText()).isEqualTo("bank-accountant");

        JsonNode listed = getJson(
                "/api/accountant/bank-reconciliations/org/" + organizationId + "/bank-account/" + bankAccountId,
                accountant(),
                status().isOk());
        assertThat(arrayContains(listed, row -> row.get("id").asLong() == completed.get("id").asLong())).isTrue();
    }

    @Test
    void csvStatementCanBeImportedAndAutoMatched() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate transactionDate = LocalDate.now();

        long organizationId = postJson(
                "/api/organizations",
                Map.of(
                        "name", "Bank Import Org " + suffix,
                        "registrationNumber", "BRI-REG-" + suffix,
                        "taxId", "BRI-TAX-" + suffix,
                        "active", true),
                financeAdmin(),
                status().isCreated())
                .get("id").asLong();

        long branchId = postJson(
                "/api/branches",
                Map.of(
                        "organizationId", organizationId,
                        "name", "Import Branch " + suffix,
                        "code", "BI-" + suffix,
                        "active", true),
                financeAdmin(),
                status().isCreated())
                .get("id").asLong();

        long cashAccountId = postJson(
                "/api/financeAdmin/chartOfAccounts/org/" + organizationId,
                Map.of(
                        "accountCode", "102-" + suffix,
                        "accountName", "Import Bank " + suffix,
                        "accountType", "ASSET",
                        "accountSubtype", "CURRENT_ASSET",
                        "normalBalance", "DEBIT"),
                financeAdmin(),
                status().isCreated())
                .get("id").asLong();

        long revenueAccountId = postJson(
                "/api/financeAdmin/chartOfAccounts/org/" + organizationId,
                Map.of(
                        "accountCode", "402-" + suffix,
                        "accountName", "Import Revenue " + suffix,
                        "accountType", "REVENUE",
                        "accountSubtype", "REVENUE",
                        "normalBalance", "CREDIT"),
                financeAdmin(),
                status().isCreated())
                .get("id").asLong();

        long bankAccountId = postJson(
                "/api/accountant/bank-accounts/org/" + organizationId,
                Map.of(
                        "chartAccountId", cashAccountId,
                        "bankName", "Import Acceptance Bank",
                        "accountNumber", "001-" + suffix,
                        "accountHolder", "Bank Import Org " + suffix,
                        "currency", "NGN",
                        "balance", BigDecimal.ZERO),
                accountant(),
                status().isCreated())
                .get("id").asLong();

        postJson(
                "/api/financeAdmin/fiscalPeriods/org/" + organizationId,
                Map.of(
                        "year", transactionDate.getYear(),
                        "quarter", quarterFor(transactionDate),
                        "startDate", transactionDate.minusDays(1).toString(),
                        "endDate", transactionDate.plusDays(1).toString()),
                financeAdmin(),
                status().isCreated());

        long journalId = postJson(
                "/api/accountant/manual-journals/org/" + organizationId,
                Map.of(
                        "branchId", branchId,
                        "description", "CSV bank receipt " + suffix,
                        "journalDate", transactionDate.toString()),
                accountant(),
                status().isCreated())
                .get("id").asLong();

        postJson(
                "/api/accountant/manual-journals/" + journalId + "/lines",
                Map.of(
                        "chartOfAccountId", cashAccountId,
                        "debitAmount", new BigDecimal("75.00"),
                        "creditAmount", BigDecimal.ZERO,
                        "narration", "CSV bank deposit",
                        "lineSequence", 1),
                accountant(),
                status().isCreated());

        postJson(
                "/api/accountant/manual-journals/" + journalId + "/lines",
                Map.of(
                        "chartOfAccountId", revenueAccountId,
                        "debitAmount", BigDecimal.ZERO,
                        "creditAmount", new BigDecimal("75.00"),
                        "narration", "CSV revenue",
                        "lineSequence", 2),
                accountant(),
                status().isCreated());

        patchJson("/api/accountant/manual-journals/" + journalId + "/submit", Map.of("submittedBy", "bank-accountant"), accountant(), status().isOk());
        patchJson("/api/cfo/manual-journals/" + journalId + "/approve", Map.of("approvedBy", "bank-cfo", "approvalNote", "CSV import"), cfo(), status().isOk());
        patchJson("/api/accountant/manual-journals/" + journalId + "/post", Map.of("postedBy", "bank-accountant"), accountant(), status().isOk());

        String csv = "transactionDate,amount,description,referenceNumber\n"
                + transactionDate + ",75.00,CSV bank deposit,\n";
        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv", csv.getBytes());

        JsonNode reconciliation = objectMapper.readTree(mockMvc.perform(multipart("/api/accountant/bank-reconciliations/org/" + organizationId + "/import")
                        .file(file)
                        .param("bankAccountId", String.valueOf(bankAccountId))
                        .param("statementDate", transactionDate.toString())
                        .param("openingBalance", "0.00")
                        .param("closingBalance", "75.00")
                        .with(accountant()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(reconciliation.get("importedLineCount").asInt()).isEqualTo(1);
        assertThat(reconciliation.get("matchedLineCount").asInt()).isEqualTo(1);
        assertThat(reconciliation.get("unresolvedDifference").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reconciliation.get("statementLines").get(0).get("matchStatus").asText()).isEqualTo("AUTO_MATCHED");
    }

    private JsonNode postJson(String path, Object body, RequestPostProcessor user, ResultMatcher statusMatcher) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(path)
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(statusMatcher)
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode patchJson(String path, Object body, RequestPostProcessor user, ResultMatcher statusMatcher) throws Exception {
        return objectMapper.readTree(mockMvc.perform(patch(path)
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(statusMatcher)
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode getJson(String path, RequestPostProcessor user, ResultMatcher statusMatcher) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(path)
                        .with(user)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(statusMatcher)
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private RequestPostProcessor financeAdmin() {
        return oidcUser("/financeAdmin", "bank-finance-admin");
    }

    private RequestPostProcessor accountant() {
        return oidcUser("/accountant", "bank-accountant");
    }

    private RequestPostProcessor cfo() {
        return oidcUser("/cfo", "bank-cfo");
    }

    private RequestPostProcessor oidcUser(String group, String username) {
        return oidcLogin()
                .idToken(token -> token
                        .subject(username)
                        .claim("groups", List.of(group))
                        .claim("preferred_username", username));
    }

    private int quarterFor(LocalDate date) {
        return ((date.getMonthValue() - 1) / 3) + 1;
    }

    private boolean arrayContains(JsonNode array, Predicate<JsonNode> predicate) {
        return StreamSupport.stream(array.spliterator(), false).anyMatch(predicate);
    }
}
