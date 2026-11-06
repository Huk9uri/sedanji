package kr.sedanji.correction;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Transaction failures are translated only after the service proxy has rolled back. */
@Service
public class ReapprovalFacade {
    private final CorrectionService service;
    public ReapprovalFacade(CorrectionService service){this.service=service;}
    @Transactional(propagation=Propagation.NEVER)
    public JsonNode write(CorrectionKind kind,long id,String action,JsonNode body,String key,String path,String user,HttpSession session) {
        if(id<1)throw CorrectionException.invalid();
        if((action.equals("create")||action.equals("approve"))&&(key==null||key.isBlank()||key.length()>128))throw CorrectionException.invalid();
        try {
            return switch(action){
                case "create"->service.create(kind,id,body,key,path,user,session);
                case "patch","rebase"->service.patch(kind,id,body,user,session,action.equals("rebase"));
                case "approve"->service.approve(kind,id,body,key,path,user);
                default->throw CorrectionException.invalid();
            };
        }catch(DataIntegrityViolationException e){
            for(Throwable cause=e;cause!=null;cause=cause.getCause()){
                String message=cause.getMessage();if(message==null)continue;
                if(message.contains("mentions_relationship_key"))throw CorrectionException.conflict("DUPLICATE_RELATION");
                if(message.contains("open_correction_key")||message.contains("revisions_open_key"))throw CorrectionException.conflict("OPEN_REVISION_EXISTS");
            }
            throw e;
        }catch(ConcurrencyFailureException e){throw CorrectionException.conflict("STALE_VERSION");}
    }
}
