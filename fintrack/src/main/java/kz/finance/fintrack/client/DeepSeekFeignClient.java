package kz.finance.fintrack.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;


@FeignClient(
        name = "deepSeekClient",
        url = "https://api.deepseek.com/v1"
)
public interface DeepSeekFeignClient {
    @PostMapping("/chat/completions")
    DeepSeekResponse chatCompletion(
            @RequestHeader("Authorization") String authorization,
            @RequestBody DeepSeekRequest request
    );
}

