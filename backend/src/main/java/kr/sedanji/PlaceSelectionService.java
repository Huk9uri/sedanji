package kr.sedanji;

import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import kr.sedanji.Models.*;
import kr.sedanji.registration.DirectRegistrationException;
import org.springframework.stereotype.Service;

@Service
public class PlaceSelectionService {
    private static final String ATTRIBUTE = PlaceSelectionService.class.getName();
    private final ExternalApis apis;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public PlaceSelectionService(ExternalApis apis) { this(apis, Clock.systemUTC()); }
    PlaceSelectionService(ExternalApis apis, Clock clock) { this.apis = apis; this.clock = clock; }
    public record Selection(String placeSelectionToken, Place place) {}
    public record Search(List<Selection> candidates, int totalCount, boolean complete) {}
    private record Batch(String admin, Instant expiresAt, Map<String, Place> places) {}
    private static final class Snapshots { private final Deque<Batch> batches = new ArrayDeque<>(); }

    public Search search(String query, String admin, HttpSession session) {
        // 쿼리가 비어 있거나 100자 초과시 거부
        if (query == null || query.isBlank() || query.length() > 100) throw DirectRegistrationException.invalid();
        // 카카오맵 api로 장소 검색 후 result에 저장
        var result = apis.places(query.strip());
        if (result == null || result.candidates() == null || result.candidates().size() > 45 || result.totalCount() < 0)
            throw new ExternalApis.ApiFailure("Kakao 장소 응답 형식 오류");

        var places = new LinkedHashMap<String, Place>();
        var selections = new ArrayList<Selection>();
        for (var raw : result.candidates()) {
            if (raw == null || raw.id() == null || !raw.id().matches("[0-9]{1,40}") || raw.name() == null || raw.name().isBlank()
                || !Double.isFinite(raw.longitude()) || !Double.isFinite(raw.latitude())
                || Math.abs(raw.longitude()) > 180 || Math.abs(raw.latitude()) > 90)
                throw new ExternalApis.ApiFailure("Kakao 장소 응답 형식 오류");

            // 장소 URL을 검증된 Kakao Place ID로 구성
            var place = new Place(raw.id(), raw.name(), blankToNull(raw.roadAddress()), blankToNull(raw.address()),
                raw.longitude(), raw.latitude(), "https://place.map.kakao.com/" + raw.id());
            // 장소마다 UUID 기반 불투명 토큰을 발급
            String token = UUID.randomUUID().toString();
            places.put(token, place); selections.add(new Selection(token, place));
        }
        // 같은 session 객체를 기준으로 lock
        synchronized (session) {
            var snapshots = snapshots(session);
            snapshots.batches.removeIf(batch -> !batch.expiresAt().isAfter(clock.instant()));
            // 같은 토큰, 장소 대응을 담은 places 맵을 세션에 보관, 유효기간 15분
            snapshots.batches.addLast(new Batch(admin, clock.instant().plusSeconds(900), Map.copyOf(places)));
            // 최대 5개 까지만 보관
            while (snapshots.batches.size() > 5) snapshots.batches.removeFirst();
        }
        return new Search(List.copyOf(selections), result.totalCount(), result.complete());
    }
    public Place resolve(String token, String admin, HttpSession session) {
        synchronized (session) {
            for (var batch : snapshots(session).batches) {
                if (batch.admin().equals(admin) && batch.expiresAt().isAfter(clock.instant()) && batch.places().containsKey(token))
                    return batch.places().get(token);
            }
        }
        throw new DirectRegistrationException(410, "PLACE_SELECTION_EXPIRED", "장소를 다시 검색하고 선택하세요.");
    }
    private Snapshots snapshots(HttpSession session) {
        var snapshots = (Snapshots) session.getAttribute(ATTRIBUTE);

        if (snapshots == null) {
            snapshots = new Snapshots();
            session.setAttribute(ATTRIBUTE, snapshots);
        }

        return snapshots;
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
