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
class ApprovalRulesAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void approvalRuleIsAppliedToSubmittedManualJournal() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate journalDate = LocalDate.now();

        long organizationId = postJson(
                "/api/organizations",
                Map.of(
                        "name", "Approval Rules Org " + suffix,
                        "registrationNumber", "APR-REG-" + suffix,
                        "taxId", "APR-TAX-" + suffix,
                        "active", true),
                financeAdmin(),
                status().isCreated())
                .get("id").asLong();

        long branchId = postJson(
                "/api/branches",
                Map.of(
                        "organizationId", organizationId,
                        "name", "Approval Branch " + suffix,
                        "code", "APR-" + suffix,
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

        JsonNode approvalRule = postJson(
                "/api/financeAdmin/approval-rules/org/" + organizationId,
                Map.of(
                        "ruleName", "High value manual journals " + suffix,
                        "moduleType", "GENERAL_LEDGER",
                        "transactionType", "MANUAL_JOURNAL",
                        "minAmount", new BigDecimal("1000.00"),
                        "accountType", "ASSET",
                        "requiredApprovals", 2,
                        "approverRole", "CFO",
                        "priority", 1,
                        "active", true),
                financeAdmin(),
                status().isCreated());

        long journalId = postJson(
                "/api/accountant/manual-journals/org/" + organizationId,
                Map.of(
                        "branchId", branchId,
                        "description", "Approval rule journal " + suffix,
                        "journalDate", journalDate.toString()),
                accountant(),
                status().isCreated())
                .get("id").asLong();

        postJson(
                "/api/accountant/manual-journals/" + journalId + "/lines",
                Map.of(
                        "chartOfAccountId", cashAccountId,
                        "debitAmount", new BigDecimal("1500.00"),
                        "creditAmount", BigDecimal.ZERO,
                        "departmentCode", "OPS",
                        "narration", "Cash debit",
                        "lineSequence", 1),
                accountant(),
                status().isCreated());

        postJson(
                "/api/accountant/manual-journals/" + journalId + "/lines",
                Map.of(
                        "chartOfAccountId", revenueAccountId,
                        "debitAmount", BigDecimal.ZERO,
                        "creditAmount", new BigDecimal("1500.00"),
                        "departmentCode", "OPS",
                        "narration", "Revenue credit",
                        "lineSequence", 2),
                accountant(),
                status().isCreated());

        JsonNode submitted = patchJson(
                "/api/accountant/manual-journals/" + journalId + "/submit",
                Map.of("submittedBy", "approval-accountant"),
                accountant(),
                status().isOk());
        assertThat(submitted.get("approvalRuleId").asLong()).isEqualTo(approvalRule.get("id").asLong());
        assertThat(submitted.get("approvalRuleName").asText()).isEqualTo(approvalRule.get("ruleName").asText());
        assertThat(submitted.get("requiredApprovals").asInt()).isEqualTo(2);

        JsonNode pending = getJson(
                "/api/cfo/manual-journals/org/" + organizationId + "/pending",
                cfo(),
                status().isOk());
        JsonNode pendingJournal = findRequired(pending, row -> row.get("journalId").asLong() == journalId);
        assertThat(pendingJournal.get("approvalRuleId").asLong()).isEqualTo(approvalRule.get("id").asLong());
        assertThat(pendingJournal.get("requiredApprovals").asInt()).isEqualTo(2);

        JsonNode auditLogs = getJson(
                "/api/auditor/audit-logs/org/" + organizationId + "?entityType=ApprovalRule",
                auditor(),
                status().isOk());
        assertThat(arrayContains(auditLogs, log -> "CREATE".equals(log.get("action").asText()))).isTrue();
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
        return oidcUser("/financeAdmin", "approval-finance-admin");
    }

    private RequestPostProcessor accountant() {
        return oidcUser("/accountant", "approval-accountant");
    }

    private RequestPostProcessor cfo() {
        return oidcUser("/cfo", "approval-cfo");
    }

    private RequestPostProcessor auditor() {
        return oidcUser("/auditor", "approval-auditor");
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

    private JsonNode findRequired(JsonNode array, Predicate<JsonNode> predicate) {
        return StreamSupport.stream(array.spliterator(), false)
                .filter(predicate)
                .findFirst()
                .orElseThrow();
    }

    private boolean arrayContains(JsonNode array, Predicate<JsonNode> predicate) {
        return StreamSupport.stream(array.spliterator(), false).anyMatch(predicate);
    }
}
