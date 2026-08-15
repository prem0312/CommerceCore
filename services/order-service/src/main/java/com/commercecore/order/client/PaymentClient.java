package com.commercecore.order.client;

import com.commercecore.order.dto.PaymentInitiationDto;
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
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestTemplate restTemplate;
    private final String paymentServiceUrl;

    public PaymentClient(RestTemplate restTemplate,
                         @Value("${services.payment-url:http://payment-service}") String paymentServiceUrl) {
        this.restTemplate = restTemplate;
        this.paymentServiceUrl = paymentServiceUrl;
    }

    public PaymentInitiationDto.PaymentResponse initiatePayment(
            PaymentInitiationDto.PaymentRequest request, String idempotencyKey, String correlationId) {
        String url = paymentServiceUrl + "/api/v1/payments";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
        if (correlationId != null) headers.set("X-Correlation-Id", correlationId);

        try {
            HttpEntity<PaymentInitiationDto.PaymentRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<PaymentInitiationDto.PaymentResponse> response =
                    restTemplate.postForEntity(url, entity, PaymentInitiationDto.PaymentResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to initiate payment with payment-service: {}", e.getMessage());
            throw new DownstreamServiceException("payment-service", e.getMessage());
        }
    }
}
