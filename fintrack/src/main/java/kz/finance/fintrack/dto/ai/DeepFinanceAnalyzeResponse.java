package kz.finance.fintrack.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeepFinanceAnalyzeResponse {
    private String topSpending;
    private String portrait;
    private String forecast;
    private String error;

    public static DeepFinanceAnalyzeResponse empty() {
        DeepFinanceAnalyzeResponse resp = new DeepFinanceAnalyzeResponse();
        resp.setError("Нет данных за месяц.");
        return resp;
    }

    public static DeepFinanceAnalyzeResponse error(String message) {
        DeepFinanceAnalyzeResponse resp = new DeepFinanceAnalyzeResponse();
        resp.setError(message);
        return resp;
    }
}
