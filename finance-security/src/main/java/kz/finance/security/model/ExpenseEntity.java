package kz.finance.security.model;

import jakarta.persistence.*;
import kz.finance.security.model.ExpenseCategory;
import kz.finance.security.model.UserEntity;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "expenses")
public class ExpenseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    private BigDecimal amount;

    private LocalDateTime date;

    @Enumerated(EnumType.STRING)
    private ExpenseCategory category;

    @ManyToOne
    private UserEntity user;
}


