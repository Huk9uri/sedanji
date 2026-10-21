package kr.sedanji;

import java.time.OffsetDateTime;
import java.util.List;
import jakarta.persistence.EntityManager;
import kr.sedanji.mention.PlaceCandidateId;
import kr.sedanji.mention.PlaceCandidateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static kr.sedanji.Models.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Testcontainers
class ApprovalIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired CatalogService service;
    @Autowired JdbcTemplate db;
    @Autowired EntityManager entityManager;
    @Autowired PlaceCandidateRepository placeCandidates;
    @MockitoBean ExternalApis apis;
    private final String id = "RMTJPFKRbEk";
    private final Place place = new Place("1158460117", "뽀요베리", "서울 마포구 월드컵로13길 55-15", "서울 마포구 망원동 57-302", 126.90687068921198, 37.55461513602502, "http://place.map.kakao.com/1158460117");
    private long imported() {
        when(apis.video(id)).thenReturn(new Video(id, "검증 영상", "채널은 셰프 아님", OffsetDateTime.now(), "🥘 뽀요베리\n📍서울 마포구 월드컵로13길 55-15", "수동 확인"));
        service.importVideo("https://youtu.be/" + id);
        return service.mentions().get(0).id();
    }
    private long prepared() {
        long mention = imported();
        when(apis.places(anyString())).thenReturn(new PlaceSearch(List.of(place), 1, true));
        service.search(mention, "뽀요베리");
        return mention;
    }
    @Test void pendingHiddenAndRepeatedImportDoesNotDuplicate() {
        imported(); imported();
        assertThat(service.mentions()).hasSize(1);
        assertThat(service.restaurants()).isEmpty();
        assertThat(service.chefs()).isEmpty();
        verify(apis, times(1)).video(id);
    }
    @Test @WithMockUser(roles="ADMIN") void approvalPersistsAndPublishesThenRejectsRepeat() throws Exception {
        long mention = prepared(), chef = service.createChef("윤남노").id();
        String body = "{\"chefId\":" + chef + ",\"placeId\":\"1158460117\",\"note\":\"영상 추천 주체와 주소 확인\"}";
        mvc.perform(post("/api/admin/mentions/" + mention + "/approve").with(csrf()).contentType("application/json").content(body)).andExpect(status().isNoContent());
        mvc.perform(get("/api/restaurants")).andExpect(status().isOk()).andExpect(jsonPath("$[0].kakaoPlaceId").value(place.id())).andExpect(jsonPath("$[0].mentions[0].chefName").value("윤남노"));
        assertThat(db.queryForObject("SELECT reviewed_by FROM mentions WHERE id=?", String.class, mention)).isEqualTo("user");
        mvc.perform(post("/api/admin/mentions/" + mention + "/approve").with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
    }
    @Test void unsearchedPlaceCannotBeApproved() {
        long mention = imported(), chef = service.createChef("윤남노").id();
        assertThatThrownBy(() -> service.approve(mention, chef, place.id(), "근거", "admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.restaurants()).isEmpty();
    }
    @Test void rejectionDoesNotCreateRestaurant() {
        long mention = prepared(); service.reject(mention, "주소 근거 부족", "admin");
        assertThat(service.mentions().get(0).status()).isEqualTo("REJECTED");
        assertThat(service.restaurants()).isEmpty();
    }
    @Test void adminRequiresLoginAndCsrf() throws Exception {
        mvc.perform(get("/api/admin/mentions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/chefs").contentType("application/json").content("{\"name\":\"셰프\"}")).andExpect(status().isForbidden());
    }
    @Test void realLoginCreatesSessionAndRejectsWrongPassword() throws Exception {
        mvc.perform(post("/api/login").with(csrf()).param("username", "admin").param("password", "incorrect"))
            .andExpect(status().isUnauthorized());
        var result = mvc.perform(post("/api/login").with(csrf()).param("username", "admin").param("password", "integration-test-password-only"))
            .andExpect(status().isNoContent()).andReturn();
        mvc.perform(get("/api/session").session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true));
    }
    @Test @WithMockUser(roles="ADMIN") void csrfRequiredEvenForAuthenticatedAdmin() throws Exception {
        mvc.perform(post("/api/admin/chefs").contentType("application/json").content("{\"name\":\"셰프\"}")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles="ADMIN") void apiFailureDoesNotCreateVideoOrLeakCredential() throws Exception {
        when(apis.video(id)).thenThrow(new ExternalApis.ApiFailure("외부 API 요청 실패 (HTTP 403)"));
        mvc.perform(post("/api/admin/videos").with(csrf()).contentType("application/json").content("{\"url\":\"https://youtu.be/" + id + "\"}")).andExpect(status().isBadGateway());
        assertThat(service.videos()).isEmpty();
    }
    @Test void sameKakaoPlaceIsDeduplicatedAcrossMentions() {
        long first = prepared(), chef = service.createChef("윤남노").id();
        service.addMention(id, "뽀요베리 수동", "", 0, "뽀요베리");
        long second = service.mentions().get(0).id();
        service.search(second, "뽀요베리");
        service.approve(first, chef, place.id(), "확인", "admin");
        service.approve(second, chef, place.id(), "추가 근거 확인", "admin");
        assertThat(service.restaurants()).hasSize(1);
        assertThat(service.restaurants().get(0).mentions()).hasSize(2);
    }
    @Test void approvedRelationshipsReloadWithoutOpenSessionInView() {
        long mention = prepared(), chef = service.createChef("윤남노").id();
        service.approve(mention, chef, place.id(), "출처 확인", "admin");
        entityManager.flush();
        entityManager.clear();
        var published = service.restaurants();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).mentions().get(0).chefName()).isEqualTo("윤남노");
        assertThat(service.mentions().get(0).status()).isEqualTo("APPROVED");
    }
    @Test void repeatedSearchReplacesSnapshotsWithoutStaleJpaEntities() {
        long mention = prepared();
        Place replacement = new Place("999", "다른 후보", "서울 다른 주소", "서울 다른 지번", 127.0, 37.5, "http://place.map.kakao.com/999");
        when(apis.places(anyString())).thenReturn(new PlaceSearch(List.of(replacement), 1, true));
        service.search(mention, "다른 후보");
        entityManager.flush();
        entityManager.clear();
        assertThat(placeCandidates.findById(new PlaceCandidateId(mention, place.id()))).isEmpty();
        assertThat(placeCandidates.findById(new PlaceCandidateId(mention, replacement.id()))).isPresent();
    }
    @Test void manualEvidenceMustExistInOfficialDescription() {
        imported();
        assertThatThrownBy(() -> service.addMention(id, "가짜", "", 0, "없는 설명")).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.mentions()).hasSize(1);
    }
    @Test @WithMockUser(roles="ADMIN") void emptyApprovalNoteFailsValidation() throws Exception {
        long mention = prepared(), chef = service.createChef("윤남노").id();
        mvc.perform(post("/api/admin/mentions/" + mention + "/approve").with(csrf()).contentType("application/json").content("{\"chefId\":" + chef + ",\"placeId\":\"1158460117\",\"note\":\" \"}")).andExpect(status().isBadRequest());
        assertThat(service.restaurants()).isEmpty();
    }
}
