package kr.sedanji.mentiondraft;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin")
public class MentionDraftController {
    private final MentionDraftService drafts;
    public MentionDraftController(MentionDraftService drafts) { this.drafts = drafts; }
    @PostMapping("/mention-drafts") @ResponseStatus(HttpStatus.CREATED)
    public MentionDraftResult create(@RequestBody JsonNode body,
        @RequestHeader(value="Idempotency-Key", required=false) String key, Principal principal, HttpSession session) {
        return drafts.create(MentionDraftInput.create(body), key, principal.getName(), session);
    }
    @GetMapping("/mention-drafts/{id}")
    public MentionDraftResult get(@PathVariable long id, Principal principal) { return drafts.get(id, principal.getName()); }
    @GetMapping("/restaurants/{restaurantId}/mention-drafts")
    public MentionDraftResult.Page list(@PathVariable long restaurantId, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="DRAFT") String status, Principal principal) {
        return drafts.list(restaurantId, page, size, status, principal.getName());
    }
    @PatchMapping("/mention-drafts/{id}")
    public MentionDraftResult patch(@PathVariable long id, @RequestBody JsonNode body, Principal principal, HttpSession session) {
        return drafts.patch(id, MentionDraftInput.patch(body), principal.getName(), session);
    }
}
