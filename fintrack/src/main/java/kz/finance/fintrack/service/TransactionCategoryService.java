package kz.finance.fintrack.service;

import kz.finance.fintrack.dto.category.CreateCategoryRequest;
import kz.finance.fintrack.dto.category.TransactionCategoryResponseDto;
import kz.finance.fintrack.exception.FinTrackException;
import kz.finance.fintrack.mapper.TransactionCategoryMapper;
import kz.finance.fintrack.model.TransactionCategoryEntity;
import kz.finance.fintrack.model.TransactionType;
import kz.finance.fintrack.repository.TransactionCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionCategoryService {

    private final TransactionCategoryRepository repository;
    private final TransactionCategoryMapper mapper;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<TransactionCategoryResponseDto> getAllByType(TransactionType type) {
       var user = userService.getCurrentUser();
        return repository.findByUserOrSystemAndType(type, user.getId())
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public TransactionCategoryResponseDto createCategory(CreateCategoryRequest request) {
        var user = userService.getCurrentUser();
        var category = TransactionCategoryEntity.builder()
                .nameRu(request.nameRu())
                .nameEn(request.nameEn())
                .icon(request.icon())
                .color(request.color())
                .type(request.type())
                .user(user)
                .system(false)
                .build();
        return mapper.toDto(repository.save(category));
    }

    @Transactional
    public TransactionCategoryResponseDto updateCategory(Long id, CreateCategoryRequest request) {
        var user = userService.getCurrentUser();
        var category = repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new FinTrackException(404, "Category not found or access denied"));
        category.setNameRu(request.nameRu());
        category.setNameEn(request.nameEn());
        category.setIcon(request.icon());
        category.setColor(request.color());
        category.setType(request.type());
        return mapper.toDto(repository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        var user = userService.getCurrentUser();
        var category = repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new FinTrackException(404, "Category not found or access denied"));
        if (category.isSystem()) throw new FinTrackException(400, "Cannot delete system category");
        repository.delete(category);
    }
}
