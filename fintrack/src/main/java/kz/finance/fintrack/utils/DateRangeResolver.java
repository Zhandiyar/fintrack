package kz.finance.fintrack.utils;

import kz.finance.fintrack.dto.PeriodType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Locale;

public class DateRangeResolver {

    public static DateRange resolve(Integer year, Integer month, Integer day) {
        LocalDate now = LocalDate.now();
        year = (year != null) ? year : now.getYear();

        if (day != null && month != null) {
            LocalDate date = LocalDate.of(year, month, day);
            return new DateRange(date.atStartOfDay(), date.atTime(LocalTime.MAX));
        } else if (month != null) {
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
            return new DateRange(start.atStartOfDay(), end.atTime(LocalTime.MAX));
        } else {
            LocalDate start = LocalDate.of(year, 1, 1);
            LocalDate end = start.with(TemporalAdjusters.lastDayOfYear());
            return new DateRange(start.atStartOfDay(), end.atTime(LocalTime.MAX));
        }
    }
    public static DateRange resolve(PeriodType periodType, Integer year, Integer month, Integer day) {
        LocalDate today = LocalDate.now();

        return switch (periodType) {
            case DAY -> {
                LocalDate date = resolveDate(year, month, day, today);
                yield new DateRange(date.atStartOfDay(), date.atTime(LocalTime.MAX));
            }
            case WEEK -> {
                LocalDate date = resolveDate(year, month, day, today);
                // ISO week: Monday - Sunday
                LocalDate start = date.with(WeekFields.ISO.getFirstDayOfWeek());
                LocalDate end = start.plusDays(6);
                yield new DateRange(
                        start.atStartOfDay(),
                        end.atTime(LocalTime.MAX).isAfter(LocalDateTime.now()) ? LocalDateTime.now() : end.atTime(LocalTime.MAX)
                );
            }
            case MONTH -> {
                int y = (year != null) ? year : today.getYear();
                int m = (month != null) ? month : today.getMonthValue();
                LocalDate start = LocalDate.of(y, m, 1);
                LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
                yield new DateRange(
                        start.atStartOfDay(),
                        adjustIfInFuture(end.atTime(LocalTime.MAX))
                );
            }
            case YEAR -> {
                int y = (year != null) ? year : today.getYear();
                LocalDate start = LocalDate.of(y, 1, 1);
                LocalDate end = start.with(TemporalAdjusters.lastDayOfYear());
                yield new DateRange(
                        start.atStartOfDay(),
                        adjustIfInFuture(end.atTime(LocalTime.MAX))
                );
            }
        };
    }

    private static LocalDate resolveDate(Integer year, Integer month, Integer day, LocalDate fallback) {
        if (year != null && month != null && day != null) {
            return LocalDate.of(year, month, day);
        }
        return fallback;
    }

    private static LocalDateTime adjustIfInFuture(LocalDateTime end) {
        return end.isAfter(LocalDateTime.now()) ? LocalDateTime.now() : end;
    }

    public static String formatPeriodLabel(LocalDateTime start, String lang) {
        Locale locale = lang.equalsIgnoreCase("en") ? Locale.ENGLISH : Locale.forLanguageTag("ru");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale);
        return start.format(formatter);
    }

    public record DateRange(LocalDateTime start, LocalDateTime end) {}
}
