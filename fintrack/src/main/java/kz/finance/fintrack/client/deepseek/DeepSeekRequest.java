package kz.finance.fintrack.client.deepseek;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeepSeekRequest(
        String model,
        List<DeepSeekMessage> messages,
        Integer max_tokens,
        Double temperature,
        Double top_p
) {
    public DeepSeekRequest(String model, List<DeepSeekMessage> messages) {
        this(model, messages, 300, 0.7, 1.0);
    }
}