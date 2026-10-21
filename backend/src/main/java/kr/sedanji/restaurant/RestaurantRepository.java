package kr.sedanji.restaurant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface RestaurantRepository extends JpaRepository<RestaurantEntity, Long> {
    Optional<RestaurantEntity> findByKakaoPlaceId(String id);
    @Query("select r from RestaurantEntity r where exists (select m.id from MentionEntity m where m.restaurant = r and m.status = 'APPROVED') order by r.id")
    List<RestaurantEntity> findPublished();

    // PostgreSQL atomic upsert preserves one place even when different mentions are approved concurrently.
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into restaurants(kakao_place_id,name,road_address,address,longitude,latitude,place_url) values (:id,:name,:road,:address,:x,:y,:url) on conflict(kakao_place_id) do update set name=excluded.name", nativeQuery = true)
    void upsert(String id, String name, String road, String address, double x, double y, String url);
}
