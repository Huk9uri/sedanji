package kr.sedanji;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import static kr.sedanji.Models.*;
import kr.sedanji.mention.MentionType;

@RestController
@RequestMapping("/api")
class ApiController {
    private final CatalogService service;
    private final CandidateMatchingService matching;
    ApiController(CatalogService service, CandidateMatchingService matching) { this.service = service; this.matching = matching; }
    record ImportRequest(@NotBlank @Size(max=500) String url) {}
    record ChefRequest(@NotBlank @Size(max=100) String name) {}
    record ManualCandidate(@NotBlank @Size(max=100) String name, @NotNull @Size(max=300) String address,
                           @NotBlank @Size(max=5000) String evidence) {}
    record Approval(@Positive long chefId, @NotBlank @Pattern(regexp="[0-9]{1,40}") String placeId,
                    @NotNull MentionType mentionType, @Min(0) Integer timestampSeconds,
                    @NotBlank @Size(max=5000) String evidence, @NotBlank @Size(max=2000) String note) {}
    record Rejection(@NotBlank @Size(max=2000) String note) {}
    record SearchRequest(@NotBlank @Size(max=100) String query) {}
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("headerName", token.getHeaderName(), "token", token.getToken()); }
    @GetMapping("/session") Map<String,Object> session(Principal principal) { return Map.of("authenticated", principal != null, "username", principal == null ? "" : principal.getName()); }
    @GetMapping("/restaurants") List<Restaurant> restaurants() { return service.restaurants(); }
    @GetMapping("/admin/chefs") List<Chef> chefs() { return service.chefs(); }
    @PostMapping("/admin/chefs") Chef chef(@Valid @RequestBody ChefRequest request) { return service.createChef(request.name()); }
    @GetMapping("/admin/videos") List<Video> videos() { return service.videos(); }
    @PostMapping("/admin/videos") Video importVideo(@Valid @RequestBody ImportRequest request) { return service.importVideo(request.url()); }
    @PostMapping("/admin/videos/{id}/candidates") @ResponseStatus(HttpStatus.CREATED)
    void addCandidate(@PathVariable String id, @Valid @RequestBody ManualCandidate request) { service.addCandidate(id, request.name(), request.address(), request.evidence()); }
    @GetMapping("/admin/candidates") List<Candidate> candidates() { return service.candidates(); }
    @GetMapping("/admin/mentions") List<Mention> mentions() { return service.mentions(); }
    @GetMapping("/admin/candidates/{id}/places") CandidateSearch saved(@PathVariable long id) { return matching.saved(id); }
    @PostMapping("/admin/candidates/{id}/places") CandidateSearch search(@PathVariable long id, @Valid @RequestBody SearchRequest request) { return matching.search(id, request.query()); }
    @PostMapping("/admin/candidates/{id}/mentions") @ResponseStatus(HttpStatus.CREATED)
    void approve(@PathVariable long id, @Valid @RequestBody Approval request, Principal principal) {
        service.approve(id, request.chefId(), request.placeId(), request.mentionType(), request.timestampSeconds(), request.evidence(), request.note(), principal.getName());
    }
    @PostMapping("/admin/candidates/{id}/dismiss") @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismiss(@PathVariable long id, @Valid @RequestBody Rejection request) { service.dismiss(id, request.note()); }
}
