package com.ktb.hackathon.team11.group;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.member.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "work_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkGroup extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(length = 200)
  private String description;

  // 사용자가 공유하고 입력하는 외부 식별자다. 내부 DB 기본 키를 초대 코드로 노출하지 않는다.
  @Column(nullable = false, unique = true, length = 6)
  private String inviteCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Member owner;

  public WorkGroup(String name, String description, String inviteCode, Member owner) {
    this.name = name.strip();
    this.description = normalizeDescription(description);
    this.inviteCode = inviteCode;
    this.owner = owner;
  }

  private static String normalizeDescription(String description) {
    return description == null || description.isBlank() ? null : description.strip();
  }
}
