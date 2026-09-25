package com.dcdev.pt.config;


import com.dcdev.pt.item.Category;
import com.dcdev.pt.item.Item;
import com.dcdev.pt.item.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds a realistic catalogue at startup so the listing page is meaningful from
 * the first request.
 *
 * <p>Fixed random seed: every run produces the same catalogue, so bugs are
 * reproducible. Timestamps are truncated to the hour, which guarantees many items
 * share a {@code createdAt} — this is what makes the id sort tie-breaker
 * observable with real data, not just in a unit test.
 */
@Configuration
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class SeedDataConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SeedDataConfiguration.class);

    private static final long RANDOM_SEED = 20260923L;
    private static final int BATCH_SIZE = 500;
    private static final int HISTORY_DAYS = 180;

    private static final String[] STYLES = {
            "Watercolour", "Floral", "Minimalist", "Hand-Lettered", "Vintage", "Foil-Stamped",
            "Recycled Kraft", "Pop-Up", "Photographic", "Geometric", "Botanical"
    };

    private static final String[] PRODUCT_TYPES = {
            "Card", "Gift Bag", "Wrapping Paper", "Gift Tag Set", "Bunting", "Balloon Set",
            "Scented Candle", "Notebook", "Keepsake Box"
    };

    private static final String[] OTHER_OCCASIONS = {"Everyday", "New Home", "Good Luck", "Get Well"};

    @Bean
    public ApplicationRunner seedItems(
            ItemRepository repository, Clock clock, @Value("${app.seed.count:2000}") int count) {

        return args -> {
            if (repository.count() > 0) {
                log.info("Skipping seed: {} items already present.", repository.count());
                return;
            }

            long startedAt = System.currentTimeMillis();
            Random random = new Random(RANDOM_SEED);
            Instant now = clock.instant().truncatedTo(ChronoUnit.HOURS);

            List<Item> batch = new ArrayList<>(BATCH_SIZE);
            for (int i = 0; i < count; i++) {
                batch.add(generateItem(random, now));
                if (batch.size() == BATCH_SIZE) {
                    repository.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                repository.saveAll(batch);
            }

            log.info("Seeded {} items in {} ms.", count, System.currentTimeMillis() - startedAt);
        };
    }

    private static Item generateItem(Random random, Instant now) {
        Category category = Category.values()[random.nextInt(Category.values().length)];
        String occasion = occasionFor(category, random);
        String style = STYLES[random.nextInt(STYLES.length)];
        String productType = PRODUCT_TYPES[random.nextInt(PRODUCT_TYPES.length)];

        String name = style + " " + occasion + " " + productType;
        if (random.nextInt(4) == 0) {
            name += " (Pack of 6)";
        }

        String description = random.nextInt(100) < 6
                ? null
                : "A " + style.toLowerCase() + " " + productType.toLowerCase() + " for " + occasion.toLowerCase() + ".";

        BigDecimal price = random.nextInt(100) < 4
                ? null
                : BigDecimal.valueOf(1.50 + random.nextDouble() * 48.0).setScale(2, RoundingMode.HALF_UP);

        Instant createdAt = now.minus(Duration.ofHours(random.nextInt(HISTORY_DAYS * 24)));

        return new Item(name, category, description, price, createdAt);
    }

    private static String occasionFor(Category category, Random random) {
        return switch (category) {
            case BIRTHDAY -> "Birthday";
            case WEDDING -> "Wedding";
            case ANNIVERSARY -> "Anniversary";
            case THANK_YOU -> "Thank You";
            case CHRISTMAS -> "Christmas";
            case OTHER -> OTHER_OCCASIONS[random.nextInt(OTHER_OCCASIONS.length)];
        };
    }
}
