package kz.finance.fintrack.controller;

import jakarta.validation.Valid;
import kz.finance.fintrack.dto.PeriodType;
import kz.finance.fintrack.dto.TransactionRequestDto;
import kz.finance.fintrack.dto.TransactionResponseDto;
import kz.finance.fintrack.model.TransactionType;
import kz.finance.fintrack.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public Page<TransactionResponseDto> getTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) PeriodType periodType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day,
            @RequestParam(required = false) LocalDateTime dateFrom,
            @RequestParam(required = false) LocalDateTime dateTo,
            @RequestHeader(name = "Accept-Language", defaultValue = "ru") String lang,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size);

        return transactionService.getUserTransactionsWithFilters(
                 type, categoryId, periodType, year, month, day, dateFrom, dateTo, lang, pageable
        );
    }

    @PostMapping
    public TransactionResponseDto createTransaction(@Valid @RequestBody TransactionRequestDto request) {
        return transactionService.createTransaction(request);
    }

    @GetMapping("/{id}")
    public TransactionResponseDto getTransactionById(
            @PathVariable Long id,
            @RequestHeader(name = "Accept-Language", defaultValue = "ru") String lang
    ) {
        return transactionService.getTransactionById(id, lang);
    }

    @PutMapping
    public TransactionResponseDto updateTransaction(@Valid @RequestBody TransactionRequestDto request) {
        return transactionService.updateTransaction(request);
    }

    @DeleteMapping("/{id}")
    public void deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
    }

} 