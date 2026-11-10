package kr.sedanji.publicweb;

import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final PublicQueryService service;
    public PublicController(PublicQueryService service) { this.service=service; }
    @ModelAttribute void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @GetMapping("/restaurants") public PublicResult.Area restaurants(@RequestParam MultiValueMap<String,String> p) {
        return service.area(PublicInput.bounds(p));
    }
    @GetMapping("/chefs") public PublicResult.Page<PublicResult.ChefItem> chefs(@RequestParam MultiValueMap<String,String> p) {
        return service.chefs(PublicInput.page(p, false));
    }
    @GetMapping("/restaurants/{id}") public PublicResult.Detail detail(@PathVariable String id, @RequestParam MultiValueMap<String,String> p) {
        return service.detail(PublicInput.positive(id), PublicInput.page(p, true));
    }
    @ExceptionHandler(PublicInput.Failure.class) ResponseEntity<?> failure(PublicInput.Failure ex) {
        return ResponseEntity.status(ex.status).body(Map.of("code", ex.code, "message", ex.getMessage()));
    }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> database() {
        return ResponseEntity.internalServerError().body(Map.of("code", "QUERY_FAILED", "message", "목록을 불러오지 못했습니다. 다시 시도하세요."));
    }
}
