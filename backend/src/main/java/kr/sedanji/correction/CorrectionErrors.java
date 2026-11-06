package kr.sedanji.correction;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import kr.sedanji.registration.DirectRegistrationException;

@Order(0)
@RestControllerAdvice(assignableTypes=CorrectionController.class)
public class CorrectionErrors {
    @ExceptionHandler(CorrectionException.class)
    public ResponseEntity<?> correction(CorrectionException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));}
    @ExceptionHandler(DirectRegistrationException.class)
    public ResponseEntity<?> place(DirectRegistrationException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));}
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class})
    public ResponseEntity<?> invalid(){return correction(CorrectionException.invalid());}
}
