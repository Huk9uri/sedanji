package kr.sedanji.restaurant;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(0)
@RestControllerAdvice(assignableTypes = AdminRestaurantController.class)
public class AdminRestaurantErrors {
    @ExceptionHandler(AdminRestaurantException.class)
    public ResponseEntity<?> known(AdminRestaurantException ex) {
        return ResponseEntity.status(ex.status).body(Map.of("code", ex.code, "message", ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> invalid() { return known(AdminRestaurantException.invalid()); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected() {
        return ResponseEntity.internalServerError().body(Map.of("code", "INTERNAL_ERROR", "message", "식당 정보를 조회하지 못했습니다."));
    }
}
