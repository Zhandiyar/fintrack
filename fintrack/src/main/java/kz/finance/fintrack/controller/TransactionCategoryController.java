package kz.finance.fintrack.controller;

import kz.finance.fintrack.dto.category.CreateCategoryRequest;
import kz.finance.fintrack.dto.category.TransactionCategoryResponseDto;
import kz.finance.fintrack.model.TransactionType;
import kz.finance.fintrack.service.TransactionCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class TransactionCategoryController {

    private final TransactionCategoryService categoryService;

    @GetMapping
    public List<TransactionCategoryResponseDto> getAllByType(
            @RequestParam("type") TransactionType type
    ) {
        return categoryService.getAllByType(type);
    }

    @PostMapping
    public TransactionCategoryResponseDto createCategory(@RequestBody CreateCategoryRequest request) {
        return categoryService.createCategory(request);
    }

    @PutMapping("/{id}")
    public TransactionCategoryResponseDto updateCategory(@PathVariable Long id, @RequestBody CreateCategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
    }
}
