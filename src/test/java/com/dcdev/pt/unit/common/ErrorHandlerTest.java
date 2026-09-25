package com.dcdev.pt.unit.common;

import com.dcdev.pt.common.ApiError;
import com.dcdev.pt.common.ErrorHandler;
import com.dcdev.pt.common.InvalidRequestException;
import com.dcdev.pt.common.ItemNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorHandlerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-02T03:04:05Z");

    private final ErrorHandler handler = new ErrorHandler(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("error timestamp comes from the injected Clock")
    void timestampComesFromClock() {
        ResponseEntity<ApiError> response = handler.handleNotFound(
                new ItemNotFoundException(42), new MockHttpServletRequest("GET", "/api/items/42"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isEqualTo(FIXED_NOW);
        assertThat(response.getBody().path()).isEqualTo("/api/items/42");
    }

    @Test
    @DisplayName("bad-request errors use the same Clock")
    void badRequestTimestampComesFromClock() {
        ResponseEntity<ApiError> response = handler.handleInvalidRequest(
                new InvalidRequestException("nope"), new MockHttpServletRequest("GET", "/api/items"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().timestamp()).isEqualTo(FIXED_NOW);
    }
}
