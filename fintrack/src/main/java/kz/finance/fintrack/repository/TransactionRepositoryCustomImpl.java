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
        String format = switch (periodType) {
            case DAY -> "YYYY-MM-DD";
            case MONTH -> "YYYY-MM";
            case YEAR -> "YYYY";
            case WEEK -> "IYYY-IW"; // ISO week
        };

        String formattedExpr = "TO_CHAR(t.date, '" + format + "')";

        String sql = """
        SELECT 
            %s AS date,
            COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS expense
        FROM transaction t
        WHERE t.user_id = :userId AND t.date BETWEEN :start AND :end
        GROUP BY %s
        ORDER BY %s
    """.formatted(formattedExpr, formattedExpr, formattedExpr);

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("start", start);
        query.setParameter("end", end);

        return convertToChartPoints(query.getResultList());
    }

    @Override
    public List<ChartPointDto> getCategoryChartPoints(
            Long userId,
            String categoryName,
            LocalDateTime start,
            LocalDateTime end,
            PeriodType periodType,
            String lang
    ) {
        String dateFormat = switch (periodType) {
            case DAY -> "YYYY-MM-DD";
            case MONTH -> "YYYY-MM";
            case YEAR -> "YYYY";
            case WEEK -> "IYYY-IW";
        };

        String toCharExpr = "TO_CHAR(t.date, '" + dateFormat + "')";
        String categoryCol = lang.equals("ru") ? "c.name_ru" : "c.name_en";

        String sql = """
            SELECT 
                %1$s AS date,
                COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS income,
                COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS expense
            FROM transaction t
            JOIN transaction_category c ON t.category_id = c.id
            WHERE t.user_id = :userId
              AND %2$s = :categoryName
              AND t.date BETWEEN :start AND :end
            GROUP BY %1$s
            ORDER BY %1$s
        """.formatted(toCharExpr, categoryCol);

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("userId", userId);
        query.setParameter("categoryName", categoryName);
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

