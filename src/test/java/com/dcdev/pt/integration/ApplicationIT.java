package com.dcdev.pt.integration;

import com.dcdev.pt.PtApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = PtApplication.class)
class ApplicationIT {

    @Test
    void contextStartsWithFlywayAndSchemaValidation() {
        // Fails if wiring, Flyway migrations or ddl-auto=validate break startup.
    }
}
