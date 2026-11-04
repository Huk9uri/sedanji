package kr.sedanji.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import kr.sedanji.mention.MentionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DirectRegistrationFacade {
    private final RegistrationService registrations;
    private final MentionRepository mentions;
    private final ObjectMapper mapper;
    public DirectRegistrationFacade(RegistrationService registrations, MentionRepository mentions, ObjectMapper mapper) {
        this.registrations = registrations; this.mentions = mentions; this.mapper = mapper;
    }
    public static final class Attempt { String placeId; }
    @Transactional(propagation=Propagation.NEVER)
    public DirectRegistrationResult approve(DirectRegistrationRequest request, String key, String reviewer, HttpSession session) {
        request.validate();
        if (key == null || key.isBlank() || key.length() > 128) throw DirectRegistrationException.invalid();
        var attempt = new Attempt();
        try { return registrations.approveDirect(request, key, hash(request), reviewer, session, attempt); }
        catch (DataIntegrityViolationException ex) {
            // The service proxy has completed rollback before this separate read.
            if (relationshipConflict(ex) && attempt.placeId != null) {
                var existing = mentions.findRelationshipId(request.chefId(), attempt.placeId, request.videoId());
                if (existing.isPresent()) throw DirectRegistrationException.duplicate(existing.get());
            }
            throw ex;
        }
    }
    private boolean relationshipConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                && "mentions_relationship_key".equals(constraint.getConstraintName())) return true;
        }
        return false;
    }
    private String hash(DirectRegistrationRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("요청 해시 생성 실패");
        }
    }
}
