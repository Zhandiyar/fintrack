package kz.finance.fintrack.repository;

import kz.finance.fintrack.dto.TransactionRawDto;
import kz.finance.fintrack.dto.analytics.AnalyticsStatsDto;
import kz.finance.fintrack.dto.analytics.CategorySummaryRawDto;
import kz.finance.fintrack.dto.analytics.DashboardStatsDto;
import kz.finance.fintrack.model.TransactionEntity;
import kz.finance.fintrack.model.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long>, TransactionRepositoryCustom, JpaSpecificationExecutor<TransactionEntity> {
    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<TransactionEntity> findAll(Specification<TransactionEntity> spec, Pageable pageable);

    Optional<TransactionEntity> findByIdAndUser(Long id, UserEntity user);

    @Query("""
                SELECT new kz.finance.fintrack.dto.TransactionRawDto(
                    t.id,
                    t.amount,
                    t.date,
                    t.createdAt,
                    t.updatedAt,
                    t.comment,
                    t.type,
                    c.id,
                    c.nameRu,
                    c.nameEn,
                    c.icon,
                    c.color
                )
                FROM TransactionEntity t
                JOIN t.category c
                WHERE t.user = :user
                ORDER BY t.date DESC
            """)
    List<TransactionRawDto> findRecentTransactions(@Param("user") UserEntity user, Pageable pageable);


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

    @Query("""
                SELECT new kz.finance.fintrack.dto.analytics.CategorySummaryRawDto(
                    c.id,
                    c.nameRu,
                    c.nameEn,
                    c.icon,
                    c.color,
                    COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0)
                )
                FROM TransactionEntity t
                JOIN t.category c
                WHERE t.user.id = :userId
                  AND t.date BETWEEN :start AND :end
                GROUP BY c.id, c.nameRu, c.nameEn
            """)
    List<CategorySummaryRawDto> getCategorySummary(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
                SELECT t FROM TransactionEntity t
                WHERE t.user.id = :userId
                AND t.date >= :from AND t.date < :to
                ORDER BY t.date
            """)
    List<TransactionEntity> findAllByUserIdAndMonth(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}