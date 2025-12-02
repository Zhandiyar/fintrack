package kz.finance.fintrack.client.deepseek;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeepSeekRequest(
        String model,
        List<DeepSeekMessage> messages,
        @JsonProperty("max_tokens")
        Integer maxTokens,
        Boolean stream
) {
}
