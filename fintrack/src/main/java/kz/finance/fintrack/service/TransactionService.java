package kz.finance.fintrack.service;

import kz.finance.fintrack.dto.TransactionRequestDto;
import kz.finance.fintrack.dto.TransactionResponseDto;
import kz.finance.fintrack.exception.FinTrackException;
import kz.finance.fintrack.mapper.TransactionMapper;
import kz.finance.fintrack.model.TransactionEntity;
import kz.finance.fintrack.model.TransactionType;
import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.TransactionCategoryRepository;
import kz.finance.fintrack.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final UserService userService;
    private final TransactionMapper mapper;

    public Page<TransactionResponseDto> getUserTransactions(Pageable pageable) {
        UserEntity currentUser = userService.getCurrentUser();
        return transactionRepository.findByUser(currentUser, pageable)
                .map(mapper::toDto);
    }

    public Page<TransactionResponseDto> getUserTransactionsByType(TransactionType type, Pageable pageable) {
        UserEntity currentUser = userService.getCurrentUser();
        return transactionRepository.findByUserAndType(currentUser, type, pageable)
                .map(mapper::toDto);
    }

    public Page<TransactionResponseDto> getUserTransactionsByCategory(Long categoryId, Pageable pageable) {
        UserEntity currentUser = userService.getCurrentUser();
        return transactionRepository.findByUserAndCategory_Id(currentUser, categoryId, pageable)
                .map(mapper::toDto);
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

        return mapper.toDto(transactionRepository.save(transaction));
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

        return mapper.toDto(transactionRepository.save(transaction));
    }

    public TransactionResponseDto getTransactionById(Long id) {
        UserEntity currentUser = userService.getCurrentUser();
        return transactionRepository.findByIdAndUser(id, currentUser)
                .map(mapper::toDto)
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