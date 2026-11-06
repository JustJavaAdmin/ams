package com.justjava.ams.integration;

import com.justjava.ams.core.config.AuthenticationManager;
import com.justjava.ams.core.config.IntegrationSecurityConfig;
import com.justjava.ams.integration.controller.PayrollJournalIntegrationController;
import com.justjava.ams.integration.dto.PayrollJournalResponse;
import com.justjava.ams.integration.exception.IntegrationApiExceptionHandler;
import com.justjava.ams.integration.service.ClientEntitlementService;
import com.justjava.ams.integration.service.PayrollJournalPostingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayrollJournalIntegrationController.class)
@Import({IntegrationSecurityConfig.class, IntegrationApiExceptionHandler.class})
class PayrollJournalIntegrationApiTest {
    private static final String POST_URL = "/api/integrations/organizations/91/payroll-journals";
    private static final String GET_URL =
            "/api/integrations/organizations/91/payroll-journals/by-key?idempotencyKey=JUSTHR%3APAY-1%3AORIGINAL";
    private static final String VALID_BODY = """
            {
              "sourceSystem":"JUSTHR",
              "journalReference":"PAY-1",
              "revision":"ORIGINAL",
              "payrollPeriodId":"PERIOD-1",
              "periodStart":"2026-10-01",
              "periodEnd":"2026-10-31",
              "currency":"NGN",
              "lines":[
                {"accountCode":"5000","accountName":"Salary","lineType":"PAYROLL","debitAmount":100.00,"creditAmount":0.00},
                {"accountCode":"2000","accountName":"Clearing","lineType":"PAYROLL","debitAmount":0.00,"creditAmount":100.00}
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientEntitlementService entitlementService;

    @MockitoBean
    private PayrollJournalPostingService postingService;

    @Test
    void postScopeSucceedsAndReadScopeSucceeds() throws Exception {
        doNothing().when(entitlementService).assertEntitled(any(), eq(91L));
        when(postingService.post(any(), any(), any(), any())).thenReturn(
                new PayrollJournalResponse(12L, "POSTED", "JUSTHR:PAY-1:ORIGINAL",
                        LocalDate.of(2026, 10, 31), new BigDecimal("100.00"),
                        new BigDecimal("100.00"), LocalDateTime.now(), false));

        mockMvc.perform(post(POST_URL)
                        .header("Idempotency-Key", "JUSTHR:PAY-1:ORIGINAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(jwt().jwt(token -> token.claim("azp", "jjeip-ams")
                                .claim("scope", "payroll-journal:post"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"));

        mockMvc.perform(get(GET_URL)
                        .with(jwt().jwt(token -> token.claim("azp", "jjeip-ams")
                                .claim("scope", "payroll-journal:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void wrongScopesAndMissingTokenAreRejected() throws Exception {
        mockMvc.perform(post(POST_URL)
                        .header("Idempotency-Key", "JUSTHR:PAY-1:ORIGINAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(jwt().jwt(token -> token.claim("scope", "payroll-journal:read"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(GET_URL)
                        .with(jwt().jwt(token -> token.claim("scope", "payroll-journal:post"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(GET_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(POST_URL)
                        .header("Idempotency-Key", "JUSTHR:PAY-1:ORIGINAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        verify(entitlementService, never()).assertEntitled(any(), any());
    }

    @Test
    void malformedJsonMissingHeaderAndWrongContentTypeUseSafeErrorShape() throws Exception {
        var caller = jwt().jwt(token -> token.claim("azp", "jjeip-ams")
                .claim("scope", "payroll-journal:post"));
        mockMvc.perform(post(POST_URL)
                        .header("Idempotency-Key", "JUSTHR:PAY-1:ORIGINAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{")
                        .with(caller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(post(POST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(caller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post(POST_URL)
                        .header("Idempotency-Key", "JUSTHR:PAY-1:ORIGINAL")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_BODY)
                        .with(caller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void dataIntegrityFailureDoesNotExposeDatabaseDetails() throws Exception {
        doNothing().when(entitlementService).assertEntitled(any(), eq(91L));
        doThrow(new DataIntegrityViolationException("private database detail"))
                .when(postingService).post(any(), any(), any(), any());

        mockMvc.perform(post(POST_URL)
                        .header("Idempotency-Key", "JUSTHR:PAY-1:ORIGINAL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .with(jwt().jwt(token -> token.claim("azp", "jjeip-ams")
                                .claim("scope", "payroll-journal:post"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                .andExpect(content().string(not(containsString("private database detail"))))
                .andExpect(content().string(not(containsString("DataIntegrityViolationException"))));
    }
}
