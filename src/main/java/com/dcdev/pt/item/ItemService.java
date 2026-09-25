package com.dcdev.pt.item;


import com.dcdev.pt.common.ItemNotFoundException;
import com.dcdev.pt.item.dto.CreateItemRequest;
import com.dcdev.pt.item.dto.ItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Service
public class ItemService {

    private final ItemRepository repository;
    private final Clock clock;

    public ItemService(ItemRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Search, filter, sort and paginate — all in the database. Exactly one data
     * query plus (at most) one count query, regardless of page size or table size.
     */
    @Transactional(readOnly = true)
    public Page<ItemResponse> search(String q, Category category, Integer page, Integer size, String sort) {
        Pageable pageable = ItemQueryParser.toPageable(page, size, sort);

        String searchText = ItemQueryParser.toSearchText(q);

        List<Specification<Item>> filters = new ArrayList<>();
        if (searchText != null) {
            filters.add(ItemSpecifications.textMatches(searchText));
        }
        if (category != null) {
            filters.add(ItemSpecifications.hasCategory(category));
        }

        // allOf(...) of an empty list is a concrete "match everything" Specification,
        // never null — which also keeps findAll(Specification, Pageable) unambiguous
        // against QueryByExampleExecutor.findAll(Example, Pageable).
        return repository.findAll(Specification.allOf(filters), pageable).map(ItemResponse::from);
    }

    @Transactional(readOnly = true)
    public ItemResponse findById(long id) {
        return repository.findById(id)
                .map(ItemResponse::from)
                .orElseThrow(() -> new ItemNotFoundException(id));
    }

    @Transactional
    public ItemResponse create(CreateItemRequest request) {
        // Input is already normalised by CreateItemRequest's constructor.
        Item item = new Item(
                request.name(),
                request.category(),
                request.description(),
                request.price(),
                clock.instant());
        return ItemResponse.from(repository.save(item));
    }

    /**
     * Deleting an unknown id is a 404, not a silent 204. Trade-off: not idempotent
     * in the strict HTTP sense (a repeated delete is 404 the second time), chosen
     * so the UI can tell the user their view was stale. See README.
     */
    @Transactional
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new ItemNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
