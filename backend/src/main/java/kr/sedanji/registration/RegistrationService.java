package kr.sedanji.registration;

import kr.sedanji.admin.AdminUserEntity;
import kr.sedanji.admin.AdminUserRepository;
import kr.sedanji.chef.ChefService;
import kr.sedanji.mention.MentionService;
import kr.sedanji.mention.MentionType;
import kr.sedanji.mentiondraft.*;
import kr.sedanji.restaurant.RestaurantService;
import kr.sedanji.restaurantcandidate.RestaurantCandidateService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {
    private final ChefService chefs;
    private final MentionService mentions;
    private final RestaurantService restaurants;
    private final RestaurantCandidateService candidates;
    private final AdminUserRepository admins;
    private final kr.sedanji.PlaceSelectionService selections;
    private final kr.sedanji.chef.ChefRepository chefRepository;
    private final kr.sedanji.video.VideoRepository videos;
    private final kr.sedanji.mention.MentionRepository mentionRepository;
    private final AdminAuditEventRepository audits;
    private final AdminRequestReceiptRepository receipts;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final MentionDraftRepository drafts;
    private final kr.sedanji.restaurant.RestaurantRepository restaurantRepository;
    private final kr.sedanji.correction.CorrectionCoordinator coordinator;

    public RegistrationService(ChefService chefs, MentionService mentions, RestaurantService restaurants,
                               RestaurantCandidateService candidates, AdminUserRepository admins,
                               kr.sedanji.PlaceSelectionService selections, kr.sedanji.chef.ChefRepository chefRepository,
                               kr.sedanji.video.VideoRepository videos, kr.sedanji.mention.MentionRepository mentionRepository,
                               AdminAuditEventRepository audits, AdminRequestReceiptRepository receipts,
                               com.fasterxml.jackson.databind.ObjectMapper mapper, MentionDraftRepository drafts,
                               kr.sedanji.restaurant.RestaurantRepository restaurantRepository,
                               kr.sedanji.correction.CorrectionCoordinator coordinator) {
        this.chefs = chefs; this.mentions = mentions; this.restaurants = restaurants;
        this.candidates = candidates; this.admins = admins;
        this.selections = selections; this.chefRepository = chefRepository; this.videos = videos;
        this.mentionRepository = mentionRepository; this.audits = audits; this.receipts = receipts; this.mapper = mapper;
        this.drafts = drafts; this.restaurantRepository = restaurantRepository;
        this.coordinator = coordinator;
    }
    @Transactional
    public void approveFromCandidate(long candidateId, long chefId, String placeId, MentionType type,
                                     Integer timestamp, String evidence, String note, String reviewer) {
        mentions.validateApproval(type, timestamp, evidence);
        var admin = admins.findForUpdate(reviewer).filter(AdminUserEntity::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 관리자만 승인할 수 있습니다."));
        coordinator.lock();
        var candidate = candidates.getForUpdate(candidateId);
        var chef = chefs.getRequired(chefId);
        var place = candidates.selectedPlace(candidateId, placeId);
        var restaurant = restaurants.getOrCreate(place);
        mentions.approve(chef, restaurant, candidate.getImportJob().getVideo(), candidate, type, timestamp, evidence, note, admin);
        candidates.resolveCandidate(candidateId);
    }

    @Transactional
    public DirectRegistrationResult approveDirect(DirectRegistrationRequest request, String key, String hash,
                                                   String reviewer, jakarta.servlet.http.HttpSession session,
                                                   DirectRegistrationFacade.Attempt attempt) {
        // 필수값, 범위 확인, 유효하지 않다면 validate() 안에서 예외 throw
        request.validate();

        var admin = admins.findForUpdate(reviewer).filter(AdminUserEntity::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 관리자만 승인할 수 있습니다."));
        var now = java.time.OffsetDateTime.now();
        var receipt = receipts.findByAdminIdAndIdempotencyKey(admin.getId(), key);
        if (receipt.isPresent() && receipt.get().active(now)) {
            if (!receipt.get().matches(hash))
                throw new DirectRegistrationException(409, "IDEMPOTENCY_CONFLICT", "다른 요청에 사용된 멱등키입니다.");
            try { return mapper.readValue(receipt.get().body(), DirectRegistrationResult.class); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("성공 영수증 형식 오류"); }
        }
        if (receipt.isPresent()) { receipts.delete(receipt.get()); receipts.flush(); }
        coordinator.lock();
        //	토큰에서 서버 보관 장소 복원
        var place = selections.resolve(request.placeSelectionToken(), reviewer, session);
        attempt.placeId = place.id();
        var chef = chefRepository.findById(request.chefId()).orElseThrow(() -> missing("셰프"));
        var video = videos.findById(request.videoId()).orElseThrow(() -> missing("영상"));
        //	같은 셰프·장소·영상 관계가 있는지 확인
        mentionRepository.findRelationshipId(chef.getId(), place.id(), video.getId())
            .ifPresent(id -> { throw DirectRegistrationException.duplicate(id); });
        //	식당 재사용 또는 생성
        var restaurant = restaurants.getOrCreate(place);
        var mention = mentions.approveDirect(chef, restaurant, video, request.mentionType(), request.timestampSeconds(),
            request.verificationNote(), admin);
        var result = new DirectRegistrationResult(mention.getId(), restaurant.getId(), mention.getVersion(), "APPROVED", "PUBLISHED");
        String body = json(result);
        // Store the resulting relation snapshot; selection tokens and authentication data are excluded.
        audits.saveAndFlush(new AdminAuditEventEntity(admin.getId(), result,
            json(java.util.Map.of("result", result, "mention", mention.toDto()))));
        receipts.saveAndFlush(new AdminRequestReceiptEntity(admin.getId(), key, hash, body, now));
        return result;
    }
    @Transactional
    public MentionDraftApprovalResult approveDraft(long id, MentionDraftApprovalRequest request, String key, String hash,
        String username, MentionDraftApprovalFacade.Attempt attempt) {
        var admin = admins.findForUpdate(username).filter(AdminUserEntity::isEnabled)
            .orElseThrow(() -> new MentionDraftApprovalException(403, "ADMIN_INACTIVE", "활성 관리자만 승인할 수 있습니다."));
        var now = java.time.OffsetDateTime.now();
        var receipt = receipts.findByAdminIdAndIdempotencyKey(admin.getId(), key);
        String path = MentionDraftApprovalFacade.path(id);
        if (receipt.isPresent() && receipt.get().active(now)) {
            if (!receipt.get().matches(path, hash, 200))
                throw new MentionDraftApprovalException(409, "IDEMPOTENCY_CONFLICT", "다른 요청에 사용된 멱등키입니다.");
            try { return mapper.readValue(receipt.get().body(), MentionDraftApprovalResult.class); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("승인 영수증 형식 오류"); }
        }
        if (receipt.isPresent()) { receipts.delete(receipt.get()); receipts.flush(); }
        coordinator.lock();
        var draft = drafts.findForUpdate(id).filter(MentionDraftEntity::isCreate).orElseThrow(MentionDraftApprovalException::missing);
        if (!draft.isEditable())
            throw new MentionDraftApprovalException(409, "DRAFT_ALREADY_APPLIED", "이미 적용된 초안입니다.",
                java.util.Map.of("currentVersion", draft.getVersion(), "appliedMentionId", draft.getAppliedMentionId()));
        if (draft.getVersion() != request.expectedVersion())
            throw new MentionDraftApprovalException(409, "STALE_VERSION", "초안을 다시 조회하세요.",
                java.util.Map.of("currentVersion", draft.getVersion()));
        var missing = new java.util.ArrayList<String>();
        if (draft.getRestaurantId() < 1) missing.add("restaurantId");
        if (draft.getChefId() == null) missing.add("chefId");
        if (draft.getVideoId() == null) missing.add("videoId");
        if (draft.getMentionType() == null) missing.add("mentionType");
        if (!missing.isEmpty())
            throw new MentionDraftApprovalException(400, "DRAFT_INCOMPLETE", "초안의 필수 항목을 확인하세요.", java.util.Map.of("missingFields", missing));
        if ((draft.getTimestampSeconds() != null && draft.getTimestampSeconds() < 0)
            || (draft.getVerificationNote() != null && draft.getVerificationNote().length() > 2000))
            throw MentionDraftApprovalException.invalid();
        var restaurant = restaurantRepository.findById(draft.getRestaurantId()).orElseThrow(MentionDraftApprovalException::missing);
        var chef = chefRepository.findById(draft.getChefId()).orElseThrow(MentionDraftApprovalException::missing);
        var video = videos.findById(draft.getVideoId()).orElseThrow(MentionDraftApprovalException::missing);
        attempt.relationship(chef.getId(), restaurant.getId(), video.getId());
        mentionRepository.findStoredRelationshipId(chef.getId(), restaurant.getId(), video.getId())
            .ifPresent(existing -> { throw MentionDraftApprovalException.duplicate(existing); });
        var before = draft.toDto(MentionDraftResult.Restaurant.from(restaurant));
        var mention = mentions.approveDirect(chef, restaurant, video, draft.getMentionType(), draft.getTimestampSeconds(),
            draft.getVerificationNote(), admin);
        draft.apply(mention.getId(), admin.getId());
        drafts.flush();
        var result = new MentionDraftApprovalResult(id, draft.getVersion(), "APPLIED", mention.getId(), restaurant.getId(),
            mention.getVersion(), "APPROVED", "PUBLISHED");
        var after = java.util.Map.of("draft", draft.toDto(MentionDraftResult.Restaurant.from(restaurant)),
            "mention", mention.toDto(), "result", result);
        audits.saveAndFlush(AdminAuditEventEntity.approveDraft(admin.getId(), id, mention.getId(), restaurant.getId(), json(before), json(after)));
        receipts.saveAndFlush(new AdminRequestReceiptEntity(admin.getId(), key, path, hash, 200, json(result), now));
        return result;
    }
    private DirectRegistrationException missing(String resource) {
        return new DirectRegistrationException(404, "RESOURCE_NOT_FOUND", resource + "을 찾을 수 없습니다.");
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("승인 결과 저장 실패"); }
    }
}
