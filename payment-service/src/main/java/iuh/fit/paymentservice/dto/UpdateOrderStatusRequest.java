package iuh.fit.paymentservice.dto;

public record UpdateOrderStatusRequest(
        String status,
        String cancelReason,
        String paymentStatus
) {
}
