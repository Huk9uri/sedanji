package kr.sedanji.mentiondraft;

public record MentionDraftApprovalResult(long draftId, long draftVersion, String draftStatus,
    long mentionId, long restaurantId, long mentionVersion, String verificationStatus, String publicationStatus) {}
