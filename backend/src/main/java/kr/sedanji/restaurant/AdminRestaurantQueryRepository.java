package kr.sedanji.restaurant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import static kr.sedanji.restaurant.AdminRestaurantResult.*;

@Repository
public class AdminRestaurantQueryRepository {
    private final NamedParameterJdbcTemplate db;
    public AdminRestaurantQueryRepository(NamedParameterJdbcTemplate db) { this.db = db; }
    private static final String PUBLIC = "m.verification_status='APPROVED' AND m.publication_status='PUBLISHED'";
    record Predicate(String sql, MapSqlParameterSource params) {}
    private Predicate predicate(AdminRestaurantQuery q) {
        StringBuilder sql = new StringBuilder(" WHERE true");
        var params = new MapSqlParameterSource();
        if (!q.query().isEmpty()) {
            String literal = q.query().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            params.addValue("query", "%" + literal + "%");
            sql.append("""
                 AND (lower(r.name) LIKE lower(:query) ESCAPE '!'
                   OR lower(r.road_address) LIKE lower(:query) ESCAPE '!'
                   OR lower(r.address) LIKE lower(:query) ESCAPE '!'
                   OR EXISTS (SELECT 1 FROM mentions m JOIN chefs c ON c.id=m.chef_id
                     WHERE m.restaurant_id=r.id AND lower(c.name) LIKE lower(:query) ESCAPE '!'))
                """);
        }
        if (!q.publication().equals("ALL")) {
            sql.append(q.publication().equals("PUBLISHED") ? " AND EXISTS (" : " AND NOT EXISTS (");
            sql.append("SELECT 1 FROM mentions m WHERE m.restaurant_id=r.id AND ").append(PUBLIC).append(")");
        }
        if (q.needsSourceReview()) sql.append("""
             AND EXISTS (SELECT 1 FROM mentions m JOIN videos v ON v.id=m.video_id
               WHERE m.restaurant_id=r.id AND v.source_check_status='REVIEW_REQUIRED')
            """);
        return new Predicate(sql.toString(), params);
    }
    long countMatching(AdminRestaurantQuery q) {
        var p = predicate(q);
        return db.queryForObject("SELECT count(*) FROM restaurants r" + p.sql(), p.params(), Long.class);
    }
    List<Restaurant> findPage(AdminRestaurantQuery q) {
        var p = predicate(q);
        p.params().addValue("size", q.size()).addValue("offset", (long) q.page() * q.size());
        return db.query("SELECT r.* FROM restaurants r" + p.sql()
            + " ORDER BY r.created_at DESC,r.id DESC LIMIT :size OFFSET :offset", p.params(), (rs, row) -> restaurant(rs));
    }
    Optional<Restaurant> detail(long id) {
        return db.query("SELECT * FROM restaurants WHERE id=:id", Map.of("id", id),
            (rs, row) -> restaurant(rs)).stream().findFirst();
    }
    private Restaurant restaurant(ResultSet rs) throws SQLException {
        return new Restaurant(rs.getLong("id"), rs.getString("kakao_place_id"), rs.getString("name"),
            rs.getString("road_address"), rs.getString("address"), rs.getDouble("longitude"),
            rs.getDouble("latitude"), rs.getString("place_url"), rs.getLong("version"), time(rs, "created_at"));
    }
    record Aggregate(Summary summary, long chefCount) {}
    Map<Long, Aggregate> summaries(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new HashMap<Long, Aggregate>();
        db.query("""
            SELECT m.restaurant_id,count(*) AS relations,
              count(*) FILTER (WHERE m.verification_status='APPROVED' AND m.publication_status='PUBLISHED') AS publics,
              count(DISTINCT m.chef_id) AS chefs,count(DISTINCT v.id) AS sources,
              count(DISTINCT v.id) FILTER (WHERE v.source_check_status='UNCHECKED') AS unchecked,
              count(DISTINCT v.id) FILTER (WHERE v.source_check_status='REVIEW_REQUIRED') AS review
            FROM mentions m JOIN videos v ON v.id=m.video_id
            WHERE m.restaurant_id IN (:ids) GROUP BY m.restaurant_id
            """, Map.of("ids", ids), rs -> {
                long sources = rs.getLong("sources"), unchecked = rs.getLong("unchecked"), review = rs.getLong("review");
                Boolean needsReview = review > 0 ? Boolean.TRUE : unchecked > 0 || sources == 0 ? null : Boolean.FALSE;
                result.put(rs.getLong("restaurant_id"), new Aggregate(new Summary(rs.getLong("relations"),
                    rs.getLong("publics"), rs.getLong("publics") > 0, needsReview, sources, unchecked, review), rs.getLong("chefs")));
            });
        return result;
    }
    Map<Long, List<ChefPreview>> chefPreview(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new HashMap<Long, List<ChefPreview>>();
        db.query("""
            SELECT * FROM (
              SELECT m.restaurant_id,c.id,c.name,count(*) AS relations,
                count(*) FILTER (WHERE m.verification_status='APPROVED' AND m.publication_status='PUBLISHED') AS publics,
                row_number() OVER (PARTITION BY m.restaurant_id ORDER BY c.id) AS position
              FROM mentions m JOIN chefs c ON c.id=m.chef_id WHERE m.restaurant_id IN (:ids)
              GROUP BY m.restaurant_id,c.id,c.name
            ) ranked WHERE position<=3 ORDER BY restaurant_id,id
            """, Map.of("ids", ids), rs -> {
                result.computeIfAbsent(rs.getLong("restaurant_id"), key -> new ArrayList<>())
                    .add(new ChefPreview(rs.getLong("id"), rs.getString("name"), rs.getLong("relations"), rs.getLong("publics")));
            });
        return result;
    }
    List<Mention> mentionPage(long id, AdminRestaurantQuery q) {
        return db.query("""
            SELECT m.*,c.name AS chef_name,v.title,v.channel_title,v.published_at,
              v.source_check_status,v.source_checked_at,v.source_check_reason
            FROM mentions m JOIN chefs c ON c.id=m.chef_id JOIN videos v ON v.id=m.video_id
            WHERE m.restaurant_id=:id ORDER BY m.id DESC LIMIT :size OFFSET :offset
            """, Map.of("id", id, "size", q.size(), "offset", (long) q.page() * q.size()), (rs, row) ->
                new Mention(rs.getLong("id"), rs.getLong("version"), new Chef(rs.getLong("chef_id"), rs.getString("chef_name")),
                    new Video(rs.getString("video_id"), rs.getString("title"), rs.getString("channel_title"),
                        time(rs, "published_at"), rs.getString("source_check_status"), time(rs, "source_checked_at"), rs.getString("source_check_reason")),
                    rs.getString("mention_type"), rs.getObject("timestamp_seconds", Integer.class),
                    rs.getString("verification_status"), rs.getString("publication_status"), rs.getString("review_note"), time(rs, "reviewed_at")));
    }
    private static OffsetDateTime time(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }
}
