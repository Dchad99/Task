package com.dcdev.pt.item;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lets the UI populate its filter/add-item controls without hard-coding the enum. */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @GetMapping
    public List<Category> list() {
        return List.of(Category.values());
    }
}
