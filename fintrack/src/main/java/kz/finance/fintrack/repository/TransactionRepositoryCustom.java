package kz.finance.fintrack.repository;

import kz.finance.fintrack.dto.PeriodType;
import kz.finance.fintrack.dto.analytics.ChartPointDto;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepositoryCustom {
    List<ChartPointDto> getChartData(Long userId, LocalDateTime start, LocalDateTime end, PeriodType periodType);

    List<ChartPointDto> getCategoryChartPoints(
            Long userId,
            String categoryName,
            LocalDateTime start,
            LocalDateTime end,
            PeriodType periodType,
            String lang
    );
}
