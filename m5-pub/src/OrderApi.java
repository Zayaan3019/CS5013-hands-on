import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/orders", produces = "application/json")
public class OrderApi {
    private final OrderService service;

    public OrderApi() {
        this(new OrderService());
    }

    public OrderApi(OrderService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /**
     * List all orders.
     *
     * @return all orders currently held by the service; their order is unspecified
     */
    @GetMapping
    public List<OrderDto> listOrders() {
        return service.listAll().stream().map(OrderDto::from).toList();
    }

    /**
     * Find an order by its identifier.
     *
     * @param id the nonblank order identifier to look up
     * @return HTTP 200 with the order when found, HTTP 400 for a blank identifier, or HTTP 404
     *         with an error body when no order has that identifier
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> findOrder(@PathVariable("id") String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest(new ErrorResponse("id must not be blank."));
        }
        var order = service.findById(id);
        if (order.isEmpty()) {
            return ResponseEntity.notFound(new ErrorResponse("Order not found."));
        }
        return ResponseEntity.ok(OrderDto.from(order.get()));
    }

    /**
     * Create an order for a customer.
     *
     * @param request the JSON request containing a nonblank customer ID and a positive amount;
     *                surrounding whitespace is removed from the customer ID
     * @return HTTP 201 with the created order, or HTTP 400 with an error body when the request is invalid
     */
    @PostMapping(consumes = "application/json")
    public ResponseEntity<?> createOrder(@RequestBody OrderCreateRequest request) {
        if (request == null || request.customerId() == null || request.customerId().isBlank()) {
            return ResponseEntity.badRequest(
                    new ErrorResponse("customerId must contain a non-whitespace character."));
        }
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0) {
            return ResponseEntity.badRequest(new ErrorResponse("amount must be greater than zero."));
        }

        String customerId = request.customerId().trim();
        Order created = service.create(customerId, amount);
        return ResponseEntity.of(HttpStatus.CREATED, OrderDto.from(created));
    }

    /**
     * Cancel an order.
     *
     * @param id the nonblank order identifier to cancel
     * @return HTTP 204 when the order is cancelled, HTTP 400 for a blank identifier, or HTTP 404
     *         with an error body when it is absent or already cancelled
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelOrder(@PathVariable("id") String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest(new ErrorResponse("id must not be blank."));
        }
        if (service.cancel(id)) {
            return ResponseEntity.noContent();
        }
        return ResponseEntity.notFound(
                new ErrorResponse("Order not found or already cancelled."));
    }
}
