package kr.sedanji.mentiondraft;

import java.util.LinkedHashMap;
import java.util.Map;
import kr.sedanji.registration.DirectRegistrationException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@Order(0) @RestControllerAdvice(assignableTypes=MentionDraftController.class)
public class MentionDraftErrors {
    @ExceptionHandler(MentionDraftException.class)
    public ResponseEntity<?> draft(MentionDraftException ex) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("code", ex.code()); body.put("message", ex.getMessage());
        if (ex.currentVersion() != null) body.put("currentVersion", ex.currentVersion());
        return ResponseEntity.status(ex.status()).body(body);
    }
    @ExceptionHandler(DirectRegistrationException.class)
    public ResponseEntity<?> selection(DirectRegistrationException ex) {
        return draft(new MentionDraftException(ex.status(), ex.code(), ex.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalid() { return draft(MentionDraftException.invalid()); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException ex) { return ResponseEntity.status(ex.getStatusCode()).build(); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected() {
        return ResponseEntity.internalServerError().body(Map.of("code", "INTERNAL_ERROR", "message", "초안을 저장하거나 조회하지 못했습니다."));
    }
}
