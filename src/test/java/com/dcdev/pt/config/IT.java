package com.dcdev.pt.config;

import com.dcdev.pt.ListingApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for tests that need a full Spring context wired to a real
 * PostgreSQL database, rather than a slice or a mock.
 *
 * <p>Deliberately no {@code @Transactional} here. Database Rider's
 * {@code @DBRider} (wired in {@link IntegrationTestBase}) already owns dataset
 * lifecycle via its own {@code cleanBefore}/{@code cleanAfter}, using a
 * connection it manages itself — wrapping the same test method in a
 * Spring-managed transaction as well would let that connection and the one
 * MockMvc's request thread uses disagree about what is committed (this project
 * turns {@code open-in-view} off deliberately, so a controller's transaction
 * ends before the response is even written), and it would hide statements from
 * the query-count assertions in {@code ItemListingQueryCountIT}, which need to
 * see every statement Hibernate actually issues rather than ones a surrounding
 * test transaction silently rolls back. Each test instead cleans up its own
 * state explicitly (dataset {@code cleanAfter}, or a repository wipe in
 * {@code @BeforeEach}/{@code @AfterEach}), which is also what keeps tests
 * independent of run order.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest(classes = ListingApplication.class)
@ActiveProfiles("test")
public @interface IT {
}
