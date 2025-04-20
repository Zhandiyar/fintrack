package kz.finance.security.repository;

import kz.finance.security.model.ExpenseEntity;
import kz.finance.security.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {
    List<ExpenseEntity> findAllByUser(UserEntity guestUser);
}
