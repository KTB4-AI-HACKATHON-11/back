package com.ktb.hackathon.team11.attempt;

import com.ktb.hackathon.team11.group.WorkGroup;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "task_photos",
    uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "sha256"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskPhoto {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  private TaskAttempt attempt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id")
  private WorkGroup group;

  @Column(nullable = false)
  private String objectKey;

  @Column(nullable = false, length = 20)
  private String mimeType;

  @Column(nullable = false)
  private long sizeBytes;

  @Column(nullable = false, length = 64)
  private String sha256;

  public TaskPhoto(TaskAttempt a, WorkGroup g, String key, String mime, long size, String sha) {
    attempt = a;
    group = g;
    objectKey = key;
    mimeType = mime;
    sizeBytes = size;
    sha256 = sha;
  }
}
