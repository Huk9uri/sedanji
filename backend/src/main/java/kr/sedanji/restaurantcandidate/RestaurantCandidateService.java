package kr.sedanji.restaurantcandidate;

import java.util.List;
import kr.sedanji.Models.*;
import kr.sedanji.importjob.ImportJobEntity;
import kr.sedanji.importjob.ImportJobRepository;
import kr.sedanji.video.VideoService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class RestaurantCandidateService {
    private final RestaurantCandidateRepository candidates;
    private final PlaceCandidateRepository places;
    private final ImportJobRepository jobs;
    private final VideoService videos;

    public RestaurantCandidateService(RestaurantCandidateRepository candidates, PlaceCandidateRepository places,
                                      ImportJobRepository jobs, VideoService videos) {
        this.candidates = candidates; this.places = places; this.jobs = jobs; this.videos = videos;
    }
    public List<Candidate> findCandidates() {
        return candidates.findAllByOrderByIdDesc().stream().map(RestaurantCandidateEntity::toDto).toList();
    }
    public RestaurantCandidateEntity getRequired(long id) {
        return candidates.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "후보를 찾을 수 없습니다."));
    }
    @Transactional
    public void addExtractedCandidate(ImportJobEntity job, Extracted value) { insertCandidate(job, value); }

    @Transactional
    public void addManualCandidate(String videoId, String name, String address, String evidence) {
        var video = videos.getRequired(videoId);
        if (!video.getDescription().contains(evidence))
            throw new IllegalArgumentException("근거는 저장된 설명란 원문에 포함되어야 합니다.");
        var job = jobs.findFirstByVideoIdOrderByIdDesc(videoId).orElseThrow();
        insertCandidate(job, new Extracted(name.strip(), address, 0, evidence));
    }
    private void insertCandidate(ImportJobEntity job, Extracted value) {
        Integer line = value.sourceLine() > 0 ? value.sourceLine() : null;
        if (!candidates.existsByImportJobIdAndSourceLineAndExtractedName(job.getId(), line, value.name().strip()))
            candidates.save(new RestaurantCandidateEntity(job, value));
    }
    // The caller owns the transaction so the lock survives the entire approval/matching flow.
    @Transactional(propagation = Propagation.MANDATORY)
    public RestaurantCandidateEntity getForUpdate(long id) {
        var candidate = candidates.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "후보를 찾을 수 없습니다."));
        if (candidate.getDisposition().equals("DISMISSED"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "제외된 후보입니다.");
        return candidate;
    }
    public Place selectedPlace(long candidateId, String placeId) {
        return places.findByCandidateIdAndPlaceId(candidateId, placeId)
            .orElseThrow(() -> new IllegalArgumentException("이 후보에서 검색한 Kakao 장소를 선택하세요.")).toDto();
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void resolveCandidate(long id) { getForUpdate(id).resolve(); }

    @Transactional
    public void dismissCandidate(long id, String note) {
        var candidate = getForUpdate(id);
        if (!candidate.getDisposition().equals("OPEN"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "관계가 승인된 후보는 제외할 수 없습니다.");
        candidate.dismiss(note);
    }
}
