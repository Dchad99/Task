-- Schema for the Item entity (com.dcdev.pt.item.Item).
--
-- The sequence increments by 50 to match @SequenceGenerator(allocationSize = 50)
-- on the entity: Hibernate's default "pooled" optimizer pre-allocates blocks of
-- ids client-side sized to allocationSize, and assumes the real database
-- sequence advances by the same amount per call. A mismatch here would cause
-- silent id collisions under concurrent inserts.
CREATE SEQUENCE item_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE items (
    id          BIGINT NOT NULL,
    name        VARCHAR(255) NOT NULL,
    category    VARCHAR(32) NOT NULL,
    description VARCHAR(1000),
    price       DECIMAL(10, 2),
    created_at  TIMESTAMP NOT NULL,
    CONSTRAINT pk_items PRIMARY KEY (id)
);

CREATE INDEX idx_items_category ON items (category);
CREATE INDEX idx_items_created_at_id ON items (created_at, id);
