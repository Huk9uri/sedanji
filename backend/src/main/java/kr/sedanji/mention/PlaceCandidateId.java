package kr.sedanji.mention;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class PlaceCandidateId implements Serializable {
    @Column(name = "mention_id")
    private Long mentionId;
    @Column(name = "kakao_place_id", length = 40)
    private String placeId;
    protected PlaceCandidateId() {}
    public PlaceCandidateId(Long mentionId, String placeId) { this.mentionId = mentionId; this.placeId = placeId; }
    public Long getMentionId() { return mentionId; }
    public String getPlaceId() { return placeId; }
    @Override public boolean equals(Object other) {
        return other instanceof PlaceCandidateId id && Objects.equals(mentionId, id.mentionId) && Objects.equals(placeId, id.placeId);
    }
    @Override public int hashCode() { return Objects.hash(mentionId, placeId); }
}
