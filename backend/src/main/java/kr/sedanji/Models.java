package kr.sedanji;

import java.time.OffsetDateTime;
import java.util.List;

public final class Models {
    private Models() {}
    public record Chef(long id, String name) {}
    public record Video(String id, String title, String channelTitle, OffsetDateTime publishedAt,
                        String description, String extractionWarning) {}
    public record Restaurant(long id, String kakaoPlaceId, String name, String roadAddress,
                             String address, double longitude, double latitude, String placeUrl,
                             List<Source> mentions) {}
    public record Source(long mentionId, String chefName, String videoId, String videoTitle) {}
    public record Mention(long id, String videoId, String videoTitle, String extractedName,
                          String extractedAddress, int sourceLine, String evidence, String status,
                          Long chefId, Long restaurantId, String reviewNote) {}
    public record Place(String id, String name, String roadAddress, String address,
                        double longitude, double latitude, String placeUrl) {}
    public record PlaceSearch(List<Place> candidates, int totalCount, boolean complete) {}
    public record Extracted(String name, String address, int sourceLine, String evidence) {}
}
