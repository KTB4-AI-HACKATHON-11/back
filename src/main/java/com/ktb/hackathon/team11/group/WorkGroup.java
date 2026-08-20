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

  // 기존 로컬/배포 DB의 NOT NULL 컬럼 호환을 위해 남겨 둔다. API와 그룹 가입에는 노출하거나 사용하지 않는다.
  @Column(nullable = false, unique = true, length = 6)
  private String inviteCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Member owner;

  public WorkGroup(String name, String description, String legacyCode, Member owner) {
    this.name = name.strip();
    this.description = normalizeDescription(description);
    this.inviteCode = legacyCode;
    this.owner = owner;
  }

  private static String normalizeDescription(String description) {
    return description == null || description.isBlank() ? null : description.strip();
  }
}
