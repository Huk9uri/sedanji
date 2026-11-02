package kr.sedanji.chef;

import java.util.List;
import kr.sedanji.Models.Chef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ChefService {
    private final ChefRepository chefs;
    public ChefService(ChefRepository chefs) { this.chefs = chefs; }

    public List<Chef> findChefs() {
        return chefs.findAllByOrderByNameAsc().stream().map(e -> new Chef(e.getId(), e.getName())).toList();
    }
    @Transactional
    public Chef createChef(String name) {
        var entity = chefs.saveAndFlush(new ChefEntity(name.strip()));
        return new Chef(entity.getId(), entity.getName());
    }
    public ChefEntity getRequired(long id) {
        return chefs.findById(id).orElseThrow(() -> new IllegalArgumentException("셰프를 선택하세요."));
    }
}
