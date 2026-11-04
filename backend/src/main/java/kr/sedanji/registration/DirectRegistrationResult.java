package kr.sedanji.registration;

public record DirectRegistrationResult(long mentionId, long restaurantId, long version,
                                       String verificationStatus, String publicationStatus) {}
