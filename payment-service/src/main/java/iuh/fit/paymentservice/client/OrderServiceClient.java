package iuh.fit.paymentservice.client;

import iuh.fit.paymentservice.dto.OrderResponse;
import iuh.fit.paymentservice.dto.UpdateOrderStatusRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service")
public interface OrderServiceClient {

    @GetMapping("/api/v1/orders/{orderId}")
    OrderResponse getOrderById(@PathVariable("orderId") String orderId);

    @PutMapping("/api/v1/orders/{orderId}/status")
    OrderResponse updateOrderStatus(
            @PathVariable("orderId") String orderId,
            @RequestBody UpdateOrderStatusRequest request
    );
}
