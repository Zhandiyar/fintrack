package kz.finance.fintrack.controller;

import kz.finance.fintrack.dto.ai.FinanceAnalyzeRequest;
import kz.finance.fintrack.dto.ai.FinanceAnalyzeResponse;
import kz.finance.fintrack.service.AiFinanceAnalysisService;
import kz.finance.fintrack.service.QuickFinanceAnalyzeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiFinanceAnalysisController {

    private final AiFinanceAnalysisService aiService;
    private final QuickFinanceAnalyzeService quickFinanceAnalyzeService;

    @PostMapping("/quick-analyze")
    public FinanceAnalyzeResponse quickAnalyze(@RequestBody FinanceAnalyzeRequest request) {
        return quickFinanceAnalyzeService.quickAnalyze(request.year(), request.month(), request.currency());
    }

    @PostMapping("/deep-analyze")
    public FinanceAnalyzeResponse analyze(@RequestBody FinanceAnalyzeRequest request) {
        return aiService.analyzeMonth(request.year(), request.month(), request.currency());
    }
}
