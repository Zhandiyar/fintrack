package kz.finance.fintrack.client;

import java.util.List;

public record DeepSeekResponse(List<DeepSeekChoice> choices) {
}
