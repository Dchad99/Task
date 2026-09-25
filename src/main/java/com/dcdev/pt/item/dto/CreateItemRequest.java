package com.dcdev.pt.item.dto;

import com.dcdev.pt.item.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for creating an item. A DTO rather than the entity, so a client
 * can never set {@code id} or {@code createdAt}, and the public API can evolve
 * independently of the persistence model.
 */
public record CreateItemRequest(

        @NotBlank(message = "name must not be blank")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotNull(message = "category is required")
        Category category,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @DecimalMin(value = "0.00", message = "price must not be negative")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 8 integer and 2 fraction digits")
        BigDecimal price) {

    /**
     * Normalises input at the boundary. Jackson builds the record through this
     * canonical constructor and Bean Validation runs afterwards, so constraints
     * check the value that will actually be stored: surrounding whitespace is
     * stripped (Unicode-aware {@code strip()}, not ASCII-only {@code trim()}),
     * and a blank description means "no description" and becomes {@code null}.
     * A whitespace-only name becomes {@code ""} and is rejected by {@code @NotBlank}.
     */
    public CreateItemRequest {
        name = name == null ? null : name.strip();
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
