package kr.sedanji;

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
    private final MentionService mentions;
    private final RestaurantService restaurants;
    private final RestaurantCandidateService candidates;
    private final AdminUserRepository admins;
    CatalogService(ChefService chefs, MentionService mentions, RestaurantService restaurants,
                   RestaurantCandidateService candidates,
                   AdminUserRepository admins) {
        this.chefs = chefs; this.mentions = mentions; this.restaurants = restaurants;
        this.candidates = candidates; this.admins = admins;
    }
    @Transactional public void approve(long id, long chefId, String placeId, MentionType type, Integer timestamp,
                                       String evidence, String note, String reviewer) {
        mentions.validateApproval(type, timestamp, evidence);
        var candidate = candidates.getForUpdate(id);
        var chef = chefs.getRequired(chefId);
        var admin = admins.findByUsername(reviewer).filter(AdminUserEntity::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 관리자만 승인할 수 있습니다."));
        Place place = candidates.selectedPlace(id, placeId);
        var restaurant = restaurants.getOrCreate(place);
        mentions.approve(chef, restaurant, candidate.getImportJob().getVideo(), candidate, type, timestamp, evidence, note, admin);
        candidates.resolveCandidate(id);
    }
}
