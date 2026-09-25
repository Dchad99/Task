package com.dcdev.pt.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single catalogue item. Deliberately flat: no relationships, so a listing
 * query can never multiply rows or trigger lazy loading.
 *
 * <p>The actual schema (table, columns, sequence, indexes) is owned by the Flyway
 * migration at {@code db/migration/V1__create_items_table.sql} — Hibernate only
 * validates against it ({@code ddl-auto: validate}). The {@code @Table(indexes = ...)}
 * below is not used to generate DDL in that mode; it's kept as documentation that
 * stays next to the entity, and would matter again if a test ever ran with
 * {@code ddl-auto: create-drop} instead.
 */
@Entity
@Table(
        name = "items",
        indexes = {
                @Index(name = "idx_items_category", columnList = "category"),
                @Index(name = "idx_items_created_at_id", columnList = "created_at, id")
        })
public class Item {

    /**
     * SEQUENCE rather than IDENTITY so Hibernate can batch inserts — matters once
     * we seed 2,000 rows at startup.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "item_seq")
    @SequenceGenerator(name = "item_seq", sequenceName = "item_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Category category;

    @Column(length = 1000)
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected Item() {
    }

    public Item(String name, Category category, String description, BigDecimal price, Instant createdAt) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
