package kr.sedanji.registration;

import kr.sedanji.admin.AdminUserEntity;
import kr.sedanji.admin.AdminUserRepository;
import kr.sedanji.chef.ChefService;
import kr.sedanji.mention.MentionService;
import kr.sedanji.mention.MentionType;
import kr.sedanji.restaurant.RestaurantService;
import kr.sedanji.restaurantcandidate.RestaurantCandidateService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {
    private final ChefService chefs;
    private final MentionService mentions;
    private final RestaurantService restaurants;
    private final RestaurantCandidateService candidates;
    private final AdminUserRepository admins;

    public RegistrationService(ChefService chefs, MentionService mentions, RestaurantService restaurants,
                               RestaurantCandidateService candidates, AdminUserRepository admins) {
        this.chefs = chefs; this.mentions = mentions; this.restaurants = restaurants;
        this.candidates = candidates; this.admins = admins;
    }
    @Transactional
    public void approveFromCandidate(long candidateId, long chefId, String placeId, MentionType type,
                                     Integer timestamp, String evidence, String note, String reviewer) {
        mentions.validateApproval(type, timestamp, evidence);
        var candidate = candidates.getForUpdate(candidateId);
        var chef = chefs.getRequired(chefId);
        var admin = admins.findByUsername(reviewer).filter(AdminUserEntity::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 관리자만 승인할 수 있습니다."));
        var place = candidates.selectedPlace(candidateId, placeId);
        var restaurant = restaurants.getOrCreate(place);
        mentions.approve(chef, restaurant, candidate.getImportJob().getVideo(), candidate, type, timestamp, evidence, note, admin);
        candidates.resolveCandidate(candidateId);
    }
}
