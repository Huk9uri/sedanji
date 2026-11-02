package kr.sedanji;

import java.util.List;
import kr.sedanji.admin.*;
import kr.sedanji.chef.*;
import kr.sedanji.mention.*;
import kr.sedanji.restaurant.*;
import kr.sedanji.restaurantcandidate.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static kr.sedanji.Models.*;

@Service @Transactional(readOnly = true)
class CatalogService {
    private final ChefService chefs;
    private final MentionRepository mentions;
    private final RestaurantService restaurants;
    private final RestaurantCandidateService candidates;
    private final AdminUserRepository admins;
    CatalogService(ChefService chefs, MentionRepository mentions, RestaurantService restaurants,
                   RestaurantCandidateService candidates,
                   AdminUserRepository admins) {
        this.chefs = chefs; this.mentions = mentions; this.restaurants = restaurants;
        this.candidates = candidates; this.admins = admins;
    }
    public List<Mention> mentions() { return mentions.findAllByOrderByIdDesc().stream().map(MentionEntity::toDto).toList(); }
    @Transactional public void approve(long id, long chefId, String placeId, MentionType type, Integer timestamp,
                                       String evidence, String note, String reviewer) {
        if (type == null || evidence == null || evidence.isBlank() || (timestamp != null && timestamp < 0))
            throw new IllegalArgumentException("언급 유형과 확인 근거를 입력하세요.");
        var candidate = candidates.getForUpdate(id);
        var chef = chefs.getRequired(chefId);
        var admin = admins.findByUsername(reviewer).filter(AdminUserEntity::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 관리자만 승인할 수 있습니다."));
        Place place = candidates.selectedPlace(id, placeId);
        var restaurant = restaurants.getOrCreate(place);
        if (mentions.existsByChefIdAndRestaurantIdAndVideoId(chefId, restaurant.getId(), candidate.getImportJob().getVideo().getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 셰프·식당·영상의 관계가 이미 있습니다.");
        mentions.saveAndFlush(new MentionEntity(candidate, chef, restaurant, type, timestamp, evidence, note, admin));
        candidates.resolveCandidate(id);
    }
}
