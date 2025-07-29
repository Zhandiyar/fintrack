package kz.finance.fintrack.client;

import java.util.List;

public record DeepSeekRequest(String model, List<DeepSeekMessage> messages) {
}
