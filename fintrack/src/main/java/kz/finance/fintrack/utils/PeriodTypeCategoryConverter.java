package kz.finance.fintrack.utils;

import kz.finance.fintrack.dto.PeriodType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class PeriodTypeCategoryConverter implements Converter<String, PeriodType> {
    @Override
    public PeriodType convert(String source) {
        return Arrays.stream(PeriodType.values())
                .filter(category -> category.name().equalsIgnoreCase(source))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid category: " + source));
    }
}
