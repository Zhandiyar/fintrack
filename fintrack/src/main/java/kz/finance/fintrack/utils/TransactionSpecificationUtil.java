package kz.finance.fintrack.utils;

import kz.finance.fintrack.model.TransactionEntity;
import kz.finance.fintrack.model.TransactionType;
import kz.finance.fintrack.model.UserEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class TransactionSpecificationUtil {
    // --- Specification helpers ---

    public static Specification<TransactionEntity> userEquals(UserEntity user) {
        return (root, query, cb) -> cb.equal(root.get("user"), user);
    }

    public static Specification<TransactionEntity> typeEquals(TransactionType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<TransactionEntity> categoryEquals(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<TransactionEntity> dateBetween(LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("date"), from, to);
            } else if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("date"), from);
            } else if (to != null) {
                return cb.lessThanOrEqualTo(root.get("date"), to);
            } else {
                return cb.conjunction();
            }
        };
    }
}
