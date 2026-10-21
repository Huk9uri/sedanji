package kr.sedanji.mention;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import kr.sedanji.Models.Place;

@Entity
@Table(name = "place_candidates")
public class PlaceCandidateEntity {
    @EmbeddedId
    private PlaceCandidateId id;
    @Column(nullable = false, columnDefinition = "text")
    private String name;
    @Column(name = "road_address", nullable = false, columnDefinition = "text")
    private String roadAddress;
    @Column(nullable = false, columnDefinition = "text")
    private String address;
    @Column(nullable = false)
    private double longitude;
    @Column(nullable = false)
    private double latitude;
    @Column(name = "place_url", nullable = false, columnDefinition = "text")
    private String placeUrl;
    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;
    protected PlaceCandidateEntity() {}
    public PlaceCandidateEntity(long mentionId, Place place) {
        id = new PlaceCandidateId(mentionId, place.id()); name = place.name(); roadAddress = place.roadAddress();
        address = place.address(); longitude = place.longitude(); latitude = place.latitude(); placeUrl = place.placeUrl(); fetchedAt = OffsetDateTime.now();
    }
    public Place toDto() { return new Place(id.getPlaceId(), name, roadAddress, address, longitude, latitude, placeUrl); }
}
