package com.dcdev.pt.item;

import com.dcdev.pt.item.dto.CreateItemRequest;
import com.dcdev.pt.item.dto.ItemResponse;
import com.dcdev.pt.item.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    /**
     * {@code GET /api/items?q=&category=&page=0&size=25&sort=createdAt,desc}
     *
     * <p>Parameters are validated and defaulted in one place ({@link ItemQueryParser})
     * rather than via Spring Data's {@code Pageable} argument resolver or
     * {@code defaultValue} here, so the rules are explicit, unit-testable, cannot
     * drift apart, and produce our own error shape.
     */
    @GetMapping
    public PageResponse<ItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {

        return PageResponse.from(service.search(q, category, page, size, sort));
    }

    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
        ItemResponse created = service.create(request);
        URI location = UriComponentsBuilder.fromPath("/api/items/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}