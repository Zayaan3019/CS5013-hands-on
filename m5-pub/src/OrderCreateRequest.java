import java.math.BigDecimal;

public record OrderCreateRequest(String customerId, BigDecimal amount) {}
