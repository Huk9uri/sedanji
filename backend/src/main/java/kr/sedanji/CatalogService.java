package kr.sedanji;

import java.time.OffsetDateTime;
import java.util.List;
import kr.sedanji.admin.*;
import kr.sedanji.chef.*;
import kr.sedanji.importjob.*;
import kr.sedanji.mention.*;
import kr.sedanji.restaurant.*;
import kr.sedanji.restaurantcandidate.*;
import kr.sedanji.video.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static kr.sedanji.Models.*;

@Service @Transactional(readOnly = true)
class CatalogService {
    private final ChefRepository chefs;
    private final VideoRepository videos;
    private final MentionRepository mentions;
    private final RestaurantRepository restaurants;
    private final RestaurantCandidateRepository candidates;
    private final PlaceCandidateRepository places;
    private final ImportJobRepository jobs;
    private final AdminUserRepository admins;
    private final ExternalApis apis;
    private final DescriptionExtractor extractor;
    CatalogService(ChefRepository chefs, VideoRepository videos, MentionRepository mentions, RestaurantRepository restaurants,
                   RestaurantCandidateRepository candidates, PlaceCandidateRepository places, ImportJobRepository jobs,
                   AdminUserRepository admins, ExternalApis apis, DescriptionExtractor extractor) {
        this.chefs = chefs; this.videos = videos; this.mentions = mentions; this.restaurants = restaurants;
        this.candidates = candidates; this.places = places; this.jobs = jobs; this.admins = admins; this.apis = apis; this.extractor = extractor;
    }
    public List<Chef> chefs() { return chefs.findAllByOrderByNameAsc().stream().map(e -> new Chef(e.getId(), e.getName())).toList(); }
    @Transactional public Chef createChef(String name) {
        var entity = chefs.findByName(name.strip()).orElseGet(() -> chefs.saveAndFlush(new ChefEntity(name.strip())));
        return new Chef(entity.getId(), entity.getName());
    }
    public List<Video> videos() { return videos.findAllByOrderByImportedAtDesc().stream().map(VideoEntity::toDto).toList(); }
    @Transactional public Video importVideo(String url) {
        String id = DescriptionExtractor.videoId(url);
        var existing = videos.findById(id);
        if (existing.isPresent()) return existing.get().toDto();
        OffsetDateTime started = OffsetDateTime.now();
        Video video = apis.video(id);
        var entity = videos.saveAndFlush(new VideoEntity(video));
        var job = jobs.saveAndFlush(new ImportJobEntity(entity, started));
        for (Extracted candidate : extractor.extract(video.description())) insertCandidate(job, candidate);
        return entity.toDto();
    }
    private void insertCandidate(ImportJobEntity job, Extracted value) {
        Integer line = value.sourceLine() > 0 ? value.sourceLine() : null;
        if (!candidates.existsByImportJobIdAndSourceLineAndExtractedName(job.getId(), line, value.name().strip()))
            candidates.save(new RestaurantCandidateEntity(job, value));
    }
    @Transactional public void addCandidate(String videoId, String name, String address, String evidence) {
        var video = videos.findById(videoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));
        if (!video.getDescription().contains(evidence)) throw new IllegalArgumentException("근거는 저장된 설명란 원문에 포함되어야 합니다.");
        var job = jobs.findFirstByVideoIdOrderByIdDesc(videoId).orElseThrow();
        insertCandidate(job, new Extracted(name.strip(), address, 0, evidence));
    }
    public List<Candidate> candidates() { return candidates.findAllByOrderByIdDesc().stream().map(RestaurantCandidateEntity::toDto).toList(); }
    public List<Mention> mentions() { return mentions.findAllByOrderByIdDesc().stream().map(MentionEntity::toDto).toList(); }
    private RestaurantCandidateEntity locked(long id) {
        var candidate = candidates.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "후보를 찾을 수 없습니다."));
        if (candidate.getDisposition().equals("DISMISSED")) throw new ResponseStatusException(HttpStatus.CONFLICT, "제외된 후보입니다.");
        return candidate;
    }
    @Transactional public void approve(long id, long chefId, String placeId, MentionType type, Integer timestamp,
                                       String evidence, String note, String reviewer) {
        if (type == null || evidence == null || evidence.isBlank() || note == null || note.isBlank() || (timestamp != null && timestamp < 0))
            throw new IllegalArgumentException("언급 유형과 확인 근거를 입력하세요.");
        var candidate = locked(id);
        var chef = chefs.findById(chefId).orElseThrow(() -> new IllegalArgumentException("셰프를 선택하세요."));
        var admin = admins.findByUsername(reviewer).filter(AdminUserEntity::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 관리자만 승인할 수 있습니다."));
        Place place = places.findByCandidateIdAndPlaceId(id, placeId)
            .orElseThrow(() -> new IllegalArgumentException("이 후보에서 검색한 Kakao 장소를 선택하세요.")).toDto();
        restaurants.upsert(place.id(), place.name(), place.roadAddress(), place.address(), place.longitude(), place.latitude(), place.placeUrl());
        var restaurant = restaurants.findByKakaoPlaceId(place.id()).orElseThrow();
        if (mentions.existsByChefIdAndRestaurantIdAndVideoIdAndMentionType(chefId, restaurant.getId(), candidate.getImportJob().getVideo().getId(), type))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 셰프·식당·영상·언급 유형의 관계가 이미 있습니다.");
        mentions.saveAndFlush(new MentionEntity(candidate, chef, restaurant, type, timestamp, evidence, note, admin));
        candidate.resolve();
    }
    @Transactional public void dismiss(long id, String note) {
        var candidate = locked(id);
        if (!candidate.getDisposition().equals("OPEN")) throw new ResponseStatusException(HttpStatus.CONFLICT, "관계가 승인된 후보는 제외할 수 없습니다.");
        candidate.dismiss(note);
    }
    public List<Restaurant> restaurants() {
        return restaurants.findPublished().stream().map(restaurant -> restaurant.toDto(
            mentions.findByRestaurantIdAndVerificationStatusOrderByIdAsc(restaurant.getId(), "APPROVED").stream().map(MentionEntity::toSource).toList())).toList();
    }
}
