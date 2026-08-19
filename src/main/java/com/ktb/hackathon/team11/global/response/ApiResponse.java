package com.ktb.hackathon.team11.global.response;

import lombok.Getter;

@Getter
public class ApiResponse<T> {

  private final String code;
  private final String message;
  private final T data;

  private ApiResponse(String code, String message, T data) {
    this.code = code;
    this.message = message;
    this.data = data;
  }

  public static <T> ApiResponse<T> of(String code, T data) {
    return new ApiResponse<>(code, "요청이 성공적으로 처리되었습니다.", data);
  }

  public static <T> ApiResponse<T> onFailure(String errorCode, String errorMessage) {
    return new ApiResponse<>(errorCode, errorMessage, null);
  }
}
