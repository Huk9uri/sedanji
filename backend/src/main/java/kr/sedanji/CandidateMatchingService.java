package kr.sedanji;

import java.util.List;
import kr.sedanji.restaurantcandidate.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static kr.sedanji.Models.*;

@Service
class CandidateMatchingService {
    private final RestaurantCandidateRepository candidates;
    private final PlaceCandidateRepository places;
    private final ExternalApis apis;
    private final PlaceMatcher matcher;
    CandidateMatchingService(RestaurantCandidateRepository candidates, PlaceCandidateRepository places, ExternalApis apis, PlaceMatcher matcher) {
        this.candidates = candidates; this.places = places; this.apis = apis; this.matcher = matcher;
    }
    @Transactional(readOnly = true)
    public CandidateSearch saved(long id) {
        var candidate = candidates.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "후보를 찾을 수 없습니다."));
        List<Place> list = places.findByCandidateIdOrderByIdAsc(id).stream().map(PlaceCandidateEntity::toDto).toList();
        return new CandidateSearch(list, candidate.getSearchTotalCount() == null ? list.size() : candidate.getSearchTotalCount(),
            candidate.isSearchComplete(), candidate.getMatchStatus());
    }
    // Persist safe error metadata but retain the last successful snapshots and matching result.
    @Transactional(noRollbackFor = ExternalApis.ApiFailure.class)
    public CandidateSearch search(long id, String query) {
        var candidate = candidates.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "후보를 찾을 수 없습니다."));
        if (candidate.getDisposition().equals("DISMISSED")) throw new ResponseStatusException(HttpStatus.CONFLICT, "제외된 후보입니다.");
        try {
            PlaceSearch search = apis.places(query);
            String status = matcher.evaluate(candidate.getExtractedName(), candidate.getExtractedAddress(), search);
            places.deleteForCandidate(id);
            places.saveAll(search.candidates().stream().map(place -> new PlaceCandidateEntity(candidate, place)).toList());
            candidate.matched(status, search.totalCount(), search.complete());
            return new CandidateSearch(search.candidates(), search.totalCount(), search.complete(), status);
        } catch (ExternalApis.ApiFailure ex) {
            candidate.matchingFailed("EXTERNAL_API_FAILURE");
            throw ex;
        }
    }
}
