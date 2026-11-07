package kr.sedanji.publication;

import java.util.Map;
import kr.sedanji.correction.CorrectionException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(0)
@RestControllerAdvice(assignableTypes=PublicationController.class)
public class PublicationErrors {
    @ExceptionHandler(PublicationException.class)
    public ResponseEntity<?> publication(PublicationException e) {return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));}
    @ExceptionHandler(CorrectionException.class)
    public ResponseEntity<?> missing(CorrectionException e) {
        return publication(new PublicationException(e.status(),e.status()==404?"RESOURCE_NOT_FOUND":e.code(),e.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class})
    public ResponseEntity<?> invalid() {return publication(PublicationException.invalid());}
}
