import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class OrderService {
    private final ConcurrentMap<String, Order> store = new ConcurrentHashMap<>();

    public List<Order> listAll() {
        return new ArrayList<>(store.values());
    }

    public Optional<Order> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public Order create(String customerId, BigDecimal amount) {
        String id = UUID.randomUUID().toString();
        Order o = new Order(id, customerId, amount);
        store.put(id, o);
        return o;
    }

    public boolean cancel(String id) {
        Order o = store.get(id);
        if (o == null || "CANCELLED".equals(o.getStatus())) {
            return false;
        }
        o.setStatus("CANCELLED");
        return true;
    }
}
