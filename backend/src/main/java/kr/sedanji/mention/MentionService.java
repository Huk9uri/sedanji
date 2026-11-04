package kr.sedanji.mention;

import java.util.List;
import kr.sedanji.Models.Mention;
import kr.sedanji.Models.Source;
import kr.sedanji.admin.AdminUserEntity;
import kr.sedanji.chef.ChefEntity;
import kr.sedanji.restaurant.RestaurantEntity;
import kr.sedanji.restaurantcandidate.RestaurantCandidateEntity;
import kr.sedanji.video.VideoEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MentionService {
    private final MentionRepository mentions;
    public MentionService(MentionRepository mentions) { this.mentions = mentions; }

    @Transactional(readOnly = true)
    public List<Mention> findMentions() {
        return mentions.findAllByOrderByIdDesc().stream().map(MentionEntity::toDto).toList();
    }
    @Transactional(readOnly = true)
    public List<Source> findPublishedSources(long restaurantId) {
        return mentions.findByRestaurantIdAndVerificationStatusAndPublicationStatusOrderByIdAsc(
            restaurantId, "APPROVED", "PUBLISHED").stream().map(MentionEntity::toSource).toList();
    }
    public void validateApproval(MentionType type, Integer timestamp, String evidence) {
        if (type == null || evidence == null || evidence.isBlank() || (timestamp != null && timestamp < 0))
            throw new IllegalArgumentException("언급 유형과 확인 근거를 입력하세요.");
    }
    // Registration owns the transaction, including place creation and candidate resolution.
    @Transactional(propagation = Propagation.MANDATORY)
    public MentionEntity approveDirect(ChefEntity chef, RestaurantEntity restaurant, VideoEntity video,
                                       MentionType type, Integer timestamp, String note, AdminUserEntity reviewer) {
        return mentions.saveAndFlush(MentionEntity.direct(video, chef, restaurant, type, timestamp, note, reviewer));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void approve(ChefEntity chef, RestaurantEntity restaurant, VideoEntity video,
                        RestaurantCandidateEntity candidate, MentionType type, Integer timestamp,
                        String evidence, String note, AdminUserEntity reviewer) {
        validateApproval(type, timestamp, evidence);
        if (reviewer == null || !reviewer.isEnabled())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 관리자만 승인할 수 있습니다.");
        if (mentions.existsByChefIdAndRestaurantIdAndVideoId(chef.getId(), restaurant.getId(), video.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 셰프·식당·영상의 관계가 이미 있습니다.");
        mentions.saveAndFlush(new MentionEntity(video, candidate, chef, restaurant, type, timestamp, evidence, note, reviewer));
    }
}
