package kz.finance.fintrack.utils;

import kz.finance.fintrack.dto.CurrencyInfo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CurrencyUtil {
    private static final List<CurrencyInfo> CURRENCIES = List.of(
            new CurrencyInfo("KZT", "₸", "Казахстанский тенге", "Казахстан"),
            new CurrencyInfo("USD", "$", "Доллар США", "США"),
            new CurrencyInfo("EUR", "€", "Евро", "Европейский союз"),
            new CurrencyInfo("GBP", "£", "Фунт стерлингов", "Великобритания"),
            new CurrencyInfo("JPY", "¥", "Иена", "Япония"),
            new CurrencyInfo("CNY", "¥", "Юань", "Китай"),
            new CurrencyInfo("RUB", "₽", "Российский рубль", "Россия"),
            new CurrencyInfo("INR", "₹", "Индийская рупия", "Индия"),
            new CurrencyInfo("BRL", "R$", "Бразильский реал", "Бразилия"),
            new CurrencyInfo("KRW", "₩", "Южнокорейская вона", "Южная Корея"),
            new CurrencyInfo("AUD", "A$", "Австралийский доллар", "Австралия"),
            new CurrencyInfo("CAD", "C$", "Канадский доллар", "Канада"),
            new CurrencyInfo("CHF", "Fr", "Швейцарский франк", "Швейцария"),
            new CurrencyInfo("SGD", "S$", "Сингапурский доллар", "Сингапур"),
            new CurrencyInfo("NZD", "NZ$", "Новозеландский доллар", "Новая Зеландия"),
            new CurrencyInfo("MXN", "Mex$", "Мексиканское песо", "Мексика"),
            new CurrencyInfo("HKD", "HK$", "Гонконгский доллар", "Гонконг"),
            new CurrencyInfo("TRY", "₺", "Турецкая лира", "Турция"),
            new CurrencyInfo("SAR", "﷼", "Саудовский риял", "Саудовская Аравия"),
            new CurrencyInfo("SEK", "kr", "Шведская крона", "Швеция"),
            new CurrencyInfo("NOK", "kr", "Норвежская крона", "Норвегия"),
            new CurrencyInfo("DKK", "kr", "Датская крона", "Дания"),
            new CurrencyInfo("PLN", "zł", "Польский злотый", "Польша"),
            new CurrencyInfo("ILS", "₪", "Израильский шекель", "Израиль"),
            new CurrencyInfo("ZAR", "R", "Южноафриканский рэнд", "ЮАР"),
            new CurrencyInfo("HUF", "Ft", "Венгерский форинт", "Венгрия"),
            new CurrencyInfo("CZK", "Kč", "Чешская крона", "Чехия"),
            new CurrencyInfo("CLP", "CL$", "Чилийское песо", "Чили"),
            new CurrencyInfo("PHP", "₱", "Филиппинское песо", "Филиппины"),
            new CurrencyInfo("AED", "د.إ", "Дирхам ОАЭ", "ОАЭ"),
            new CurrencyInfo("COP", "CO$", "Колумбийское песо", "Колумбия"),
            new CurrencyInfo("TWD", "NT$", "Тайваньский доллар", "Тайвань")
    );

    private static final Map<String, CurrencyInfo> CODE_TO_INFO = CURRENCIES.stream()
            .collect(Collectors.toMap(CurrencyInfo::code, c -> c));

    public static CurrencyInfo getCurrencyInfo(String code) {
        return CODE_TO_INFO.getOrDefault(code, new CurrencyInfo(code, code, code, ""));
    }

    public static String getSymbol(String code) {
        return getCurrencyInfo(code).symbol();
    }

    public static List<CurrencyInfo> getAllCurrencies() {
        return CURRENCIES;
    }
}
