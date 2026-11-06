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
    @Column(name = "road_address", columnDefinition = "text")
    private String roadAddress;
    @Column(columnDefinition = "text")
    private String address;
    @Version private long version;
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
    public void correctPlace(Place place) {
        if (!kakaoPlaceId.equals(place.id()) || place.name() == null || place.name().isBlank()
            || !Double.isFinite(place.longitude()) || !Double.isFinite(place.latitude())
            || Math.abs(place.longitude()) > 180 || Math.abs(place.latitude()) > 90)
            throw new IllegalArgumentException("같은 Kakao 장소의 공식 정보를 선택하세요.");
        name=place.name(); roadAddress=place.roadAddress(); address=place.address();
        longitude=place.longitude(); latitude=place.latitude(); placeUrl=place.placeUrl();
    }
    public Restaurant toDto(List<Source> mentions) { return new Restaurant(id, kakaoPlaceId, name, roadAddress, address, longitude, latitude, placeUrl, mentions); }
}
