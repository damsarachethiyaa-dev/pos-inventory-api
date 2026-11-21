package com.chethiya.posapi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PosApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    @Test
    void unauthenticatedRequestsAreRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cashierCannotCreateProductsButAdminCan() throws Exception {
        String cashierToken = loginAndGetToken("cashier", "Cashier@1234");

        Map<String, Object> payload = Map.of(
                "sku", "SKU-RBAC-01",
                "barcode", "8909999000011",
                "name", "RBAC Test Item",
                "category", "Test",
                "unitPrice", 250.00,
                "stockQuantity", 15,
                "reorderLevel", 5);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullPosLifecycleCheckoutConflictRollbackAndVoidRestore() throws Exception {
        String adminToken = loginAndGetToken("admin", "Admin@1234");
        String cashierToken = loginAndGetToken("cashier", "Cashier@1234");

        // 1. Admin creates product with stock = 10, reorderLevel = 5
        Map<String, Object> createPayload = Map.of(
                "sku", "SKU-INT-100",
                "barcode", "8908888100100",
                "name", "Mechanical Barcode Scanner",
                "category", "Hardware",
                "unitPrice", 500.00,
                "stockQuantity", 10,
                "reorderLevel", 5);

        MvcResult created = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockQuantity").value(10))
                .andReturn();

        long productId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // 2. Cashier looks up product by barcode
        mockMvc.perform(get("/api/v1/products/barcode/8908888100100")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-INT-100"));

        // 3. Cashier checks out 6 units -> stock becomes 4 (triggers low-stock!)
        Map<String, Object> checkoutPayload = Map.of(
                "paymentMethod", "CARD",
                "items", List.of(Map.of("productId", productId, "quantity", 6)));

        MvcResult saleResult = mockMvc.perform(post("/api/v1/sales/checkout")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(3000.00))
                .andExpect(jsonPath("$.taxAmount").value(240.00))
                .andExpect(jsonPath("$.totalAmount").value(3240.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();

        JsonNode saleJson = objectMapper.readTree(saleResult.getResponse().getContentAsString());
        long saleId = saleJson.get("id").asLong();
        String receiptNumber = saleJson.get("receiptNumber").asText();

        // 4. Attempting to buy 5 more when only 4 remain returns 409 Conflict (ProblemDetail)
        Map<String, Object> overdrawPayload = Map.of(
                "paymentMethod", "CASH",
                "items", List.of(Map.of("productId", productId, "quantity", 5)));

        mockMvc.perform(post("/api/v1/sales/checkout")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overdrawPayload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient Stock"));

        // 5. Low-stock alerts include this product (stock 4 <= reorderLevel 5)
        MvcResult lowStock = mockMvc.perform(get("/api/v1/products/low-stock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(lowStock.getResponse().getContentAsString()).contains("SKU-INT-100");

        // 6. Receipt lookup works
        mockMvc.perform(get("/api/v1/sales/receipt/" + receiptNumber)
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashierUsername").value("cashier"));

        // 7. Admin voids sale -> stock restored back to 10
        mockMvc.perform(post("/api/v1/sales/" + saleId + "/void")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));

        mockMvc.perform(get("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(10));

        // 8. Admin restocks +15 -> stock becomes 25
        mockMvc.perform(patch("/api/v1/products/" + productId + "/restock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(25));
    }

    @Test
    void healthEndpointIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
