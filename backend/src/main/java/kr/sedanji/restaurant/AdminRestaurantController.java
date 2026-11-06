package kr.sedanji.restaurant;

import java.security.Principal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/restaurants")
public class AdminRestaurantController {
    private final AdminRestaurantQueryService service;
    public AdminRestaurantController(AdminRestaurantQueryService service) { this.service = service; }
    @GetMapping
    public AdminRestaurantResult.Page<AdminRestaurantResult.Item> list(
        @RequestParam MultiValueMap<String, String> params, Principal principal) {
        return service.list(AdminRestaurantQuery.parse(params, false), principal.getName());
    }
    @GetMapping("/{id}")
    public AdminRestaurantResult.Detail detail(@PathVariable long id,
        @RequestParam MultiValueMap<String, String> params, Principal principal) {
        return service.detail(id, AdminRestaurantQuery.parse(params, true), principal.getName());
    }
}
