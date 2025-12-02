package kz.finance.fintrack.client.deepseek;

import java.util.List;

public record DeepSeekResponse(
        String id,
        String object,
        long created,
        String model,
        List<DeepSeekChoice> choices,
        Object usage
) {}