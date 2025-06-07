package kz.finance.fintrack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse(
    boolean success,
    String message,
    Integer code,
    Object data
) {
    public static ApiResponse error(String message, int code) {
        return new ApiResponse(false, message, code, null);
    }
}