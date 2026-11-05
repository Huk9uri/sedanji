package kr.sedanji.mentiondraft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import kr.sedanji.PlaceSelectionService;
import kr.sedanji.admin.*;
import kr.sedanji.chef.ChefRepository;
import kr.sedanji.registration.*;
import kr.sedanji.restaurant.*;
import kr.sedanji.video.VideoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly=true)
public class MentionDraftService {
    public static final String PATH = "/api/admin/mention-drafts";
    private final MentionDraftRepository drafts;
    private final AdminUserRepository admins;
    private final RestaurantRepository restaurants;
    private final RestaurantService restaurantService;
    private final ChefRepository chefs;
    private final VideoRepository videos;
    private final PlaceSelectionService selections;
    private final AdminAuditEventRepository audits;
    private final AdminRequestReceiptRepository receipts;
    private final ObjectMapper mapper;
    public MentionDraftService(MentionDraftRepository drafts, AdminUserRepository admins, RestaurantRepository restaurants,
        RestaurantService restaurantService, ChefRepository chefs, VideoRepository videos, PlaceSelectionService selections,
        AdminAuditEventRepository audits, AdminRequestReceiptRepository receipts, ObjectMapper mapper) {
        this.drafts = drafts; this.admins = admins; this.restaurants = restaurants; this.restaurantService = restaurantService;
        this.chefs = chefs; this.videos = videos; this.selections = selections; this.audits = audits; this.receipts = receipts; this.mapper = mapper;
    }
    @Transactional
    public MentionDraftResult create(MentionDraftInput.Create request, String key, String username, HttpSession session) {
        if (key == null || key.isBlank() || key.length() > 128) throw MentionDraftException.invalid();
        var admin = active(username, true);
        var now = OffsetDateTime.now();
        String hash = hash(request);
        var receipt = receipts.findByAdminIdAndIdempotencyKey(admin.getId(), key);
        if (receipt.isPresent() && receipt.get().active(now)) {
            if (!receipt.get().matches(PATH, hash, 201))
                throw new MentionDraftException(409, "IDEMPOTENCY_CONFLICT", "다른 요청에 사용된 멱등키입니다.");
            try { return mapper.readValue(receipt.get().body(), MentionDraftResult.class); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("초안 성공 영수증 형식 오류"); }
        }
        if (receipt.isPresent()) { receipts.delete(receipt.get()); receipts.flush(); }
        validateReferences(request.values());
        var restaurant = place(request.values(), username, session);
        var draft = drafts.saveAndFlush(new MentionDraftEntity(restaurant.getId(), request, admin.getId()));
        var result = draft.toDto(MentionDraftResult.Restaurant.from(restaurant));
        String body = json(result);
        audits.saveAndFlush(AdminAuditEventEntity.draft(admin.getId(), draft.getId(), restaurant.getId(), null, body));
        receipts.saveAndFlush(new AdminRequestReceiptEntity(admin.getId(), key, PATH, hash, 201, body, now));
        return result;
    }
    public MentionDraftResult get(long id, String username) {
        active(username, false); positive(id);
        var draft = drafts.findById(id).filter(MentionDraftEntity::isCreate).orElseThrow(MentionDraftException::missing);
        return result(draft);
    }
    public MentionDraftResult.Page list(long restaurantId, int page, int size, String status, String username) {
        active(username, false); positive(restaurantId);
        if (page < 0 || size < 1 || size > 100 || !("DRAFT".equals(status) || "APPLIED".equals(status))) throw MentionDraftException.invalid();
        var restaurant = restaurants.findById(restaurantId).orElseThrow(MentionDraftException::missing);
        var rows = drafts.findByRestaurantIdAndKindAndStatus(restaurantId, "CREATE", status,
            PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        var dto = MentionDraftResult.Restaurant.from(restaurant);
        return new MentionDraftResult.Page(rows.stream().map(d -> d.toDto(dto)).toList(), rows.getTotalElements(), page, size, rows.hasNext());
    }
    @Transactional
    public MentionDraftResult patch(long id, MentionDraftInput.Patch request, String username, HttpSession session) {
        var admin = active(username, true); positive(id);
        var draft = drafts.findForUpdate(id).orElseThrow(MentionDraftException::missing);
        if (!draft.isCreate()) throw MentionDraftException.missing();
        if (!draft.isEditable()) throw new MentionDraftException(409, "DRAFT_NOT_EDITABLE", "적용 완료된 초안은 수정할 수 없습니다.");
        if (draft.getVersion() != request.expectedVersion())
            throw new MentionDraftException(409, "STALE_VERSION", "초안을 다시 조회하세요.", draft.getVersion());
        var before = result(draft);
        validateReferences(request.values());
        long restaurant = request.changesPlace() ? place(request.values(), username, session).getId() : draft.getRestaurantId();
        draft.patch(request, restaurant, admin.getId());
        drafts.flush();
        var after = result(draft);
        audits.saveAndFlush(AdminAuditEventEntity.draft(admin.getId(), id, restaurant, json(before), json(after)));
        return after;
    }
    private AdminUserEntity active(String username, boolean lock) {
        return (lock ? admins.findForUpdate(username) : admins.findByUsername(username)).filter(AdminUserEntity::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 관리자만 사용할 수 있습니다."));
    }
    private void positive(long id) { if (id < 1) throw MentionDraftException.invalid(); }
    private void validateReferences(MentionDraftInput.Values values) {
        if (values.chefId() != null && !chefs.existsById(values.chefId())) throw MentionDraftException.missing();
        if (values.videoId() != null && !videos.existsById(values.videoId())) throw MentionDraftException.missing();
    }
    private RestaurantEntity place(MentionDraftInput.Values values, String username, HttpSession session) {
        if (values.restaurantId() != null) return restaurants.findById(values.restaurantId()).orElseThrow(MentionDraftException::missing);
        // Resolves only the server's session snapshot; never calls an external API.
        return restaurantService.getOrCreate(selections.resolve(values.placeSelectionToken(), username, session));
    }
    private MentionDraftResult result(MentionDraftEntity draft) {
        return draft.toDto(MentionDraftResult.Restaurant.from(restaurants.findById(draft.getRestaurantId()).orElseThrow(MentionDraftException::missing)));
    }
    private String hash(MentionDraftInput.Create request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("POST\n" + PATH + "\n" + json(request.values())).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("요청 해시 생성 실패"); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("초안 결과 저장 실패"); }
    }
}
