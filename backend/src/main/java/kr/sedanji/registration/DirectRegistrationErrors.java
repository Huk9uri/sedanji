package kr.sedanji.registration;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;

/** New contracts are scoped to new endpoints; existing error bodies remain unchanged. */
@Order(0)
@RestControllerAdvice(assignableTypes=DirectRegistrationController.class)
public class DirectRegistrationErrors {
    @ExceptionHandler(DirectRegistrationException.class)
    public ResponseEntity<?> direct(DirectRegistrationException ex) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("code", ex.code()); body.put("message", ex.getMessage());
        if (ex.existingMentionId() != null) body.put("existingMentionId", ex.existingMentionId());
        return ResponseEntity.status(ex.status()).body(body);
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class, IllegalArgumentException.class})
    public ResponseEntity<?> invalid() { return direct(DirectRegistrationException.invalid()); }
}
