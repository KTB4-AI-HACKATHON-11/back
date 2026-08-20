package com.ktb.hackathon.team11.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.member.Member;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PushSubscriptionServiceTest {
  @Test
  void rejectsNonHttpsEndpointBeforeSaving() {
    PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    PushSubscriptionService service = new PushSubscriptionService(repository);

    assertThatThrownBy(
            () -> service.upsert(mock(Member.class), "http://example.com/push", "key", "auth", null))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_PUSH_SUBSCRIPTION);
    verify(repository, never()).save(any());
  }

  @Test
  void savesValidSubscriptionWithStableEndpointHash() {
    PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    PushSubscriptionService service = new PushSubscriptionService(repository);
    Member member = mock(Member.class);
    when(repository.findByEndpointHash(anyString())).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    PushSubscriptionRecord saved =
        service.upsert(member, "https://push.example.com/subscription/1", "key", "auth", null);

    assertThat(saved.getEndpointHash()).hasSize(64);
    assertThat(saved.isActive()).isTrue();
  }
}
