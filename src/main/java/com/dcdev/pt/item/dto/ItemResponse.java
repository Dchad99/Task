package com.dcdev.pt.item.dto;

import com.dcdev.pt.item.Category;
import com.dcdev.pt.item.Item;

import java.math.BigDecimal;
import java.time.Instant;

public record ItemResponse(
        Long id,
        String name,
        Category category,
        String description,
        BigDecimal price,
        Instant createdAt) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getCategory(),
                item.getDescription(),
                item.getPrice(),
                item.getCreatedAt());
    }
}
