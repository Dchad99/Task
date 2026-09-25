package com.dcdev.pt.unit.service;

import com.dcdev.pt.common.ItemNotFoundException;
import com.dcdev.pt.item.Category;
import com.dcdev.pt.item.Item;
import com.dcdev.pt.item.ItemRepository;
import com.dcdev.pt.item.ItemService;
import com.dcdev.pt.item.dto.CreateItemRequest;
import com.dcdev.pt.item.dto.ItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service behaviour in isolation: no database, no Spring context. Covers the
 * decisions the service owns — which filters get applied, how input is
 * normalised, and what happens when an item is missing.
 */
@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-23T10:15:30Z");

    @Mock
    private ItemRepository repository;

    private ItemService service;

    @BeforeEach
    void setUp() {
        service = new ItemService(repository, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    /**
     * {@code any(Specification.class)} (not the bare {@code any()}) throughout this
     * class: {@code ItemRepository} extends both
     * {@code JpaSpecificationExecutor.findAll(Specification, Pageable)} and, via
     * {@code JpaRepository}, {@code QueryByExampleExecutor.findAll(Example, Pageable)}.
     * An untyped matcher leaves the compiler unable to pick an overload.
     */
    @Test
    @DisplayName("no filters still builds a concrete match-all specification, never a bare null")
    void searchWithoutFiltersBuildsMatchAllSpecification() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.search("   ", null, 0, 25, null);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("search and category filters are both applied")
    void searchWithFiltersBuildsSpecification() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.search("birthday", Category.BIRTHDAY, 2, 50, "name,asc");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort()).containsExactly(
                new Sort.Order(Sort.Direction.ASC, "name"),
                new Sort.Order(Sort.Direction.ASC, "id"));
    }

    @Test
    @DisplayName("results are mapped to response DTOs, never entities")
    void searchMapsEntitiesToResponses() {
        Item item = new Item("Floral Birthday Card", Category.BIRTHDAY, "Nice", new BigDecimal("4.50"), FIXED_NOW);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(item)));

        Page<ItemResponse> result = service.search(null, null, 0, 25, null);

        assertThat(result.getContent()).singleElement().satisfies(response -> {
            assertThat(response.name()).isEqualTo("Floral Birthday Card");
            assertThat(response.category()).isEqualTo(Category.BIRTHDAY);
        });
    }

    @Test
    @DisplayName("create stores the normalised request and stamps the clock")
    void createNormalisesInput() {
        when(repository.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new CreateItemRequest("  Vintage Wedding Card  ", Category.WEDDING, "   ", new BigDecimal("9.99")));

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(repository).save(saved.capture());

        assertThat(saved.getValue().getName()).isEqualTo("Vintage Wedding Card");
        assertThat(saved.getValue().getDescription()).isNull();
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("deleting a known item removes it")
    void deleteRemovesKnownItem() {
        when(repository.existsById(7L)).thenReturn(true);

        service.delete(7L);

        verify(repository).deleteById(7L);
    }

    @Test
    @DisplayName("deleting an unknown item is a not-found, not a silent success")
    void deleteUnknownItemThrows() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ItemNotFoundException.class)
                .hasMessageContaining("99");

        verify(repository, never()).deleteById(any());
    }

    @Test
    @DisplayName("fetching an unknown item is a not-found")
    void findByIdUnknownThrows() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(42L)).isInstanceOf(ItemNotFoundException.class);
    }
}
