package kr.sedanji.publication;

import com.fasterxml.jackson.databind.node.ObjectNode;
import kr.sedanji.admin.*;
import kr.sedanji.correction.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PublicationQueryService {
    private final CorrectionStore store;
    private final AdminUserRepository admins;
    public PublicationQueryService(CorrectionStore store,AdminUserRepository admins) { this.store=store;this.admins=admins; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ObjectNode get(long id,String username) {
        admins.findByUsername(username).filter(AdminUserEntity::isEnabled)
            .orElseThrow(()->new PublicationException(403,"ADMIN_INACTIVE","활성 관리자만 사용할 수 있습니다."));
        var context=context(id);
        var out=store.mapper().createObjectNode();
        out.set("mention",context.path("mention"));out.set("references",context.path("references"));
        out.put("contextHash",store.hash(context));
        var rows=store.db().queryForList("""
            select to_jsonb(e)::text from (select id,action,reason,actor_id as "actorId",occurred_at as "occurredAt",
              before_value as "beforeValue",after_value as "afterValue" from admin_audit_events
              where mention_id=? and action in ('SUSPEND_MENTION','RESTORE_MENTION')
              order by occurred_at desc,id desc limit 1) e
            """,String.class,id);
        out.set("latestControl",rows.isEmpty()?store.mapper().nullNode():store.parse(rows.get(0)));
        var reasons=store.db().queryForList("select reason from admin_audit_events where mention_id=? and action='SUSPEND_MENTION' order by occurred_at desc,id desc limit 1",String.class,id);
        out.put("lastSuspendReason",reasons.isEmpty()?null:reasons.get(0));
        return out;
    }
    ObjectNode context(long id) {
        // Shared correction snapshots preserve display values, reference versions and original provenance.
        var mention=store.snapshot(CorrectionKind.MENTION,id);
        var context=store.mapper().createObjectNode();context.put("schemaVersion",1);
        context.set("mention",mention);context.set("references",store.references(CorrectionKind.MENTION,mention));
        return context;
    }
}
