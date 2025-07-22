package kz.finance.fintrack.service;

import kz.finance.fintrack.dto.PeriodType;
import kz.finance.fintrack.dto.TransactionRequestDto;
import kz.finance.fintrack.dto.TransactionResponseDto;
import kz.finance.fintrack.exception.FinTrackException;
import kz.finance.fintrack.mapper.TransactionMapper;
import kz.finance.fintrack.model.TransactionEntity;
import kz.finance.fintrack.model.TransactionType;
import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.TransactionCategoryRepository;
import kz.finance.fintrack.repository.TransactionRepository;
import kz.finance.fintrack.utils.DateRangeResolver;
import kz.finance.fintrack.utils.TransactionSpecificationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final UserService userService;
    private final TransactionMapper mapper;

    public Page<TransactionResponseDto> getUserTransactionsWithFilters(
            TransactionType type,
            Long categoryId,
            PeriodType periodType,
            Integer year,
            Integer month,
            Integer day,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            String lang,
            Pageable pageable
    ) {
        UserEntity currentUser = userService.getCurrentUser();

        Specification<TransactionEntity> spec = Specification.where(TransactionSpecificationUtil.userEquals(currentUser));

        if (type != null) {
            spec = spec.and(TransactionSpecificationUtil.typeEquals(type));
        }
        if (categoryId != null) {
            spec = spec.and(TransactionSpecificationUtil.categoryEquals(categoryId));
        }

        // Используем DateRangeResolver только если periodType задан
        if (periodType != null) {
            var range = DateRangeResolver.resolve(periodType, year, month, day);
            spec = spec.and(TransactionSpecificationUtil.dateBetween(range.start(), range.end()));
        } else if (dateFrom != null || dateTo != null) {
            spec = spec.and(TransactionSpecificationUtil.dateBetween(dateFrom, dateTo));
        }

        return transactionRepository.findAll(spec, pageable)
                .map(transaction -> mapper.toDto(transaction, lang));
    }

    @Transactional
    public TransactionResponseDto createTransaction(TransactionRequestDto request) {
        UserEntity currentUser = userService.getCurrentUser();
        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new FinTrackException(BAD_REQUEST.value(), "Category not found"));

        if (category.getType() != request.type()) {
            throw new FinTrackException(BAD_REQUEST.value(), "Category type does not match transaction type");
        }

        var transaction = TransactionEntity.builder()
                .amount(request.amount())
                .date(request.date())
                .comment(request.comment())
                .type(request.type())
                .category(category)
                .user(currentUser)
                .build();

        return mapper.toDto(transactionRepository.save(transaction), request.lang());
    }

    @Transactional
    public TransactionResponseDto updateTransaction(TransactionRequestDto request) {
        if (request.id() == null) {
            throw new FinTrackException(BAD_REQUEST.value(), "Transaction ID is required for update");
        }

        UserEntity currentUser = userService.getCurrentUser();
        var transaction = transactionRepository.findByIdAndUser(request.id(), currentUser)
                .orElseThrow(() -> new FinTrackException(BAD_REQUEST.value(), "Transaction not found"));

        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new FinTrackException(BAD_REQUEST.value(), "Category not found"));

        if (category.getType() != request.type()) {
            throw new FinTrackException(BAD_REQUEST.value(), "Category type does not match transaction type");
        }

        transaction.setAmount(request.amount());
        transaction.setDate(request.date());
        transaction.setComment(request.comment());
        transaction.setType(request.type());
        transaction.setCategory(category);

        return mapper.toDto(transactionRepository.save(transaction), request.lang());
    }

    public TransactionResponseDto getTransactionById(Long id, String lang) {
        UserEntity currentUser = userService.getCurrentUser();
        return transactionRepository.findByIdAndUser(id, currentUser)
                .map(entity -> mapper.toDto(entity, lang))
                .orElseThrow(() -> new FinTrackException(BAD_REQUEST.value(), "Transaction not found"));
    }

    @Transactional
    public void deleteTransaction(Long id) {
        UserEntity currentUser = userService.getCurrentUser();
        var transaction = transactionRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new FinTrackException(BAD_REQUEST.value(), "Transaction not found"));
        transactionRepository.delete(transaction);
    }
} 