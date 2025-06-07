package kz.finance.fintrack.repository;

import kz.finance.fintrack.dto.TransactionResponseDto;
import kz.finance.fintrack.dto.analytics.AnalyticsCategoryTotalDto;
import kz.finance.fintrack.dto.analytics.AnalyticsStatsDto;
import kz.finance.fintrack.dto.analytics.DashboardStatsDto;
import kz.finance.fintrack.model.CategorySummaryProjection;
import kz.finance.fintrack.model.TransactionEntity;
import kz.finance.fintrack.model.TransactionType;
import kz.finance.fintrack.model.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long>, TransactionRepositoryCustom {
    @Query("""
                SELECT t FROM TransactionEntity t
                WHERE t.user = :user
                ORDER BY t.date DESC
            """)
    Page<TransactionEntity> findByUser(@Param("user") UserEntity user, Pageable pageable);

    @Query("""
                SELECT t FROM TransactionEntity t
                WHERE t.user = :user AND t.type = :type
                ORDER BY t.date DESC
            """)
    Page<TransactionEntity> findByUserAndType(
            @Param("user") UserEntity user,
            @Param("type") TransactionType type,
            Pageable pageable
    );

    @Query("""
                SELECT t FROM TransactionEntity t
                WHERE t.user = :user AND t.category.id = :categoryId
                ORDER BY t.date DESC
            """)
    Page<TransactionEntity> findByUserAndCategory_Id(
            @Param("user") UserEntity user,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );

    Optional<TransactionEntity> findByIdAndUser(Long id, UserEntity user);

    @Query("""
                SELECT new kz.finance.fintrack.dto.TransactionResponseDto(
                    t.id,
                    t.amount,
                    t.date,
                    t.createdAt,
                    t.updatedAt,
                    t.comment,
                    t.type,
                    c.id,
                    c.nameRu,
                    c.nameEn
                )
                FROM TransactionEntity t
                JOIN t.category c
                WHERE t.user = :user
                ORDER BY t.date DESC
            """)
    List<TransactionResponseDto> findRecentTransactions(@Param("user") UserEntity user, Pageable pageable);


    @Query("""
                SELECT new kz.finance.fintrack.dto.analytics.DashboardStatsDto(
                    COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 'INCOME' AND t.date BETWEEN :currentStart AND :currentEnd THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' AND t.date BETWEEN :currentStart AND :currentEnd THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 'INCOME' AND t.date BETWEEN :previousStart AND :previousEnd THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' AND t.date BETWEEN :previousStart AND :previousEnd THEN t.amount ELSE 0 END), 0)
                )
                FROM TransactionEntity t
                WHERE t.user = :user
            """)
    DashboardStatsDto getDashboardStats(
            @Param("user") UserEntity user,
            @Param("currentStart") LocalDateTime currentStart,
            @Param("currentEnd") LocalDateTime currentEnd,
            @Param("previousStart") LocalDateTime previousStart,
            @Param("previousEnd") LocalDateTime previousEnd
    );


    @Query("""
                SELECT new kz.finance.fintrack.dto.analytics.AnalyticsStatsDto(
                    COALESCE(SUM(CASE WHEN t.type = 'INCOME' AND t.date BETWEEN :start AND :end THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' AND t.date BETWEEN :start AND :end THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 'INCOME' AND t.date BETWEEN :prevStart AND :prevEnd THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' AND t.date BETWEEN :prevStart AND :prevEnd THEN t.amount ELSE 0 END), 0)
                )
                FROM TransactionEntity t
                WHERE t.user = :user
            """)
    AnalyticsStatsDto getSummaryStats(
            @Param("user") UserEntity user,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("prevStart") LocalDateTime prevStart,
            @Param("prevEnd") LocalDateTime prevEnd
    );

    @Query(
            value = """
                    SELECT 
                        c.id AS categoryId,
                        c.name_ru AS nameRu,
                        c.name_en AS nameEn,
                        COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS totalIncome,
                        COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS totalExpense
                    FROM transaction t
                    JOIN transaction_category c ON t.category_id = c.id
                    WHERE t.user_id = :userId AND t.date BETWEEN :start AND :end
                    GROUP BY c.id, c.name_ru, c.name_en
                    ORDER BY
                        CASE :lang
                            WHEN 'en' THEN c.name_en
                            ELSE c.name_ru
                        END
                    """,
            nativeQuery = true
    )
    List<CategorySummaryProjection> getCategorySummaryNative(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("lang") String lang
    );

    @Query("""
                SELECT new kz.finance.fintrack.dto.analytics.AnalyticsCategoryTotalDto(
                    COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0)
                )
                FROM TransactionEntity t
                JOIN t.category c
                WHERE t.user = :user AND
                      ((:lang = 'ru' AND c.nameRu = :categoryName) OR
                       (:lang = 'en' AND c.nameEn = :categoryName)) AND
                      t.date BETWEEN :start AND :end
            """)
    AnalyticsCategoryTotalDto getCategoryTotals(
            @Param("user") UserEntity user,
            @Param("categoryName") String categoryName,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("lang") String lang
    );


//    @Query(value = """
//                SELECT
//                    TO_CHAR(t.date, :format) AS date,
//                    COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS income,
//                    COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS expense
//                FROM transaction t
//                JOIN transaction_category c ON t.category_id = c.id
//                WHERE t.user_id = :userId AND
//                      ( (:lang = 'ru' AND c.name_ru = :categoryName) OR
//                        (:lang = 'en' AND c.name_en = :categoryName) ) AND
//                      t.date BETWEEN :start AND :end
//                GROUP BY TO_CHAR(t.date, :format)
//                ORDER BY TO_CHAR(t.date, :format)
//            """, nativeQuery = true)
//    List<CategoryChartPointProjection> getCategoryChartRawData(
//            @Param("userId") Long userId,
//            @Param("categoryName") String categoryName,
//            @Param("start") LocalDateTime start,
//            @Param("end") LocalDateTime end,
//            @Param("format") String format,
//            @Param("lang") String lang
//    );
}