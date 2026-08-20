package com.ktb.hackathon.team11.global.handler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import com.ktb.hackathon.team11.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JpaMetamodelMappingContext jpaMappingContext;

  @Test
  @DisplayName("성공 응답은 code, message, data 형식을 사용한다")
  void successResponseHasCommonFormat() throws Exception {
    mockMvc
        .perform(get("/test/success"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.message").value("요청이 성공적으로 처리되었습니다."))
        .andExpect(jsonPath("$.data.value").value("ok"));
  }

  @Test
  @DisplayName("비즈니스 예외는 오류 코드에 지정된 상태와 응답을 반환한다")
  void handlesBusinessException() throws Exception {
    mockMvc
        .perform(get("/test/business-error"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("테스트 리소스를 찾을 수 없습니다."))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  @DisplayName("Bean Validation 실패는 첫 번째 검증 메시지를 공통 응답으로 반환한다")
  void handlesValidationException() throws Exception {
    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.message").value("이름은 필수입니다."));
  }

  @Test
  @DisplayName("읽을 수 없는 JSON은 공통 JSON 오류 응답을 반환한다")
  void handlesUnreadableJson() throws Exception {
    mockMvc
        .perform(
            post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_JSON_FORMAT"))
        .andExpect(jsonPath("$.message").value("요청 JSON 형식이 올바르지 않습니다."));
  }

  @Test
  @DisplayName("예상하지 못한 예외는 내부 정보를 숨기고 500 응답을 반환한다")
  void handlesUnexpectedException() throws Exception {
    mockMvc
        .perform(get("/test/unexpected-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
        .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
  }

  @RestController
  @RequestMapping("/test")
  public static class TestController {

    @GetMapping("/success")
    ApiResponse<Map<String, String>> success() {
      return ApiResponse.of("SUCCESS", Map.of("value", "ok"));
    }

    @GetMapping("/business-error")
    void businessError() {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "테스트 리소스를 찾을 수 없습니다.");
    }

    @PostMapping("/validation")
    ApiResponse<TestRequest> validation(@Valid @RequestBody TestRequest request) {
      return ApiResponse.of("SUCCESS", request);
    }

    @GetMapping("/unexpected-error")
    void unexpectedError() {
      throw new IllegalStateException("노출되면 안 되는 내부 오류");
    }
  }

  record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {}
}
