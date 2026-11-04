package kr.sedanji;

import java.time.*;
import java.util.List;
import kr.sedanji.Models.*;
import kr.sedanji.registration.DirectRegistrationException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceSelectionServiceTest {
    private final ExternalApis apis = mock(ExternalApis.class);
    private final Clock clock = mock(Clock.class);
    private final PlaceSelectionService service = new PlaceSelectionService(apis, clock);
    private final MockHttpSession session = new MockHttpSession();
    private final Instant now = Instant.parse("2026-11-04T00:00:00Z");
    private final Place place = new Place("123", "식당", "", null, 127, 37, "untrusted-url");
    private String search() {
        when(clock.instant()).thenReturn(now);
        when(apis.places(anyString())).thenReturn(new PlaceSearch(List.of(place), 80, false));
        var result = service.search("식당", "admin", session);
        assertThat(result.totalCount()).isEqualTo(80);
        assertThat(result.complete()).isFalse();
        return result.candidates().get(0).placeSelectionToken();
    }
    @Test void snapshotsAreBoundToAdminAndSessionAndNormalizeMissingAddresses() {
        var token = search();
        var selected = service.resolve(token, "admin", session);
        assertThat(selected.roadAddress()).isNull();
        assertThat(selected.address()).isNull();
        assertThat(selected.placeUrl()).isEqualTo("https://place.map.kakao.com/123");
        assertThatThrownBy(() -> service.resolve(token, "other", session)).isInstanceOf(DirectRegistrationException.class);
        assertThatThrownBy(() -> service.resolve(token, "admin", new MockHttpSession())).isInstanceOf(DirectRegistrationException.class);
        verify(apis, times(1)).places(anyString());
    }
    @Test void expiryAndSixthSearchEvictTokens() {
        String token = search();
        when(clock.instant()).thenReturn(now.plusSeconds(900));
        assertThatThrownBy(() -> service.resolve(token, "admin", session)).isInstanceOf(DirectRegistrationException.class);
        String evicted = search();
        for (int i = 0; i < 5; i++) search();
        assertThatThrownBy(() -> service.resolve(evicted, "admin", session)).isInstanceOf(DirectRegistrationException.class);
    }
    @Test void malformedExternalCoordinatesFailWithoutReplacingPreviousSnapshot() {
        String token = search();
        when(apis.places(anyString())).thenReturn(new PlaceSearch(List.of(new Place("123", "식당", null, null,
            Double.NaN, 37, "")), 1, true));
        assertThatThrownBy(() -> service.search("식당", "admin", session)).isInstanceOf(ExternalApis.ApiFailure.class);
        assertThat(service.resolve(token, "admin", session).id()).isEqualTo("123");
    }
}
