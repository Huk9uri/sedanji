package kr.sedanji.chef;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChefRepository extends JpaRepository<ChefEntity, Long> {
    List<ChefEntity> findAllByOrderByNameAsc();
    Optional<ChefEntity> findByName(String name);
}
