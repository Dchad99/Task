package com.dcdev.pt.integration.repository;

import com.dcdev.pt.config.IntegrationTestBase;
import com.dcdev.pt.config.SqlStatementRecorder.RecordedStatement;
import com.dcdev.pt.item.Category;
import com.dcdev.pt.item.Item;
import com.dcdev.pt.item.ItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@code hibernate.jdbc.batch_size: 50} from application.yml: saving many
 * items (as the seed does) must reach the driver as JDBC batches, not as one
 * INSERT round trip per row. Without the setting this sees 120 executions.
 */
class ItemInsertBatchingIT extends IntegrationTestBase {

    private static final int ROWS = 120;

    @Autowired
    private ItemRepository repository;

    @Test
    @DisplayName("saving 120 items sends 3 JDBC batches (50 + 50 + 20), not 120 INSERTs")
    void insertsAreSentInBatches() {
        List<Item> items = new ArrayList<>(ROWS);
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < ROWS; i++) {
            items.add(new Item("Batch Item " + i, Category.OTHER, null, BigDecimal.ONE, createdAt));
        }

        sqlRecorder.reset();
        repository.saveAll(items);

        List<RecordedStatement> inserts = sqlRecorder.statements().stream()
                .filter(RecordedStatement::isInsert)
                .filter(statement -> statement.touchesTable("items"))
                .toList();
        assertThat(inserts)
                .as("rows per JDBC execution")
                .extracting(statement -> statement.parameterSets().size())
                .containsExactly(50, 50, 20);
        assertThat(repository.count()).isEqualTo(ROWS);
    }
}
