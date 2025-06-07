package kz.finance.fintrack.repository;

import kz.finance.fintrack.dto.CategoryExpenseDto;
import kz.finance.fintrack.dto.ExpenseAggregationDto;
import kz.finance.fintrack.dto.ExpenseDto;
import kz.finance.fintrack.dto.analytics.ChartDataDto;
import kz.finance.fintrack.model.ExpenseCategory;
import kz.finance.fintrack.model.ExpenseEntity;
import kz.finance.fintrack.model.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {
    List<ExpenseEntity> findByUser(UserEntity user);

    Optional<ExpenseEntity> findByIdAndUser(Long expenseId, UserEntity currentUser);
    @Query("""
        SELECT new kz.finance.fintrack.dto.analytics.ChartDataDto(
            TO_CHAR(e.date, 'Mon'), SUM(e.amount))
        FROM ExpenseEntity e
        WHERE e.user = :user AND e.date BETWEEN :start AND :end
        GROUP BY TO_CHAR(e.date, 'Mon')
        ORDER BY MIN(e.date)
    """)
    List<ChartDataDto> findMonthlyTotals(@Param("user") UserEntity user,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    @Query("""
    SELECT new kz.finance.fintrack.dto.analytics.ChartDataDto(TO_CHAR(e.date, 'YYYY-MM-DD'), SUM(e.amount))
    FROM ExpenseEntity e
    WHERE e.user = :user AND e.date BETWEEN :start AND :end
    GROUP BY TO_CHAR(e.date, 'YYYY-MM-DD')
    ORDER BY TO_CHAR(e.date, 'YYYY-MM-DD')
""")
    List<ChartDataDto> findDailyTotals(@Param("user") UserEntity user,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);


    @Query("""
        SELECT new kz.finance.fintrack.dto.analytics.ChartDataDto(
            TO_CHAR(e.date, 'Dy'), SUM(e.amount))
        FROM ExpenseEntity e
        WHERE e.user = :user AND e.date BETWEEN :start AND :end
        GROUP BY TO_CHAR(e.date, 'Dy')
        ORDER BY MIN(e.date)
    """)
    List<ChartDataDto> findWeekdayTotals(@Param("user") UserEntity user,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

   /**
     * Сумма расходов по категориям
     */
     @Query("""
        SELECT new kz.finance.fintrack.dto.CategoryExpenseDto(e.category, SUM(e.amount))
        FROM ExpenseEntity e
        WHERE e.user = :user AND e.date BETWEEN :start AND :end
        GROUP BY e.category
        HAVING SUM(e.amount) > 0
    """)
    List<CategoryExpenseDto> findExpenseSumGroupedByCategory(@Param("user") UserEntity user,
                                                             @Param("start") LocalDateTime start,
                                                             @Param("end") LocalDateTime end);

    /**
     * Получение агрегированных данных по расходам (день, неделя, месяц, год)
     */
    @Query("""
                SELECT new kz.finance.fintrack.dto.ExpenseAggregationDto(
                    COALESCE(SUM(e.amount), 0),
                    COALESCE(SUM(CASE WHEN DATE_TRUNC('day', CAST(e.date AS TIMESTAMP)) = DATE_TRUNC('day', CAST(:date AS TIMESTAMP)) THEN e.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN e.date BETWEEN :startOfWeek AND :endOfWeek THEN e.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN DATE_TRUNC('month', CAST(e.date AS TIMESTAMP)) = DATE_TRUNC('month', CAST(:targetDate AS TIMESTAMP)) THEN e.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN DATE_TRUNC('year', CAST(e.date AS TIMESTAMP)) = DATE_TRUNC('year', CAST(:targetDate AS TIMESTAMP)) THEN e.amount ELSE 0 END), 0)
                )
                    FROM ExpenseEntity e WHERE e.user = :user AND DATE_TRUNC('year', CAST(e.date AS TIMESTAMP)) = DATE_TRUNC('year', CAST(:targetDate AS TIMESTAMP))
            """)
    ExpenseAggregationDto findAggregatedExpenses(
            @Param("user") UserEntity user,
            @Param("date") LocalDateTime date,
            @Param("startOfWeek") LocalDateTime startOfWeek,
            @Param("endOfWeek") LocalDateTime endOfWeek,
            @Param("targetDate") LocalDateTime targetDate
    );

    /**
     * Получение списка расходов по категории (без преобразования в DTO)
     */
    @Query("""
        SELECT new kz.finance.fintrack.dto.ExpenseDto(e.id, e.category, e.amount, e.description, e.date)
        FROM ExpenseEntity e
        WHERE e.user = :user AND e.category = :category
        ORDER BY e.date DESC
    """)
    List<ExpenseDto> findExpensesByCategoryDto(
            @Param("user") UserEntity user,
            @Param("category") ExpenseCategory category,
            Pageable pageable
    );
}
