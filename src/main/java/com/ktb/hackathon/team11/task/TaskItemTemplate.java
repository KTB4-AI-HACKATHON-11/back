package com.ktb.hackathon.team11.task;

import com.ktb.hackathon.team11.ai.CompletionType;
import com.ktb.hackathon.team11.global.exception.BusinessException;
import com.ktb.hackathon.team11.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "task_item_templates",
    uniqueConstraints = @UniqueConstraint(columnNames = {"task_template_id", "sequence_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskItemTemplate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "task_template_id")
  private TaskTemplate taskTemplate;

  @Column(name = "sequence_no", nullable = false)
  private int sequence;

  @Column(nullable = false, length = 80)
  private String title;

  @Column(nullable = false, length = 500)
  private String instruction;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CompletionType completionType;

  @Column(length = 1000)
  private String verificationRule;

  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean enabled = true;

  private String referenceImageKey;

  @Column(length = 20)
  private String referenceImageMimeType;

  private Long referenceImageSizeBytes;

  @Column(length = 64)
  private String referenceImageSha256;

  public TaskItemTemplate(
      TaskTemplate taskTemplate,
      int sequence,
      String title,
      String instruction,
      CompletionType completionType,
      String verificationRule) {
    validateVerification(completionType, verificationRule, ErrorCode.INVALID_COMPLETION_TYPE);
    this.taskTemplate = taskTemplate;
    this.sequence = sequence;
    this.title = title;
    this.instruction = instruction;
    this.completionType = completionType;
    this.verificationRule = completionType == CompletionType.CHECK ? null : verificationRule;
  }

  public void updateVerification(CompletionType completionType, String verificationRule) {
    validateVerification(completionType, verificationRule, ErrorCode.VERIFICATION_RULE_REQUIRED);
    this.completionType = completionType;
    this.verificationRule = completionType == CompletionType.CHECK ? null : verificationRule;
  }

  private void validateVerification(
      CompletionType completionType, String verificationRule, ErrorCode missingRuleError) {
    if (completionType == null)
      throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
    if (completionType == CompletionType.PHOTO
        && (verificationRule == null || verificationRule.isBlank()))
      throw new BusinessException(missingRuleError);
    if (completionType == CompletionType.CHECK
        && verificationRule != null
        && !verificationRule.isBlank())
      throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public void setReferenceImage(String key, String mimeType, long sizeBytes, String sha256) {
    if (completionType != CompletionType.PHOTO)
      throw new BusinessException(ErrorCode.INVALID_COMPLETION_TYPE);
    referenceImageKey = key;
    referenceImageMimeType = mimeType;
    referenceImageSizeBytes = sizeBytes;
    referenceImageSha256 = sha256;
  }

  public String clearReferenceImage() {
    String oldKey = referenceImageKey;
    referenceImageKey = null;
    referenceImageMimeType = null;
    referenceImageSizeBytes = null;
    referenceImageSha256 = null;
    return oldKey;
  }

  public boolean hasReferenceImage() {
    return referenceImageKey != null
        && !referenceImageKey.isBlank()
        && referenceImageMimeType != null
        && referenceImageSizeBytes != null
        && referenceImageSha256 != null;
  }
}
