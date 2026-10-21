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

@RestController
@RequestMapping("/api")
class ApiController {
    private final CatalogService service;
    ApiController(CatalogService service) { this.service = service; }
    record ImportRequest(@NotBlank @Size(max=500) String url) {}
    record ChefRequest(@NotBlank @Size(max=100) String name) {}
    record ManualMention(@NotBlank @Size(max=100) String name, @NotNull @Size(max=300) String address,
                         @Min(0) int sourceLine, @NotBlank @Size(max=5000) String evidence) {}
    record Approval(@Positive long chefId, @NotBlank @Pattern(regexp="[0-9]{1,40}") String placeId,
                    @NotBlank @Size(max=2000) String note) {}
    record Rejection(@NotBlank @Size(max=2000) String note) {}
    record SearchRequest(@NotBlank @Size(max=100) String query) {}
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("headerName", token.getHeaderName(), "token", token.getToken()); }
    @GetMapping("/session") Map<String,Object> session(Principal principal) { return Map.of("authenticated", principal != null, "username", principal == null ? "" : principal.getName()); }
    @GetMapping("/restaurants") List<Restaurant> restaurants() { return service.restaurants(); }
    @GetMapping("/admin/chefs") List<Chef> chefs() { return service.chefs(); }
    @PostMapping("/admin/chefs") Chef chef(@Valid @RequestBody ChefRequest request) { return service.createChef(request.name()); }
    @GetMapping("/admin/videos") List<Video> videos() { return service.videos(); }
    @PostMapping("/admin/videos") Video importVideo(@Valid @RequestBody ImportRequest request) { return service.importVideo(request.url()); }
    @PostMapping("/admin/videos/{id}/mentions") @ResponseStatus(HttpStatus.CREATED)
    void addMention(@PathVariable String id, @Valid @RequestBody ManualMention request) { service.addMention(id, request.name(), request.address(), request.sourceLine(), request.evidence()); }
    @GetMapping("/admin/mentions") List<Mention> mentions() { return service.mentions(); }
    @PostMapping("/admin/mentions/{id}/places") PlaceSearch search(@PathVariable long id, @Valid @RequestBody SearchRequest request) { return service.search(id, request.query()); }
    @PostMapping("/admin/mentions/{id}/approve") @ResponseStatus(HttpStatus.NO_CONTENT)
    void approve(@PathVariable long id, @Valid @RequestBody Approval request, Principal principal) { service.approve(id, request.chefId(), request.placeId(), request.note(), principal.getName()); }
    @PostMapping("/admin/mentions/{id}/reject") @ResponseStatus(HttpStatus.NO_CONTENT)
    void reject(@PathVariable long id, @Valid @RequestBody Rejection request, Principal principal) { service.reject(id, request.note(), principal.getName()); }
}
