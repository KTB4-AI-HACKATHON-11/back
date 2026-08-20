package com.ktb.hackathon.team11.member;

import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
  private final MemberRepository repository;

  @Transactional
  public Member create(String nickname) {
    String normalized = nickname.strip();
    if (repository.existsByNickname(normalized))
      throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
    return repository.save(new Member(normalized, MemberRole.MANAGER));
  }

  public Member login(String nickname) {
    return repository
        .findByNickname(nickname.strip())
        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
  }

  public Member requireMember(long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
  }

  public Member requireRole(long id, MemberRole role) {
    Member member = requireMember(id);
    if (member.getRole() != role)
      throw new BusinessException(
          role == MemberRole.MANAGER ? ErrorCode.MANAGER_REQUIRED : ErrorCode.WORKER_REQUIRED);
    return member;
  }
}
