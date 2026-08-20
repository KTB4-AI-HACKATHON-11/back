package com.ktb.hackathon.team11.store;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.group.WorkGroup;
import com.ktb.hackathon.team11.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "store_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreInfo extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private WorkGroup group;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StoreInfoCategory category;

  @Column(nullable = false, length = 60)
  private String title;

  @Column(nullable = false, length = 1000)
  private String content;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Member createdBy;

  public StoreInfo(WorkGroup group, Member createdBy, StoreInfoCategory category, String title, String content) {
    this.group = group;
    this.createdBy = createdBy;
    update(category, title, content);
  }

  public void update(StoreInfoCategory category, String title, String content) {
    this.category = category;
    this.title = title.strip();
    this.content = content.strip();
  }
}
