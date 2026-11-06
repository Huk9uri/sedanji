package kr.sedanji.restaurant;

import java.time.OffsetDateTime;
import java.util.List;

public final class AdminRestaurantResult {
    private AdminRestaurantResult() {}
    public record Page<T>(List<T> items, long totalElements, int page, int size, boolean hasNext) {
        static <T> Page<T> of(List<T> items, long total, int page, int size) {
            return new Page<>(items, total, page, size, ((long) page + 1) * size < total);
        }
    }
    public record Chef(long id, String name) {}
    public record ChefPreview(long id, String name, long relationCount, long publicRelationCount) {}
    public record Summary(long relationCount, long publicRelationCount, boolean hasPublicRelation,
        Boolean needsSourceReview, long sourceCount, long uncheckedSourceCount, long reviewRequiredSourceCount) {}
    public record Item(long id, String name, String roadAddress, String address, OffsetDateTime createdAt,
        long relationCount, long publicRelationCount, boolean hasPublicRelation, long chefCount,
        List<ChefPreview> chefs, boolean hasMoreChefs, Boolean needsSourceReview,
        long sourceCount, long uncheckedSourceCount, long reviewRequiredSourceCount) {}
    public record Restaurant(long id, String kakaoPlaceId, String name, String roadAddress, String address,
        double longitude, double latitude, String placeUrl, long version, OffsetDateTime createdAt) {}
    public record Video(String id, String title, String channelTitle, OffsetDateTime publishedAt,
        String sourceCheckStatus, OffsetDateTime sourceCheckedAt, String sourceCheckReason) {}
    public record Mention(long id, long version, Chef chef, Video video, String mentionType,
        Integer timestampSeconds, String verificationStatus, String publicationStatus,
        String verificationNote, OffsetDateTime reviewedAt) {}
    public record Detail(Restaurant restaurant, Summary summary, Page<Mention> mentions) {}
}
