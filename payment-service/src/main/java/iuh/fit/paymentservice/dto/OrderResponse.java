package iuh.fit.paymentservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderResponse(
        UUID id,
        UUID customerId,
        String status,
        BigDecimal total,
        LocalDateTime orderDate
) {
}
