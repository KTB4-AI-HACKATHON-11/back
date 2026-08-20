package com.ktb.hackathon.team11.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ktb.hackathon.team11.global.exception.*;
import com.ktb.hackathon.team11.member.Member;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

class SessionServiceTest {
  @Mock private MemberSessionRepository repository;
  @Mock private Member member;
  private AutoCloseable mocks;
  private SessionService service;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    service = new SessionService(repository);
    ReflectionTestUtils.setField(service, "sessionDays", 7L);
    ReflectionTestUtils.setField(service, "cookieSecure", true);
  }

  @Test
  void storesOnlyHashedTokenAndIssuesSecureHttpOnlyCookie() {
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    SessionService.IssuedSession issued = service.issue(member);

    ArgumentCaptor<MemberSession> captor = ArgumentCaptor.forClass(MemberSession.class);
    verify(repository).save(captor.capture());
    assertThat(issued.token()).hasSizeGreaterThanOrEqualTo(40);
    assertThat(captor.getValue().getTokenHash()).hasSize(64).doesNotContain(issued.token());
    assertThat(service.cookie(issued.token()).toString())
        .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/");
  }

  @Test
  void rejectsMissingOrUnknownSession() {
    assertThatThrownBy(() -> service.require(null))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).getErrorCode())
        .isEqualTo(ErrorCode.SESSION_REQUIRED);

    when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.require("unknown-token"))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).getErrorCode())
        .isEqualTo(ErrorCode.SESSION_REQUIRED);
  }
}
