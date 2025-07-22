package kz.finance.fintrack.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import kz.finance.fintrack.dto.PeriodType;
import kz.finance.fintrack.dto.analytics.ChartPointDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TransactionRepositoryCustomImpl implements TransactionRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public List<ChartPointDto> getChartData(Long userId, LocalDateTime start, LocalDateTime end, PeriodType periodType) {
        String dateFormat = switch (periodType) {
            case DAY, WEEK, MONTH -> "YYYY-MM-DD"; // для всех срезов, где нужна детализация по дням
            case YEAR -> "YYYY-MM";                // детализация по месяцам
        };

        String sql = """
                    SELECT TO_CHAR(t.date, '%s') as label,
                           COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) as income,
                           COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) as expense
                    FROM transaction t
                    WHERE t.user_id = :userId
                      AND t.date BETWEEN :start AND :end
                    GROUP BY label
                    ORDER BY label
                """.formatted(dateFormat);

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("start", start);
        query.setParameter("end", end);

        return convertToChartPoints(query.getResultList());
    }

    private List<ChartPointDto> convertToChartPoints(List<?> rawData) {
        return rawData.stream()
                .map(row -> {
                    Object[] arr = (Object[]) row;
                    return new ChartPointDto(
                            (String) arr[0],
                            (BigDecimal) arr[1],
                            (BigDecimal) arr[2]
                    );
                })
                .collect(Collectors.toList());
    }
}

