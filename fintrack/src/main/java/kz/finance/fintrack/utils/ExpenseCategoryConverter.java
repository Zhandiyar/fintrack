package kz.finance.fintrack.utils;

import kz.finance.fintrack.dto.ExpenseCategory;
import org.hibernate.annotations.Comment;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ExpenseCategoryConverter implements Converter<String, ExpenseCategory> {
    @Override
    public ExpenseCategory convert(String source) {
        return Arrays.stream(ExpenseCategory.values())
                .filter(category -> category.name().equalsIgnoreCase(source))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid category: " + source));
    }
}
