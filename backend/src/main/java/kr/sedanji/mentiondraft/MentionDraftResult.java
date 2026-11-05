package kr.sedanji.mentiondraft;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import kr.sedanji.mention.MentionType;
import kr.sedanji.restaurant.RestaurantEntity;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record MentionDraftResult(long id, String kind, String status, long version, Restaurant restaurant,
    Long chefId, String videoId, MentionType mentionType, Integer timestampSeconds, String verificationNote,
    Long candidateId, Long appliedMentionId, long createdBy, long updatedBy, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Restaurant(long id, String kakaoPlaceId, String name, String roadAddress, String address,
                             double longitude, double latitude, String placeUrl) {
        public static Restaurant from(RestaurantEntity entity) {
            var r = entity.toDto(List.of());
            return new Restaurant(r.id(), r.kakaoPlaceId(), r.name(), r.roadAddress(), r.address(), r.longitude(), r.latitude(), r.placeUrl());
        }
    }
    public record Page(List<MentionDraftResult> items, long totalElements, int page, int size, boolean hasNext) {}
}
