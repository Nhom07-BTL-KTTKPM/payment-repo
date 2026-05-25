package iuh.fit.paymentservice.service;

import iuh.fit.paymentservice.client.OrderServiceClient;
import iuh.fit.paymentservice.dto.OrderResponse;
import iuh.fit.paymentservice.dto.PaymentRequest;
import iuh.fit.paymentservice.dto.PaymentResponse;
import iuh.fit.paymentservice.dto.PaymentStatusResponse;
import iuh.fit.paymentservice.dto.UpdateOrderStatusRequest;
import iuh.fit.paymentservice.entity.Payment;
import iuh.fit.paymentservice.entity.PaymentMethod;
import iuh.fit.paymentservice.entity.PaymentStatus;
import iuh.fit.paymentservice.repo.PaymentRepository;
import iuh.fit.shared.error.BusinessException;
import iuh.fit.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    private static final long PAYMENT_EXPIRE_MINUTES = 15;
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final VnPayService vnPayService;
    private final OrderServiceClient orderServiceClient;
    private final PaymentRepository paymentRepository;
    private final String successRedirectUrl;
    private final String failRedirectUrl;

    public PaymentService(
            VnPayService vnPayService,
            OrderServiceClient orderServiceClient,
            PaymentRepository paymentRepository,
            @Value("${payment.redirect.success-url}") String successRedirectUrl,
            @Value("${payment.redirect.fail-url}") String failRedirectUrl
    ) {
        this.vnPayService = vnPayService;
        this.orderServiceClient = orderServiceClient;
        this.paymentRepository = paymentRepository;
        this.successRedirectUrl = successRedirectUrl;
        this.failRedirectUrl = failRedirectUrl;
    }

    public PaymentResponse createPayment(HttpServletRequest request, PaymentRequest paymentRequest) {
        if (paymentRequest == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request body is required");
        }
        UUID orderId = paymentRequest.orderId();
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "orderId is required");
        }

        OrderResponse order = orderServiceClient.getOrderById(orderId.toString());
        if (order == null || order.total() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found");
        }
        if (order.status() == null || !"PENDING".equalsIgnoreCase(order.status())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Order is not pending");
        }
        if (order.total().longValue() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Order amount is invalid");
        }

        String vnpayUrl = vnPayService.createPaymentUrlForOrder(request, order);
        return new PaymentResponse(vnpayUrl);
    }

    public PaymentResponse retryPayment(UUID orderId, HttpServletRequest request) {
        OrderResponse order = orderServiceClient.getOrderById(orderId.toString());
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found");
        }

        if (order.status() == null || !"PENDING".equalsIgnoreCase(order.status())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Order is not pending");
        }

        if (order.orderDate() != null) {
            long minutesElapsed = ChronoUnit.MINUTES.between(order.orderDate(), LocalDateTime.now());
            if (minutesElapsed >= PAYMENT_EXPIRE_MINUTES) {
                orderServiceClient.updateOrderStatus(
                        orderId.toString(),
                    new UpdateOrderStatusRequest("CANCELLED", "Order expired before payment", null)
                );
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "Order expired before payment. Order has been cancelled."
                );
            }
        }

        String vnpayUrl = vnPayService.createPaymentUrlForOrder(request, order);
        return new PaymentResponse(vnpayUrl);
    }

    public void handleVnpayReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int status = vnPayService.orderReturn(request);
        String orderInfo = request.getParameter("vnp_OrderInfo");
        UUID orderId = extractOrderId(orderInfo);

        if (status == 1) {
            try {
                OrderResponse order = orderServiceClient.getOrderById(orderId.toString());
                if (order == null) {
                    response.sendRedirect(buildFailedUrl(orderId));
                    return;
                }

                persistPaymentIfMissing(orderId, request);

                orderServiceClient.updateOrderStatus(
                        orderId.toString(),
                    new UpdateOrderStatusRequest("PROCESSING", null, "PAID")
                );

                response.sendRedirect(buildSuccessUrl(orderId));
            } catch (Exception ex) {
                logger.error("Error handling VNPay return for order {}", orderId, ex);
                response.sendRedirect(buildFailedUrl(orderId));
            }
        } else {
            response.sendRedirect(buildFailedUrl(orderId));
        }
    }

    private void persistPaymentIfMissing(UUID orderId, HttpServletRequest request) {
        Optional<Payment> existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return;
        }

        String transactionId = request.getParameter("vnp_TransactionNo");
        String responseCode = request.getParameter("vnp_ResponseCode");
        String paymentAmount = request.getParameter("vnp_Amount");
        String bankCode = request.getParameter("vnp_BankCode");
        String bankTranNo = request.getParameter("vnp_BankTranNo");

        Long amountValue = parseAmount(paymentAmount);
        BigDecimal amount = amountValue == null ? BigDecimal.ZERO : BigDecimal.valueOf(amountValue);

        String payload = String.format(
                "{\"responseCode\":\"%s\",\"bankCode\":\"%s\",\"bankTranNo\":\"%s\",\"transactionNo\":\"%s\"}",
                responseCode == null ? "" : responseCode,
                bankCode == null ? "" : bankCode,
                bankTranNo == null ? "" : bankTranNo,
                transactionId == null ? "" : transactionId
        );

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setAmount(amount);
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setTransactionId(transactionId);
        payment.setPayload(payload);
        paymentRepository.save(payment);
    }

    private Long parseAmount(String paymentAmount) {
        if (paymentAmount == null || paymentAmount.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(paymentAmount) / 100;
        } catch (NumberFormatException ex) {
            logger.warn("Invalid payment amount value: {}", paymentAmount, ex);
            return null;
        }
    }

    private UUID extractOrderId(String orderInfo) {
        if (orderInfo == null || orderInfo.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "orderInfo is missing");
        }
        Matcher matcher = UUID_PATTERN.matcher(orderInfo);
        if (!matcher.find()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "orderId not found in orderInfo");
        }
        try {
            return UUID.fromString(matcher.group());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid orderId in orderInfo");
        }
    }

    private String buildSuccessUrl(UUID orderId) {
        return appendOrderId(successRedirectUrl, orderId);
    }

    private String buildFailedUrl(UUID orderId) {
        return appendOrderId(failRedirectUrl, orderId);
    }

    private String appendOrderId(String baseUrl, UUID orderId) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return orderId == null ? "" : orderId.toString();
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed + orderId;
        }
        return trimmed + "/" + orderId;
    }

    public PaymentStatusResponse getPaymentStatus(UUID orderId) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "orderId is required");
        }

        OrderResponse order = orderServiceClient.getOrderById(orderId.toString());
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found");
        }

        Optional<Payment> payment = paymentRepository.findByOrderId(orderId);
        if (payment.isPresent()) {
            Payment existing = payment.get();
            return new PaymentStatusResponse(
                    orderId,
                    existing.getPaymentStatus() == null ? "PENDING" : existing.getPaymentStatus().name(),
                    existing.getTransactionId()
            );
        }

        return new PaymentStatusResponse(orderId, PaymentStatus.PENDING.name(), null);
    }
}
