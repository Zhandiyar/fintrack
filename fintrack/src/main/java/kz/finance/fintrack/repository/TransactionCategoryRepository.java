package kz.finance.fintrack.repository;

import kz.finance.fintrack.model.TransactionCategoryEntity;
import kz.finance.fintrack.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TransactionCategoryRepository extends JpaRepository<TransactionCategoryEntity, Long> {
    Optional<TransactionCategoryEntity> findById(Long id);

    @Query("SELECT c FROM TransactionCategoryEntity c WHERE c.type = :type AND (c.user.id = :userId OR c.system = true)")
    List<TransactionCategoryEntity> findByUserOrSystemAndType(TransactionType type, Long userId);

    Optional<TransactionCategoryEntity> findByIdAndUserId(Long id, Long userId);
} 