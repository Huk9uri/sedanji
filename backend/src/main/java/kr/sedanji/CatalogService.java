package kr.sedanji;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static kr.sedanji.Models.*;

@Service
class CatalogService {
    private final JdbcTemplate db;
    private final ExternalApis apis;
    private final DescriptionExtractor extractor;
    CatalogService(JdbcTemplate db, ExternalApis apis, DescriptionExtractor extractor) {
        this.db = db; this.apis = apis; this.extractor = extractor;
    }
    List<Chef> chefs() { return db.query("SELECT id,name FROM chefs ORDER BY name", (rs, n) -> new Chef(rs.getLong(1), rs.getString(2))); }
    Chef createChef(String name) {
        return db.queryForObject("INSERT INTO chefs(name) VALUES (?) ON CONFLICT(name) DO UPDATE SET name=EXCLUDED.name RETURNING id,name",
            (rs, n) -> new Chef(rs.getLong(1), rs.getString(2)), name.strip());
    }
    List<Video> videos() { return db.query("SELECT * FROM videos ORDER BY imported_at DESC", this::video); }
    private Video video(ResultSet rs, int n) throws SQLException {
        return new Video(rs.getString("id"), rs.getString("title"), rs.getString("channel_title"), rs.getObject("published_at", OffsetDateTime.class), rs.getString("description"), rs.getString("extraction_warning"));
    }
    @Transactional
    public Video importVideo(String url) {
        String id = DescriptionExtractor.videoId(url);
        var existing = db.query("SELECT * FROM videos WHERE id=?", this::video, id);
        if (!existing.isEmpty()) return existing.get(0); // Preserve reviewed evidence on repeat imports.
        Video v = apis.video(id);
        db.update("INSERT INTO videos(id,title,channel_title,published_at,description,extraction_warning) VALUES (?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING",
            v.id(), v.title(), v.channelTitle(), v.publishedAt(), v.description(), v.extractionWarning());
        for (Extracted e : extractor.extract(v.description())) insertMention(id, e);
        return v;
    }
    private void insertMention(String videoId, Extracted e) {
        db.update("INSERT INTO mentions(video_id,extracted_name,extracted_address,source_line,evidence) VALUES (?,?,?,?,?) ON CONFLICT(video_id,source_line,extracted_name) DO NOTHING",
            videoId, e.name(), e.address(), e.sourceLine(), e.evidence());
    }
    @Transactional
    public void addMention(String videoId, String name, String address, int sourceLine, String evidence) {
        var videos = db.query("SELECT * FROM videos WHERE id=?", this::video, videoId);
        if (videos.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다.");
        // Admin-supplied evidence must still occur verbatim in the stored official description.
        if (!videos.get(0).description().contains(evidence)) throw new IllegalArgumentException("근거는 저장된 설명란 원문에 포함되어야 합니다.");
        insertMention(videoId, new Extracted(name.strip(), address.strip(), sourceLine, evidence));
    }
    List<Mention> mentions() {
        return db.query("SELECT m.*,v.title AS video_title FROM mentions m JOIN videos v ON v.id=m.video_id ORDER BY m.id DESC", this::mention);
    }
    private Mention mention(ResultSet rs, int n) throws SQLException {
        return new Mention(rs.getLong("id"), rs.getString("video_id"), rs.getString("video_title"), rs.getString("extracted_name"), rs.getString("extracted_address"), rs.getInt("source_line"), rs.getString("evidence"), rs.getString("status"), rs.getObject("chef_id", Long.class), rs.getObject("restaurant_id", Long.class), rs.getString("review_note"));
    }
    private Mention lockedPending(long id) {
        var rows = db.query("SELECT m.*,v.title AS video_title FROM mentions m JOIN videos v ON v.id=m.video_id WHERE m.id=? FOR UPDATE OF m", this::mention, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "후보를 찾을 수 없습니다.");
        if (!rows.get(0).status().equals("PENDING")) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 검토된 후보입니다.");
        return rows.get(0);
    }
    @Transactional
    public PlaceSearch search(long mentionId, String query) {
        Mention mention = lockedPending(mentionId);
        PlaceSearch search = apis.places(query == null || query.isBlank() ? mention.extractedName() : query);
        db.update("DELETE FROM place_candidates WHERE mention_id=?", mentionId);
        for (Place p : search.candidates()) db.update("INSERT INTO place_candidates(mention_id,kakao_place_id,name,road_address,address,longitude,latitude,place_url) VALUES (?,?,?,?,?,?,?,?)",
            mentionId, p.id(), p.name(), p.roadAddress(), p.address(), p.longitude(), p.latitude(), p.placeUrl());
        return search;
    }
    @Transactional
    public void approve(long mentionId, long chefId, String placeId, String note, String reviewer) {
        lockedPending(mentionId);
        if (db.queryForObject("SELECT count(*) FROM chefs WHERE id=?", Integer.class, chefId) == 0) throw new IllegalArgumentException("셰프를 선택하세요.");
        var places = db.query("SELECT * FROM place_candidates WHERE mention_id=? AND kakao_place_id=?",
            (rs, n) -> new Place(rs.getString("kakao_place_id"), rs.getString("name"), rs.getString("road_address"), rs.getString("address"), rs.getDouble("longitude"), rs.getDouble("latitude"), rs.getString("place_url")), mentionId, placeId);
        if (places.isEmpty()) throw new IllegalArgumentException("이 후보에서 검색한 Kakao 장소를 선택하세요.");
        Place p = places.get(0);
        Long restaurantId = db.queryForObject("INSERT INTO restaurants(kakao_place_id,name,road_address,address,longitude,latitude,place_url) VALUES (?,?,?,?,?,?,?) ON CONFLICT(kakao_place_id) DO UPDATE SET name=EXCLUDED.name RETURNING id", Long.class,
            p.id(), p.name(), p.roadAddress(), p.address(), p.longitude(), p.latitude(), p.placeUrl());
        db.update("UPDATE mentions SET status='APPROVED',chef_id=?,restaurant_id=?,review_note=?,reviewed_by=?,reviewed_at=now() WHERE id=?",
            chefId, restaurantId, note.strip(), reviewer, mentionId);
    }
    @Transactional
    public void reject(long id, String note, String reviewer) {
        lockedPending(id);
        db.update("UPDATE mentions SET status='REJECTED',review_note=?,reviewed_by=?,reviewed_at=now() WHERE id=?", note.strip(), reviewer, id);
    }
    List<Restaurant> restaurants() {
        return db.query("SELECT r.* FROM restaurants r WHERE EXISTS (SELECT 1 FROM mentions m WHERE m.restaurant_id=r.id AND m.status='APPROVED') ORDER BY r.id",
            (rs, n) -> new Restaurant(rs.getLong("id"), rs.getString("kakao_place_id"), rs.getString("name"), rs.getString("road_address"), rs.getString("address"), rs.getDouble("longitude"), rs.getDouble("latitude"), rs.getString("place_url"), sources(rs.getLong("id"))));
    }
    private List<Source> sources(long restaurantId) {
        return db.query("SELECT m.id,c.name,m.video_id,v.title FROM mentions m JOIN chefs c ON c.id=m.chef_id JOIN videos v ON v.id=m.video_id WHERE m.restaurant_id=? AND m.status='APPROVED' ORDER BY m.id",
            (rs, n) -> new Source(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), restaurantId);
    }
}
