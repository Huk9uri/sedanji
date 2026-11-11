package kr.sedanji.publicweb;

import java.time.OffsetDateTime;
import java.util.List;

public final class PublicResult {
    private PublicResult() {}
    public record Chef(long id, String name) {}
    public record ChefItem(long id, String name, long restaurantCount) {}
    public record Item(long id, String name, double latitude, double longitude, String roadAddress,
                       String address, List<Chef> chefs, long chefCount, boolean hasMoreChefs) {}
    public record Query(double west, double south, double east, double north, Long chefId) {}
    public record Area(List<Item> items, Query query, int returnedCount, int limit, boolean truncated, Long totalCount) {}
    public record Page<T>(List<T> items, long totalElements, int page, int size, boolean hasNext) {}
    public record Video(String id, String title, String channelTitle, OffsetDateTime publishedAt) {}
    public record Source(long mentionId, Chef chef, String mentionType, Video video, Integer timestampSeconds) {}
    public record Restaurant(long id, String name, String kakaoPlaceId, String roadAddress, String address,
                         double latitude, double longitude, String placeUrl) {}
    public record Detail(Restaurant restaurant, Page<Source> sources) {}
}
