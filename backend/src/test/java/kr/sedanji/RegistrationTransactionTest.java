package kr.sedanji;

import java.time.OffsetDateTime;
import java.util.List;
import kr.sedanji.chef.ChefService;
import kr.sedanji.importjob.VideoImportService;
import kr.sedanji.mention.MentionService;
import kr.sedanji.mention.MentionType;
import kr.sedanji.registration.RegistrationService;
import kr.sedanji.restaurant.RestaurantService;
import kr.sedanji.restaurantcandidate.RestaurantCandidateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static kr.sedanji.Models.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Intentionally no test transaction: verifies the service's own commit/rollback boundary. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RegistrationTransactionTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired RegistrationService registrations;
    @Autowired VideoImportService imports;
    @Autowired ChefService chefs;
    @Autowired MentionService mentions;
    @Autowired RestaurantService restaurants;
    @Autowired CandidateMatchingService matching;
    @Autowired JdbcTemplate db;
    @MockitoBean ExternalApis apis;
    @MockitoSpyBean RestaurantCandidateService candidates;

    private record Prepared(long candidateId, long chefId, Place place) {}
    private Prepared prepare(String videoId, String placeId) {
        when(apis.video(videoId)).thenReturn(new Video(videoId, "영상", "채널", OffsetDateTime.now(),
            "🥘 검증 식당\n📍서울 마포구 월드컵로13길 55-15", "확인"));
        imports.importVideo("https://youtu.be/" + videoId);
        long candidate = candidates.findCandidates().stream().filter(c -> c.videoId().equals(videoId))
            .findFirst().orElseThrow().id();
        var place = new Place(placeId, "검증 식당", "서울 마포구 월드컵로13길 55-15", "서울 마포구 망원동", 127, 37,
            "https://place.map.kakao.com/" + placeId);
        when(apis.places(anyString())).thenReturn(new PlaceSearch(List.of(place), 1, true));
        matching.search(candidate, "검증 식당");
        return new Prepared(candidate, chefs.createChef("검증 셰프").id(), place);
    }
    private void approve(Prepared prepared) {
        registrations.approveFromCandidate(prepared.candidateId(), prepared.chefId(), prepared.place().id(),
            MentionType.VISITED, null, "영상 방문 확인", null, "admin");
    }
    @Test void serviceStartsTransactionAndCommitsApprovalWithoutCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var prepared = prepare("RMTJPFKRbEk", "1001");
        approve(prepared);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(candidates.findCandidates().stream().filter(c -> c.id() == prepared.candidateId())
            .findFirst().orElseThrow().disposition()).isEqualTo("RESOLVED");
        assertThat(mentions.findMentions()).anyMatch(m -> m.candidateId() == prepared.candidateId());
        assertThat(restaurants.findPublishedRestaurants()).anyMatch(r -> r.kakaoPlaceId().equals("1001"));
    }
    @Test void finalCandidateUpdateFailureRollsBackRelationshipAndNewRestaurant() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var prepared = prepare("abcdefghijk", "1002");
        RestaurantCandidateService candidateTarget = AopTestUtils.getUltimateTargetObject(candidates);
        doThrow(new IllegalStateException("후보 상태 변경 실패")).when(candidateTarget).resolveCandidate(prepared.candidateId());
        assertThatThrownBy(() -> approve(prepared)).isInstanceOf(IllegalStateException.class)
            .hasMessage("후보 상태 변경 실패");
        assertThat(candidates.findCandidates().stream().filter(c -> c.id() == prepared.candidateId())
            .findFirst().orElseThrow().disposition()).isEqualTo("OPEN");
        assertThat(mentions.findMentions()).noneMatch(m -> m.candidateId() == prepared.candidateId());
        assertThat(db.queryForObject("select count(*) from restaurants where kakao_place_id='1002'", Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from videos where id='abcdefghijk'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from import_jobs where video_id='abcdefghijk'", Integer.class)).isEqualTo(1);
    }
}
