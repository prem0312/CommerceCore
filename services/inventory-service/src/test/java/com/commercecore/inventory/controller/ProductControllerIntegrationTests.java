package com.commercecore.inventory.controller;

import com.commercecore.inventory.dto.ProductRequest;
import com.commercecore.inventory.model.Product;
import com.commercecore.inventory.repository.InventoryRepository;
import com.commercecore.inventory.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void setup() {
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/products should create product and initial inventory")
    void testCreateProduct_Admin_Success() throws Exception {
        ProductRequest request = new ProductRequest("Gaming Monitor", "4K 144Hz IPS", new BigDecimal("399.99"), "Monitors", 50, 5);

        mockMvc.perform(post("/api/v1/products")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Gaming Monitor"))
                .andExpect(jsonPath("$.price").value(399.99))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/products should return list of products")
    void testListProducts_Success() throws Exception {
        Product p = new Product("Smart Watch", "Fitness tracker", new BigDecimal("199.99"), "Wearables", true);
        productRepository.save(p);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Smart Watch"));
    }
}
