package kr.sedanji.restaurant;

import java.util.List;
import kr.sedanji.Models.*;
import kr.sedanji.mention.MentionEntity;
import kr.sedanji.mention.MentionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RestaurantService {
    private final RestaurantRepository restaurants;
    private final MentionRepository mentions;
    public RestaurantService(RestaurantRepository restaurants, MentionRepository mentions) {
        this.restaurants = restaurants; this.mentions = mentions;
    }
    public List<Restaurant> findPublishedRestaurants() {
        return restaurants.findPublished().stream().map(restaurant -> restaurant.toDto(
            mentions.findByRestaurantIdAndVerificationStatusAndPublicationStatusOrderByIdAsc(
                restaurant.getId(), "APPROVED", "PUBLISHED").stream().map(MentionEntity::toSource).toList())).toList();
    }
    @Transactional
    public RestaurantEntity getOrCreate(Place place) {
        restaurants.upsert(place.id(), place.name(), place.roadAddress(), place.address(),
            place.longitude(), place.latitude(), place.placeUrl());
        return restaurants.findByKakaoPlaceId(place.id()).orElseThrow();
    }
}
