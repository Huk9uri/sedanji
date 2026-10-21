package kr.sedanji;

import java.util.List;
import kr.sedanji.chef.*;
import kr.sedanji.mention.*;
import kr.sedanji.restaurant.*;
import kr.sedanji.video.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static kr.sedanji.Models.*;

/** Coordinates the existing API while persistence is delegated to domain repositories. */
@Service
@Transactional(readOnly = true)
class CatalogService {
    private final ChefRepository chefs;
    private final VideoRepository videos;
    private final MentionRepository mentions;
    private final RestaurantRepository restaurants;
    private final PlaceCandidateRepository places;
    private final ExternalApis apis;
    private final DescriptionExtractor extractor;

    CatalogService(ChefRepository chefs, VideoRepository videos, MentionRepository mentions,
                   RestaurantRepository restaurants, PlaceCandidateRepository places,
                   ExternalApis apis, DescriptionExtractor extractor) {
        this.chefs = chefs; this.videos = videos; this.mentions = mentions;
        this.restaurants = restaurants; this.places = places; this.apis = apis; this.extractor = extractor;
    }
    public List<Chef> chefs() { return chefs.findAllByOrderByNameAsc().stream().map(e -> new Chef(e.getId(), e.getName())).toList(); }

    @Transactional
    public Chef createChef(String name) {
        ChefEntity entity = chefs.findByName(name.strip()).orElseGet(() -> chefs.saveAndFlush(new ChefEntity(name.strip())));
        return new Chef(entity.getId(), entity.getName());
    }
    public List<Video> videos() { return videos.findAllByOrderByImportedAtDesc().stream().map(VideoEntity::toDto).toList(); }

    @Transactional
    public Video importVideo(String url) {
        String id = DescriptionExtractor.videoId(url);
        var existing = videos.findById(id);
        if (existing.isPresent()) return existing.get().toDto();
        Video video = apis.video(id);
        VideoEntity entity = videos.saveAndFlush(new VideoEntity(video));
        for (Extracted candidate : extractor.extract(video.description())) insertMention(entity, candidate);
        return entity.toDto();
    }
    private void insertMention(VideoEntity video, Extracted candidate) {
        if (!mentions.existsByVideoIdAndSourceLineAndExtractedName(video.getId(), candidate.sourceLine(), candidate.name()))
            mentions.save(new MentionEntity(video, candidate));
    }
    @Transactional
    public void addMention(String videoId, String name, String address, int sourceLine, String evidence) {
        VideoEntity video = videos.findById(videoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));
        if (!video.getDescription().contains(evidence)) throw new IllegalArgumentException("근거는 저장된 설명란 원문에 포함되어야 합니다.");
        insertMention(video, new Extracted(name.strip(), address.strip(), sourceLine, evidence));
    }
    public List<Mention> mentions() { return mentions.findAllByOrderByIdDesc().stream().map(MentionEntity::toDto).toList(); }

    private MentionEntity lockedPending(long id) {
        MentionEntity entity = mentions.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "후보를 찾을 수 없습니다."));
        if (!entity.getStatus().equals("PENDING")) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 검토된 후보입니다.");
        return entity;
    }
    @Transactional
    public PlaceSearch search(long mentionId, String query) {
        MentionEntity mention = lockedPending(mentionId);
        PlaceSearch search = apis.places(query == null || query.isBlank() ? mention.getExtractedName() : query);
        places.deleteForMention(mentionId);
        places.saveAll(search.candidates().stream().map(place -> new PlaceCandidateEntity(mentionId, place)).toList());
        return search;
    }
    @Transactional
    public void approve(long mentionId, long chefId, String placeId, String note, String reviewer) {
        MentionEntity mention = lockedPending(mentionId);
        ChefEntity chef = chefs.findById(chefId).orElseThrow(() -> new IllegalArgumentException("셰프를 선택하세요."));
        Place place = places.findById(new PlaceCandidateId(mentionId, placeId))
            .orElseThrow(() -> new IllegalArgumentException("이 후보에서 검색한 Kakao 장소를 선택하세요.")).toDto();
        restaurants.upsert(place.id(), place.name(), place.roadAddress(), place.address(), place.longitude(), place.latitude(), place.placeUrl());
        RestaurantEntity restaurant = restaurants.findByKakaoPlaceId(place.id()).orElseThrow();
        mention.approve(chef, restaurant, note, reviewer);
    }
    @Transactional
    public void reject(long id, String note, String reviewer) { lockedPending(id).reject(note, reviewer); }

    public List<Restaurant> restaurants() {
        return restaurants.findPublished().stream().map(restaurant -> restaurant.toDto(
            mentions.findByRestaurantIdAndStatusOrderByIdAsc(restaurant.getId(), "APPROVED").stream().map(MentionEntity::toSource).toList())).toList();
    }
}
