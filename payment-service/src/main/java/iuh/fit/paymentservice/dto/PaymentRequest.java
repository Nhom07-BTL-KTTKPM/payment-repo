package iuh.fit.paymentservice.dto;

import java.util.UUID;

public record PaymentRequest(
        UUID orderId
) {
}
