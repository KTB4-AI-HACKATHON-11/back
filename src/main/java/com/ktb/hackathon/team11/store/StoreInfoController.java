package com.ktb.hackathon.team11.store;

import com.ktb.hackathon.team11.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
@RequiredArgsConstructor
public class StoreInfoController {
  private final StoreInfoService service;

  @GetMapping("/store-info")
  ApiResponse<List<StoreInfoService.Response>> list(@PathVariable long groupId, @RequestParam long requesterId) {
    return ApiResponse.of("STORE_INFO_LIST_FOUND", service.list(groupId, requesterId));
  }

  @PostMapping("/store-info")
  ApiResponse<StoreInfoService.Response> create(@PathVariable long groupId, @Valid @RequestBody Request request) {
    return ApiResponse.of("STORE_INFO_CREATED", service.create(groupId, request.managerId(), request.category(), request.title(), request.content()));
  }

  @PatchMapping("/store-info/{storeInfoId}")
  ApiResponse<StoreInfoService.Response> update(@PathVariable long groupId, @PathVariable long storeInfoId, @Valid @RequestBody Request request) {
    return ApiResponse.of("STORE_INFO_UPDATED", service.update(groupId, storeInfoId, request.managerId(), request.category(), request.title(), request.content()));
  }

  @DeleteMapping("/store-info/{storeInfoId}")
  ApiResponse<Void> delete(@PathVariable long groupId, @PathVariable long storeInfoId, @RequestParam long managerId) {
    service.delete(groupId, storeInfoId, managerId);
    return ApiResponse.of("STORE_INFO_DELETED", null);
  }

  @PostMapping("/ask")
  ApiResponse<StoreInfoService.Answer> ask(@PathVariable long groupId, @Valid @RequestBody AskRequest request) {
    return ApiResponse.of("STORE_INFO_ANSWERED", service.ask(groupId, request.requesterId(), request.question()));
  }

  public record Request(@NotNull Long managerId, @NotBlank String category, @NotBlank @Size(max = 60) String title, @NotBlank @Size(max = 1000) String content) {}
  public record AskRequest(@NotNull Long requesterId, @NotBlank @Size(max = 200) String question) {}
}
