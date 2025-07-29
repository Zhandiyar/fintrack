package kz.finance.fintrack.controller;

import kz.finance.fintrack.dto.ai.FinanceAnalyzeRequest;
import kz.finance.fintrack.dto.ai.FinanceAnalyzeResponse;
import kz.finance.fintrack.service.AiFinanceAnalysisService;
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

    @PostMapping("/analyze")
    public FinanceAnalyzeResponse analyze(@RequestBody FinanceAnalyzeRequest request) {
        String result = aiService.analyzeMonth(request.year(), request.month());
        return new FinanceAnalyzeResponse(result);
    }
}
