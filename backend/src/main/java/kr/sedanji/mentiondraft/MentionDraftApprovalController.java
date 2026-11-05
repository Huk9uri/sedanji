package kr.sedanji.mentiondraft;

import com.fasterxml.jackson.databind.JsonNode;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/mention-drafts")
public class MentionDraftApprovalController {
    private final MentionDraftApprovalFacade approvals;
    public MentionDraftApprovalController(MentionDraftApprovalFacade approvals) { this.approvals = approvals; }
    @PostMapping("/{id}/approve")
    public MentionDraftApprovalResult approve(@PathVariable long id, @RequestBody JsonNode body,
        @RequestHeader(value="Idempotency-Key", required=false) String key, Principal principal) {
        return approvals.approve(id, MentionDraftApprovalRequest.parse(body), key, principal.getName());
    }
}
