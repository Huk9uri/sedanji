package kr.sedanji.publication;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PublicationFacade {
    private final PublicationService service;
    public PublicationFacade(PublicationService service) {this.service=service;}
    @Transactional(propagation=Propagation.NEVER)
    public JsonNode write(long id,boolean restore,JsonNode body,String key,String user) {
        if(id<1 || key==null || key.isBlank() || key.length()>128)throw PublicationException.invalid();
        try {return service.write(id,restore,body,key,user);}
        catch(ConcurrencyFailureException e) {throw PublicationException.conflict("STALE_VERSION");}
        catch(DataAccessException e) {throw PublicationException.conflict("INVALID_STATE");}
    }
}
