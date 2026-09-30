import java.math.BigDecimal;

public record OrderDto(String id, String customerId, BigDecimal amount, String status) {
    public static OrderDto from(Order o) {
        return new OrderDto(o.getId(), o.getCustomerId(), o.getAmount(), o.getStatus());
    }
}
