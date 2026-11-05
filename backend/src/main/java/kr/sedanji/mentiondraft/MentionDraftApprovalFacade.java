package kr.sedanji.mentiondraft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import kr.sedanji.mention.MentionRepository;
import kr.sedanji.registration.RegistrationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class MentionDraftApprovalFacade {
    private final RegistrationService registrations;
    private final MentionRepository mentions;
    public MentionDraftApprovalFacade(RegistrationService registrations, MentionRepository mentions) {
        this.registrations = registrations; this.mentions = mentions;
    }
    /** Only validated scalar identifiers survive rollback. Never retain managed entities here. */
    public static final class Attempt {
        private Long chefId, restaurantId;
        private String videoId;
        public void relationship(long chef, long restaurant, String video) {
            chefId = chef; restaurantId = restaurant; videoId = video;
        }
    }
    public static String path(long id) { return MentionDraftService.PATH + "/" + id + "/approve"; }
    @Transactional(propagation=Propagation.NEVER)
    public MentionDraftApprovalResult approve(long id, MentionDraftApprovalRequest request, String key, String username) {
        if (id < 1 || request == null || request.expectedVersion() < 0 || key == null || key.isBlank() || key.length() > 128)
            throw MentionDraftApprovalException.invalid();
        var attempt = new Attempt();
        try { return registrations.approveDraft(id, request, key, hash(id, request), username, attempt); }
        catch (DataIntegrityViolationException ex) {
            // The transaction-owning service proxy has rolled back before this repository read.
            if (relationshipConflict(ex) && attempt.chefId != null) {
                var existing = mentions.findStoredRelationshipId(attempt.chefId, attempt.restaurantId, attempt.videoId);
                if (existing.isPresent()) throw MentionDraftApprovalException.duplicate(existing.get());
            }
            throw ex;
        } catch (ConcurrencyFailureException ex) {
            throw new MentionDraftApprovalException(409, "CONCURRENT_CONFLICT", "동시 변경이 발생했습니다. 초안을 다시 조회하세요.");
        }
    }
    private boolean relationshipConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                && "mentions_relationship_key".equals(constraint.getConstraintName())) return true;
        }
        return false;
    }
    private String hash(long id, MentionDraftApprovalRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                ("POST\n" + path(id) + "\n" + request.expectedVersion()).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("요청 해시 생성 실패"); }
    }
}
