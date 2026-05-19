package iuh.fit.paymentservice.dto;

import java.util.UUID;

public record PaymentStatusResponse(
        UUID orderId,
        String status,
        String transactionId
) {
}
