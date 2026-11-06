package kr.sedanji.restaurant;

import java.util.List;
import kr.sedanji.admin.AdminUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static kr.sedanji.restaurant.AdminRestaurantResult.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminRestaurantQueryService {
    private final AdminUserRepository admins;
    private final AdminRestaurantQueryRepository repository;
    private static final AdminRestaurantQueryRepository.Aggregate EMPTY =
        new AdminRestaurantQueryRepository.Aggregate(new Summary(0, 0, false, null, 0, 0, 0), 0);
    public AdminRestaurantQueryService(AdminUserRepository admins, AdminRestaurantQueryRepository repository) {
        this.admins = admins; this.repository = repository;
    }
    private void active(String username) {
        if (admins.findByUsername(username).filter(a -> a.isEnabled()).isEmpty())
            throw new AdminRestaurantException(403, "ADMIN_INACTIVE", "활성 관리자 권한이 필요합니다.");
    }
    public Page<Item> list(AdminRestaurantQuery q, String username) {
        active(username);
        long total = repository.countMatching(q);
        var restaurants = repository.findPage(q);
        List<Long> ids = restaurants.stream().map(Restaurant::id).toList();
        var summaries = repository.summaries(ids);
        var chefs = repository.chefPreview(ids);
        var items = restaurants.stream().map(r -> {
            var a = summaries.getOrDefault(r.id(), EMPTY);
            var s = a.summary();
            return new Item(r.id(), r.name(), r.roadAddress(), r.address(), r.createdAt(), s.relationCount(),
                s.publicRelationCount(), s.hasPublicRelation(), a.chefCount(), chefs.getOrDefault(r.id(), List.of()),
                a.chefCount() > 3, s.needsSourceReview(), s.sourceCount(), s.uncheckedSourceCount(), s.reviewRequiredSourceCount());
        }).toList();
        return Page.of(items, total, q.page(), q.size());
    }
    public Detail detail(long id, AdminRestaurantQuery q, String username) {
        active(username);
        var restaurant = repository.detail(id).orElseThrow(() ->
            new AdminRestaurantException(404, "RESOURCE_NOT_FOUND", "식당을 찾을 수 없습니다."));
        var summary = repository.summaries(List.of(id)).getOrDefault(id, EMPTY).summary();
        return new Detail(restaurant, summary,
            Page.of(repository.mentionPage(id, q), summary.relationCount(), q.page(), q.size()));
    }
}
