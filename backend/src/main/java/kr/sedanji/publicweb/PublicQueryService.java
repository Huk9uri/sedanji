package kr.sedanji.publicweb;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class PublicQueryService {
    private final PublicQueryRepository repository;
    public PublicQueryService(PublicQueryRepository repository) { this.repository=repository; }
    public PublicResult.Area area(PublicInput.Bounds q) { return repository.area(q); }
    public PublicResult.Page<PublicResult.ChefItem> chefs(PublicInput.Page q) { return repository.chefs(q); }
    public PublicResult.Detail detail(long id, PublicInput.Page q) { return repository.detail(id,q); }
}
