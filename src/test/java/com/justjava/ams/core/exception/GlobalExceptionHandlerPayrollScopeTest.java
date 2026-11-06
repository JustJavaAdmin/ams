package com.justjava.ams.core.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.ams.common.controller.OrganizationApiController;
import com.justjava.ams.common.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class GlobalExceptionHandlerPayrollScopeTest {
    private final OrganizationService organizationService = mock(OrganizationService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new OrganizationApiController(organizationService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void malformedNonPayrollRequestKeepsOriginalGlobalErrorResponse() throws Exception {
        var response = mockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andReturn()
                .getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body.get("message").asText()).isEqualTo("An unexpected error occurred");
        assertThat(body.get("errorCode").asText()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(body.get("status").asInt()).isEqualTo(500);
        assertThat(body.get("path").asText()).isEqualTo("/api/organizations");
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.get("fieldErrors").isNull()).isTrue();
    }

    @Test
    void nonPayrollDataIntegrityFailureKeepsOriginalGlobalErrorResponse() throws Exception {
        doThrow(new DataIntegrityViolationException("forced legacy database detail"))
                .when(organizationService).createOrganization(any());

        var response = mockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Legacy test organization","registrationNumber":"LEGACY-TEST"}
                                """))
                .andReturn()
                .getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(body.get("message").asText()).isEqualTo("forced legacy database detail");
        assertThat(body.get("errorCode").asText()).isEqualTo("CONFLICT");
        assertThat(body.get("status").asInt()).isEqualTo(409);
        assertThat(body.get("path").asText()).isEqualTo("/api/organizations");
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.get("fieldErrors").isNull()).isTrue();
    }
}
