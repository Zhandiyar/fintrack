package kz.finance.fintrack.controller;

import kz.finance.fintrack.dto.analytics.DashboardDto;
import kz.finance.fintrack.service.AnalyticsService;
import kz.finance.fintrack.utils.DateRangeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public DashboardDto getDashboard(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day,
            @RequestHeader(name = "Accept-Language", defaultValue = "ru") String lang
    ) {
        var range = DateRangeResolver.resolve(year, month, day);
        return analyticsService.getDashboard(range.start(), range.end(), lang);
    }
} 