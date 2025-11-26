package kz.finance.security.repository;


import kz.finance.security.model.TransactionEntity;
import kz.finance.security.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    List<TransactionEntity> findAllByUser(UserEntity guest);
}