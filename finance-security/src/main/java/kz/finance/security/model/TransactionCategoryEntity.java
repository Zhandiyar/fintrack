package kz.finance.security.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transaction_category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionCategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transaction_category_seq")
    @SequenceGenerator(name = "transaction_category_seq", sequenceName = "seq_transaction_category_id", allocationSize = 50)
    private Long id;
    private String nameRu;
    private String nameEn;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @ManyToOne
    private UserEntity user;
    private String icon;
    private String color;
    private boolean system;
}
