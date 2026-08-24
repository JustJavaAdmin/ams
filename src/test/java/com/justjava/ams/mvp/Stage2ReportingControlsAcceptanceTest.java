package com.justjava.ams.mvp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Stage2ReportingControlsAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void stage2ReportingControlsFlowWorksThroughApis() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate journalDate = LocalDate.now();

        long organizationId = postJson(
                "/api/organizations",
                Map.of(
                        "name", "Stage 2 Acceptance Org " + suffix,
                        "registrationNumber", "S2-REG-" + suffix,
                        "taxId", "S2-TAX-" + suffix,
                        "active", true),
                financeAdmin(),
                status().isCreated())
                .get("id").asLong();

        long branchId = postJson(
                "/api/branches",
                Map.of(
                        "organizationId", organizationId,
                        "name", "Stage 2 Branch " + suffix,
                        "code", "S2-" + suffix,
                        "active", true),
                financeAdmin(),
                status().isCreated())
                .get("id").asLong();

        long cashAccountId = postJson(
                "/api/financeAdmin/chartOfAccounts/org/" + organizationId,
                Map.of(
                        "accountCode", "101-" + suffix,
                        "accountName", "Cash " + suffix,
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

        postJson(
                "/api/financeAdmin/fiscalPeriods/org/" + organizationId,
                Map.of(
                        "year", journalDate.getYear(),
                        "quarter", quarterFor(journalDate),
                        "startDate", journalDate.minusDays(1).toString(),
                        "endDate", journalDate.plusDays(1).toString()),
                financeAdmin(),
                status().isCreated());

        long journalId = postJson(
                "/api/accountant/manual-journals/org/" + organizationId,
                Map.of(
                        "branchId", branchId,
                        "description", "Stage 2 posted journal " + suffix,
                        "journalDate", journalDate.toString()),
                accountant(),
                status().isCreated())
                .get("id").asLong();

        postJson(
                "/api/accountant/manual-journals/" + journalId + "/lines",
                Map.of(
                        "chartOfAccountId", cashAccountId,
                        "debitAmount", new BigDecimal("100.00"),
                        "creditAmount", BigDecimal.ZERO,
                        "narration", "Cash debit",
                        "lineSequence", 1),
                accountant(),
                status().isCreated());

        postJson(
                "/api/accountant/manual-journals/" + journalId + "/lines",
                Map.of(
                        "chartOfAccountId", revenueAccountId,
                        "debitAmount", BigDecimal.ZERO,
                        "creditAmount", new BigDecimal("100.00"),
                        "narration", "Revenue credit",
                        "lineSequence", 2),
                accountant(),
                status().isCreated());

        patchJson(
                "/api/accountant/manual-journals/" + journalId + "/submit",
                Map.of("submittedBy", "stage2-accountant"),
                accountant(),
                status().isOk());

        patchJson(
                "/api/cfo/manual-journals/" + journalId + "/approve",
                Map.of("approvedBy", "stage2-cfo", "approvalNote", "Approved for Stage 2 acceptance"),
                cfo(),
                status().isOk());

        patchJson(
                "/api/accountant/manual-journals/" + journalId + "/post",
                Map.of("postedBy", "stage2-accountant"),
                accountant(),
                status().isOk());

        JsonNode glRows = getJson(
                "/api/accountant/general-ledger/journal/" + journalId,
                accountant(),
                status().isOk());
        assertThat(glRows.size()).isEqualTo(2);

        JsonNode defaultModules = postJson(
                "/api/financeAdmin/module-controls/org/" + organizationId + "/defaults",
                Map.of(),
                financeAdmin(),
                status().isCreated());
        JsonNode modules = defaultModules.get("modules");
        assertModulePresent(modules, "GENERAL_LEDGER");
        assertModulePresent(modules, "REPORTING");
        assertModulePresent(modules, "AUDIT");
        assertModulePresent(modules, "APPROVALS");

        long reportingModuleId = findRequired(modules, module -> "REPORTING".equals(module.get("moduleType").asText()))
                .get("id").asLong();

        patchJson(
                "/api/financeAdmin/module-controls/" + reportingModuleId + "/toggle",
                Map.of("enabled", false, "reason", "Stage 2 acceptance toggle off"),
                financeAdmin(),
                status().isOk());

        patchJson(
                "/api/financeAdmin/module-controls/" + reportingModuleId + "/toggle",
                Map.of("enabled", true, "reason", "Stage 2 acceptance toggle on"),
                financeAdmin(),
                status().isOk());

        JsonNode trialBalance = getJson(
                "/api/cfo/trial-balance/org/" + organizationId + "?asOfDate=" + journalDate,
                cfo(),
                status().isOk());
        assertThat(arrayContains(trialBalance.get("lines"), line -> line.get("accountId").asLong() == cashAccountId)).isTrue();
        assertThat(arrayContains(trialBalance.get("lines"), line -> line.get("accountId").asLong() == revenueAccountId)).isTrue();
        assertThat(decimal(trialBalance, "totalDebits")).isEqualByComparingTo(decimal(trialBalance, "totalCredits"));
        assertThat(trialBalance.get("balanced").asBoolean()).isTrue();

        postJson(
                "/api/cfo/trial-balance/org/" + organizationId + "/snapshots",
                Map.of(
                        "asOfDate", journalDate.toString(),
                        "persistSnapshot", true,
                        "generatedBy", "stage2-cfo"),
                cfo(),
                status().isOk());

        JsonNode incomeStatement = postJson(
                "/api/cfo/financial-reports/org/" + organizationId + "/generate",
                Map.of(
                        "reportType", "INCOME_STATEMENT",
                        "fromDate", journalDate.minusDays(1).toString(),
                        "toDate", journalDate.plusDays(1).toString(),
                        "generatedBy", "stage2-cfo",
                        "persist", true),
                cfo(),
                status().isOk());
        assertThat(decimal(incomeStatement, "totalRevenue")).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(decimal(incomeStatement, "netIncome")).isEqualByComparingTo(new BigDecimal("100.00"));

        JsonNode balanceSheet = postJson(
                "/api/cfo/financial-reports/org/" + organizationId + "/generate",
                Map.of(
                        "reportType", "BALANCE_SHEET",
                        "fromDate", journalDate.minusDays(1).toString(),
                        "toDate", journalDate.plusDays(1).toString(),
                        "generatedBy", "stage2-cfo",
                        "persist", true),
                cfo(),
                status().isOk());
        assertThat(decimal(balanceSheet, "totalAssets")).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(balanceSheet.hasNonNull("balanceSheetVariance")).isTrue();

        JsonNode cashFlow = postJson(
                "/api/cfo/financial-reports/org/" + organizationId + "/generate",
                Map.of(
                        "reportType", "CASH_FLOW",
                        "fromDate", journalDate.minusDays(1).toString(),
                        "toDate", journalDate.plusDays(1).toString(),
                        "generatedBy", "stage2-cfo",
                        "persist", true),
                cfo(),
                status().isOk());
        assertThat(cashFlow.get("reportType").asText()).isEqualTo("CASH_FLOW");
        assertThat(cashFlow.get("summaryMetrics").has("netChangeInCash")).isTrue();

        JsonNode equity = postJson(
                "/api/cfo/financial-reports/org/" + organizationId + "/generate",
                Map.of(
                        "reportType", "EQUITY",
                        "fromDate", journalDate.minusDays(1).toString(),
                        "toDate", journalDate.plusDays(1).toString(),
                        "generatedBy", "stage2-cfo",
                        "persist", true),
                cfo(),
                status().isOk());
        assertThat(equity.get("reportType").asText()).isEqualTo("EQUITY");
        assertThat(equity.get("summaryMetrics").has("closingEquity")).isTrue();

        JsonNode budgetVariance = postJson(
                "/api/cfo/financial-reports/org/" + organizationId + "/generate",
                Map.of(
                        "reportType", "BUDGET_VARIANCE",
                        "fromDate", journalDate.minusDays(1).toString(),
                        "toDate", journalDate.plusDays(1).toString(),
                        "generatedBy", "stage2-cfo",
                        "persist", true),
                cfo(),
                status().isOk());
        assertThat(budgetVariance.get("reportType").asText()).isEqualTo("BUDGET_VARIANCE");
        assertThat(budgetVariance.get("summaryMetrics").has("totalVariance")).isTrue();

        JsonNode customReport = postJson(
                "/api/cfo/financial-reports/org/" + organizationId + "/generate",
                Map.of(
                        "reportType", "CUSTOM",
                        "fromDate", journalDate.minusDays(1).toString(),
                        "toDate", journalDate.plusDays(1).toString(),
                        "generatedBy", "stage2-cfo",
                        "persist", true),
                cfo(),
                status().isOk());
        assertThat(customReport.get("reportType").asText()).isEqualTo("CUSTOM");
        assertThat(customReport.get("summaryMetrics").has("total")).isTrue();

        JsonNode securityEvents = getJson(
                "/api/auditor/security-events/org/" + organizationId,
                auditor(),
                status().isOk());
        assertThat(arrayContains(securityEvents, event -> "CONFIGURATION_CHANGE".equals(event.get("eventType").asText()))).isTrue();
        JsonNode reportEvent = findRequired(securityEvents,
                event -> "REPORT_GENERATION".equals(event.get("eventType").asText()));

        JsonNode acknowledged = patchJson(
                "/api/auditor/security-events/" + reportEvent.get("id").asLong() + "/acknowledge",
                Map.of("acknowledgedBy", "stage2-auditor"),
                auditor(),
                status().isOk());
        assertThat(acknowledged.get("acknowledged").asBoolean()).isTrue();
        assertThat(acknowledged.get("acknowledgedBy").asText()).isNotBlank();

        JsonNode unacknowledged = getJson(
                "/api/auditor/security-events/org/" + organizationId + "/unacknowledged",
                auditor(),
                status().isOk());
        assertThat(arrayContains(unacknowledged,
                event -> event.get("id").asLong() == acknowledged.get("id").asLong())).isFalse();
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
        return oidcUser("/financeAdmin", "stage2-finance-admin");
    }

    private RequestPostProcessor accountant() {
        return oidcUser("/accountant", "stage2-accountant");
    }

    private RequestPostProcessor cfo() {
        return oidcUser("/cfo", "stage2-cfo");
    }

    private RequestPostProcessor auditor() {
        return oidcUser("/auditor", "stage2-auditor");
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

    private void assertModulePresent(JsonNode modules, String moduleType) {
        assertThat(arrayContains(modules, module -> moduleType.equals(module.get("moduleType").asText()))).isTrue();
    }

    private JsonNode findRequired(JsonNode array, Predicate<JsonNode> predicate) {
        return StreamSupport.stream(array.spliterator(), false)
                .filter(predicate)
                .findFirst()
                .orElseThrow();
    }

    private boolean arrayContains(JsonNode array, Predicate<JsonNode> predicate) {
        return StreamSupport.stream(array.spliterator(), false).anyMatch(predicate);
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        return node.get(fieldName).decimalValue();
    }
}
