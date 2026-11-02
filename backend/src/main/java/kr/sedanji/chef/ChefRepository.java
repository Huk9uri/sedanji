package kr.sedanji.chef;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChefRepository extends JpaRepository<ChefEntity, Long> {
    List<ChefEntity> findAllByOrderByNameAsc();
}
