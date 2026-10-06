# CS5013 Module 5

## Description

A Java 17 exercise project with tax-calculation methods and an in-memory order API for listing, finding, creating, and cancelling orders.

## Build

Use a Java 17 JDK and GNU Make. Run `make deps && make test` to compile the sources and run the unit tests. Run `make coverage` for the JaCoCo report or `make mutation` for the PIT report.

## Quick example

```java
import java.math.BigDecimal;

OrderService orders = new OrderService();
Order created = orders.create("customer-7", new BigDecimal("49.95"));
OrderDto result = OrderDto.from(created);
```

The `OrderApi` class describes the REST routes. This project includes local Spring annotation and response stubs for compilation; it does not include an HTTP server bootstrap.

## Contributing

TODO

## License

MIT (placeholder)
