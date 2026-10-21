package kr.sedanji.restaurant;

import jakarta.persistence.*;
import java.util.List;
import kr.sedanji.Models.*;

@Entity
@Table(name = "restaurants")
public class RestaurantEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "kakao_place_id", nullable = false, unique = true, length = 40)
    private String kakaoPlaceId;
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

    protected RestaurantEntity() {}
    public RestaurantEntity(Place place) {
        kakaoPlaceId = place.id(); name = place.name(); roadAddress = place.roadAddress(); address = place.address();
        longitude = place.longitude(); latitude = place.latitude(); placeUrl = place.placeUrl();
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public Restaurant toDto(List<Source> mentions) { return new Restaurant(id, kakaoPlaceId, name, roadAddress, address, longitude, latitude, placeUrl, mentions); }
}
