package iuh.fit.paymentservice.controller;

import iuh.fit.paymentservice.dto.PaymentRequest;
import iuh.fit.paymentservice.dto.PaymentResponse;
import iuh.fit.paymentservice.dto.PaymentStatusResponse;
import iuh.fit.paymentservice.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-payment")
    public ResponseEntity<PaymentResponse> createPayment(
            HttpServletRequest request,
            @RequestBody PaymentRequest paymentRequest
    ) {
        return ResponseEntity.ok(paymentService.createPayment(request, paymentRequest));
    }

    @PostMapping("/retry-payment/{orderId}")
    public ResponseEntity<PaymentResponse> retryPayment(
            @PathVariable UUID orderId,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(paymentService.retryPayment(orderId, request));
    }

    @GetMapping("/vnpay-return")
    public void handleVnpayReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        paymentService.handleVnpayReturn(request, response);
    }

    @GetMapping("/status/{orderId}")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(orderId));
    }
}
