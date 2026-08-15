package com.commercecore.order.client;

import com.commercecore.order.dto.InventoryReservationDto;
import com.commercecore.order.exception.DownstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final RestTemplate restTemplate;
    private final String inventoryServiceUrl;

    public InventoryClient(RestTemplate restTemplate,
                           @Value("${services.inventory-url:http://inventory-service}") String inventoryServiceUrl) {
        this.restTemplate = restTemplate;
        this.inventoryServiceUrl = inventoryServiceUrl;
    }

    public void batchReserve(InventoryReservationDto.BatchReserveRequest request, String correlationId) {
        String url = inventoryServiceUrl + "/api/v1/inventory/batch-reserve";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) headers.set("X-Correlation-Id", correlationId);

        try {
            HttpEntity<InventoryReservationDto.BatchReserveRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<Void> response = restTemplate.postForEntity(url, entity, Void.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new DownstreamServiceException("inventory-service", "Stock reservation returned status " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to reserve stock via inventory-service: {}", e.getMessage());
            throw new DownstreamServiceException("inventory-service", e.getMessage());
        }
    }

    public void batchRelease(InventoryReservationDto.BatchReleaseRequest request, String correlationId) {
        String url = inventoryServiceUrl + "/api/v1/inventory/batch-release";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) headers.set("X-Correlation-Id", correlationId);

        try {
            HttpEntity<InventoryReservationDto.BatchReleaseRequest> entity = new HttpEntity<>(request, headers);
            restTemplate.postForEntity(url, entity, Void.class);
        } catch (Exception e) {
            log.warn("Non-fatal: failed to release stock via inventory-service: {}", e.getMessage());
        }
    }
}
