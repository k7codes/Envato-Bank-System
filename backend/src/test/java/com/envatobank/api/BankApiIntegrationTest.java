package com.envatobank.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BankApiIntegrationTest {

    private static final String AUTHORIZATION = "Authorization";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCanListOwnSeededAccounts() throws Exception {
        String token = login("ayse@example.com", "Demo!2345");

        JsonNode accounts = getAccounts(token);

        assertThat(accounts.isArray()).isTrue();
        assertThat(accounts.size()).isGreaterThanOrEqualTo(2);
        assertThat(accounts.get(0).get("currency").asText()).isEqualTo("TRY");
        assertThat(accounts.get(0).get("balanceMinor").asLong()).isEqualTo(1_000_000L);
    }

    @Test
    void transferMovesFundsAtomicallyAndIsIdempotent() throws Exception {
        String ayse = login("ayse@example.com", "Demo!2345");
        String mehmet = login("mehmet@example.com", "Demo!2345");

        JsonNode ayseAccounts = getAccounts(ayse);
        JsonNode mehmetAccounts = getAccounts(mehmet);
        String sourceId = ayseAccounts.get(0).get("id").asText();
        String targetNo = mehmetAccounts.get(0).get("accountNo").asText();
        long sourceBefore = ayseAccounts.get(0).get("balanceMinor").asLong();
        long targetBefore = mehmetAccounts.get(0).get("balanceMinor").asLong();

        String key = UUID.randomUUID().toString();
        String body = transferBody(sourceId, targetNo, 500L, key);

        String first = postTransfer(ayse, body, 201);
        String second = postTransfer(ayse, body, 201);

        assertThat(json.readTree(second).get("id").asText())
                .isEqualTo(json.readTree(first).get("id").asText());

        JsonNode ayseAfter = getAccounts(ayse);
        JsonNode mehmetAfter = getAccounts(mehmet);
        assertThat(ayseAfter.get(0).get("balanceMinor").asLong()).isEqualTo(sourceBefore - 500L);
        assertThat(mehmetAfter.get(0).get("balanceMinor").asLong()).isEqualTo(targetBefore + 500L);
    }

    @Test
    void cannotTransferFromAnotherCustomersAccount() throws Exception {
        String ayse = login("ayse@example.com", "Demo!2345");
        String mehmet = login("mehmet@example.com", "Demo!2345");

        String ayseSourceId = getAccounts(ayse).get(0).get("id").asText();
        String mehmetTargetNo = getAccounts(mehmet).get(0).get("accountNo").asText();

        postTransfer(mehmet, transferBody(ayseSourceId, mehmetTargetNo, 100L, UUID.randomUUID().toString()), 403);
    }

    @Test
    void rejectsTransferExceedingBalance() throws Exception {
        String ayse = login("ayse@example.com", "Demo!2345");
        String mehmet = login("mehmet@example.com", "Demo!2345");

        String sourceId = getAccounts(ayse).get(0).get("id").asText();
        String targetNo = getAccounts(mehmet).get(0).get("accountNo").asText();

        // Below the per-transaction limit (250000000) but above the account balance (1000000).
        postTransfer(ayse, transferBody(sourceId, targetNo, 200_000_000L, UUID.randomUUID().toString()), 422);
    }

    private String login(String email, String password) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        String response = mvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }

    private JsonNode getAccounts(String token) throws Exception {
        String response = mvc.perform(get("/api/accounts").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private String transferBody(String sourceId, String targetNo, long amountMinor, String key) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sourceAccountId", sourceId);
        payload.put("targetAccountNo", targetNo);
        payload.put("amountMinor", amountMinor);
        payload.put("idempotencyKey", key);
        payload.put("description", "integration test");
        try {
            return json.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String postTransfer(String token, String body, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/transfers")
                        .header(AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }
}
