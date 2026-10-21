package kr.sedanji;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ExternalApis.ApiFailure.class)
    ResponseEntity<?> external(ExternalApis.ApiFailure ex) { return ResponseEntity.status(502).body(Map.of("message", ex.getMessage())); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException ex) { return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation() { return ResponseEntity.badRequest().body(Map.of("message", "입력값과 필수 항목을 확인하세요.")); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException ex) { return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", ex.getReason() == null ? "요청 처리 실패" : ex.getReason())); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> database() { return ResponseEntity.status(409).body(Map.of("message", "저장 충돌이 발생했습니다. 목록을 새로고침하세요.")); }
}
