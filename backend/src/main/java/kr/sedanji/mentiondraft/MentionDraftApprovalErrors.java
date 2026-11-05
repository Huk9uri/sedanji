package kr.sedanji.mentiondraft;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(0)
@RestControllerAdvice(assignableTypes=MentionDraftApprovalController.class)
public class MentionDraftApprovalErrors {
    @ExceptionHandler(MentionDraftApprovalException.class)
    public ResponseEntity<?> approval(MentionDraftApprovalException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.code()); body.put("message", ex.getMessage()); body.putAll(ex.details());
        return ResponseEntity.status(ex.status()).body(body);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalid() { return approval(MentionDraftApprovalException.invalid()); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected() {
        return ResponseEntity.internalServerError().body(Map.of("code", "INTERNAL_ERROR", "message", "초안을 승인하지 못했습니다."));
    }
}
