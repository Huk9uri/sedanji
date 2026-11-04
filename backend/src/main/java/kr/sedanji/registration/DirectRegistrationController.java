package kr.sedanji.registration;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import kr.sedanji.Models.Video;
import kr.sedanji.PlaceSelectionService;
import kr.sedanji.video.VideoMetadataService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class DirectRegistrationController {
    private final PlaceSelectionService places;
    private final DirectRegistrationFacade registrations;
    private final VideoMetadataService videos;
    public DirectRegistrationController(PlaceSelectionService places, DirectRegistrationFacade registrations, VideoMetadataService videos) {
        this.places = places; this.registrations = registrations; this.videos = videos;
    }

    @GetMapping("/places")
    public PlaceSelectionService.Search search(@RequestParam String query, Principal principal, HttpSession session) {
        return places.search(query, principal.getName(), session);
    }

    @PostMapping("/mentions") @ResponseStatus(HttpStatus.CREATED)
    public DirectRegistrationResult approve(@Valid @RequestBody DirectRegistrationRequest request,
                                            @RequestHeader(value="Idempotency-Key", required=false) String key,
                                            Principal principal, HttpSession session) {
        return registrations.approve(request, key, principal.getName(), session);
    }

    public record MetadataRequest(@NotBlank @Size(max=500) String url) {}

    @PostMapping("/videos/metadata") @ResponseStatus(HttpStatus.CREATED)
    public Video metadata(@Valid @RequestBody MetadataRequest request) {
        return videos.register(request.url());
    }
}
