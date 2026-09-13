package rw.centrika.orders;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderManagementApplicationTests {

    @Test
    void contextLoads() {
        // Fails fast if wiring is broken (missing bean, bad JPA mapping,
        // misconfigured converter, etc.) — cheap smoke test to run in CI
        // before anything else.
    }
}
