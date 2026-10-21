package kr.sedanji;

import java.time.OffsetDateTime;
import java.util.List;
import kr.sedanji.mention.MentionType;

public final class Models {
    private Models() {}
    public record Chef(long id, String name) {}
    public record Video(String id, String title, String channelTitle, OffsetDateTime publishedAt,
                        String description, String extractionWarning) {}
    public record Restaurant(long id, String kakaoPlaceId, String name, String roadAddress,
                             String address, double longitude, double latitude, String placeUrl,
                             List<Source> mentions) {}
    public record Source(long mentionId, String chefName, String videoId, String videoTitle,
                         MentionType mentionType, Integer timestampSeconds) {}
    public record Candidate(long id, String videoId, String videoTitle, String extractedName,
                            String extractedAddress, Integer sourceLine, String evidence,
                            String matchStatus, String matchingErrorCode, OffsetDateTime matchedAt,
                            String disposition, String resolutionNote) {}
    public record Mention(long id, Long candidateId, String videoId, String videoTitle,
                          long chefId, String chefName, long restaurantId, String restaurantName,
                          MentionType mentionType, Integer timestampSeconds, String verificationStatus,
                          String evidence, String reviewNote, String reviewedBy, OffsetDateTime reviewedAt) {}
    public record Place(String id, String name, String roadAddress, String address,
                        double longitude, double latitude, String placeUrl) {}
    public record PlaceSearch(List<Place> candidates, int totalCount, boolean complete) {}
    public record CandidateSearch(List<Place> candidates, int totalCount, boolean complete, String matchStatus) {}
    public record Extracted(String name, String address, int sourceLine, String evidence) {}
}
