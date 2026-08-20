package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.global.common.BaseEntity;
import com.ktb.hackathon.team11.group.WorkGroup;
import com.ktb.hackathon.team11.member.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "task_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskTemplate extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private WorkGroup group;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Member creator;

  @Column(nullable = false, length = 80)
  private String title;

  @Column(nullable = false, length = 2000)
  private String sourceMessage;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false)
  private boolean notifyOnCompletion;

  public TaskTemplate(WorkGroup g, Member c, String t, String s) {
    this(g, c, t, s, false);
  }

  public TaskTemplate(WorkGroup g, Member c, String t, String s, boolean notifyOnCompletion) {
    group = g;
    creator = c;
    title = t;
    sourceMessage = s;
    this.notifyOnCompletion = notifyOnCompletion;
  }

  public void update(String t, Boolean a) {
    if (t != null && !t.isBlank()) title = t.strip();
    if (a != null) active = a;
  }

  public void deactivate() {
    active = false;
  }

  public void updateCompletionNotification(boolean enabled) {
    notifyOnCompletion = enabled;
  }
}
