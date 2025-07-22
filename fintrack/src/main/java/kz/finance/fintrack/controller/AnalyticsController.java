package kz.finance.fintrack.controller;

import kz.finance.fintrack.dto.PeriodType;
import kz.finance.fintrack.dto.analytics.AnalyticsCategoriesDto;
import kz.finance.fintrack.dto.analytics.AnalyticsSummaryDto;
import kz.finance.fintrack.service.AnalyticsService;
import kz.finance.fintrack.utils.DateRangeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public AnalyticsSummaryDto getTransactionSummary(
            @RequestParam PeriodType periodType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day
    ) {
        var range = DateRangeResolver.resolve(periodType, year, month, day);
        return analyticsService.getTransactionSummary(range.start(), range.end(), periodType);
    }

    @GetMapping("/categories")
    public AnalyticsCategoriesDto getCategorySummary(
            @RequestParam PeriodType periodType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day,
            @RequestParam(defaultValue = "ru") String lang
    ) {
        var range = DateRangeResolver.resolve(periodType, year, month, day);
        return analyticsService.getCategoriesAnalytics(range.start(), range.end(), lang);
    }
}
