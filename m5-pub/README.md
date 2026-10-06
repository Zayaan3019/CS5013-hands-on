# CS5013 Module 5

## Description

The project contains a Java 17 tax calculator and an in-memory order service with controller code for listing, finding, creating, and cancelling orders.

## Build

Use a Java 17 JDK and GNU Make. From this directory, run:

```sh
make deps && make test
make coverage
make mutation
```

The coverage report is written to `coverage/index.html`; the mutation report is written to `build/reports/pitest/index.html`.

## Quick example

The order service can be used directly from Java:

```java
import java.math.BigDecimal;

OrderService orders = new OrderService();
Order created = orders.create("customer-7", new BigDecimal("49.95"));
OrderDto result = OrderDto.from(created);
```

The controller source describes the REST routes. Local Spring annotation and response stubs let the exercise compile without the Spring dependency. There is no HTTP server bootstrap in this project.

## Contributing

TODO

## License

MIT (placeholder)
