package com.financial.multitenancy;

import com.financial.multitenancy.dto.CreateAccountRequest;
import com.financial.multitenancy.dto.MoneyRequest;
import com.financial.multitenancy.infra.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests using an in-memory H2 database (test profile).
 * Validates the full HTTP stack including tenant isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    // ------------------------------------------------------------------ //
    // Helpers //
    // ------------------------------------------------------------------ //

    private String tokenFor(String tenantId, String userId) {
        return jwtUtil.generateToken(userId, UUID.fromString(tenantId));
    }

    private UUID createAccount(String token, BigDecimal balance) throws Exception {
        var body = new CreateAccountRequest(UUID.randomUUID(), balance);
        MvcResult result = mockMvc.perform(post("/api/accounts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        var node = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(node.get("id").asText());
    }

    // ------------------------------------------------------------------ //
    // Full HTTP round-trip tests //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("deposit & withdraw — balance should reflect operations correctly")
    void depositAndWithdraw_shouldUpdateBalance() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        String token = tokenFor(tenantId, UUID.randomUUID().toString());
        UUID accountId = createAccount(token, new BigDecimal("100.00"));

        // Deposit 500
        mockMvc.perform(post("/api/accounts/{id}/deposit", accountId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("500.00"), "salary"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(600.00));

        // Withdraw 200
        mockMvc.perform(post("/api/accounts/{id}/withdraw", accountId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("200.00"), "rent"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(400.00));
    }

    @Test
    @DisplayName("withdraw — insufficient funds should return 422")
    void withdraw_insufficientFunds_returns422() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        String token = tokenFor(tenantId, UUID.randomUUID().toString());
        UUID accountId = createAccount(token, new BigDecimal("10.00"));

        mockMvc.perform(post("/api/accounts/{id}/withdraw", accountId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("999.00"), "test"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("tenant isolation — tenant B cannot access tenant A's account")
    void tenantIsolation_differentTenant_returns404() throws Exception {
        // Tenant A creates account
        String tenantA = UUID.randomUUID().toString();
        String tokenA = tokenFor(tenantA, UUID.randomUUID().toString());
        UUID accountIdA = createAccount(tokenA, new BigDecimal("1000.00"));

        // Tenant B tries to get balance of tenant A's account
        String tenantB = UUID.randomUUID().toString();
        String tokenB = tokenFor(tenantB, UUID.randomUUID().toString());

        mockMvc.perform(get("/api/accounts/{id}/balance", accountIdA)
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("unauthenticated request should return 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/balance", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
