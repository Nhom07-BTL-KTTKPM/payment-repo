package iuh.fit.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "cart-service")
public interface CartServiceClient {

    @DeleteMapping("/api/v1/carts/{customerId}")
    void clearCart(@PathVariable("customerId") String customerId);
}
