package kr.sedanji.publication;

import java.time.OffsetDateTime;

public record PublicationResult(String action,long mentionId,long restaurantId,long previousVersion,long version,
    String verificationStatus,String publicationStatus,long auditEventId,OffsetDateTime occurredAt) {}
