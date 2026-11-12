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
import kr.sedanji.mention.MentionService;
import kr.sedanji.registration.RegistrationService;
import kr.sedanji.chef.ChefService;
import kr.sedanji.restaurant.RestaurantService;
import kr.sedanji.video.VideoService;
import kr.sedanji.importjob.VideoImportService;
import kr.sedanji.restaurantcandidate.RestaurantCandidateService;

@RestController
@RequestMapping("/api")
class ApiController {
    private final RegistrationService registrations;
    private final MentionService mentions;
    private final ChefService chefs;
    private final RestaurantService restaurants;
    private final VideoService videos;
    private final VideoImportService videoImports;
    private final CandidateMatchingService matching;
    private final RestaurantCandidateService candidates;
    ApiController(RegistrationService registrations, CandidateMatchingService matching, ChefService chefs, RestaurantService restaurants, VideoService videos, RestaurantCandidateService candidates, VideoImportService videoImports, MentionService mentions) { this.registrations = registrations; this.matching = matching; this.chefs = chefs; this.restaurants = restaurants; this.videos = videos; this.candidates = candidates; this.videoImports = videoImports; this.mentions = mentions; }
    record ImportRequest(@NotBlank @Size(max=500) String url) {}
    record ChefRequest(@NotBlank @Size(max=100) String name) {}
    record ManualCandidate(@NotBlank @Size(max=100) String name, @NotNull @Size(max=300) String address,
                           @NotBlank @Size(max=5000) String evidence) {}
    record Approval(@Positive long chefId, @NotBlank @Pattern(regexp="[0-9]{1,40}") String placeId,
                    @NotNull MentionType mentionType, @Min(0) Integer timestampSeconds,
                    @NotBlank @Size(max=5000) String evidence, @Size(max=2000) String note) {}
    record Rejection(@NotBlank @Size(max=2000) String note) {}
    record SearchRequest(@NotBlank @Size(max=100) String query) {}
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("headerName", token.getHeaderName(), "token", token.getToken()); }
    @GetMapping("/session") Map<String,Object> session(Principal principal) { return Map.of("authenticated", principal != null, "username", principal == null ? "" : principal.getName()); }
    @GetMapping("/restaurants") List<Restaurant> restaurants() { return restaurants.findPublishedRestaurants(); }
    @GetMapping("/admin/chefs") List<Chef> chefs() { return chefs.findChefs(); }
    @PostMapping("/admin/chefs") Chef chef(@Valid @RequestBody ChefRequest request) { return chefs.createChef(request.name()); }
    @GetMapping("/admin/videos") List<Video> videos() { return videos.findVideos(); }
    @PostMapping("/admin/videos") Video importVideo(@Valid @RequestBody ImportRequest request) { return videoImports.importVideo(request.url()); }
    @PostMapping("/admin/videos/{id}/candidates/reextract")
    Map<String, Integer> reextractCandidates(@PathVariable String id) {
        return Map.of("addedCount", videoImports.reextractCandidates(id));
    }
    @PostMapping("/admin/videos/{id}/candidates") @ResponseStatus(HttpStatus.CREATED)
    void addCandidate(@PathVariable String id, @Valid @RequestBody ManualCandidate request) { candidates.addManualCandidate(id, request.name(), request.address(), request.evidence()); }
    @GetMapping("/admin/candidates") List<Candidate> candidates() { return candidates.findCandidates(); }
    @GetMapping("/admin/mentions") List<Mention> mentions() { return mentions.findMentions(); }
    @GetMapping("/admin/candidates/{id}/places") CandidateSearch saved(@PathVariable long id) { return matching.saved(id); }
    @PostMapping("/admin/candidates/{id}/places") CandidateSearch search(@PathVariable long id, @Valid @RequestBody SearchRequest request) { return matching.search(id, request.query()); }
    @PostMapping("/admin/candidates/{id}/mentions") @ResponseStatus(HttpStatus.CREATED)
    void approve(@PathVariable long id, @Valid @RequestBody Approval request, Principal principal) {
        registrations.approveFromCandidate(id, request.chefId(), request.placeId(), request.mentionType(), request.timestampSeconds(), request.evidence(), request.note(), principal.getName());
    }
    @PostMapping("/admin/candidates/{id}/dismiss") @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismiss(@PathVariable long id, @Valid @RequestBody Rejection request) { candidates.dismissCandidate(id, request.note()); }
}
