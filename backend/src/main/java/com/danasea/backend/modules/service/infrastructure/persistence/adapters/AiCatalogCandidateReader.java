package com.danasea.backend.modules.service.infrastructure.persistence.adapters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import com.danasea.backend.modules.service.application.api.AiCatalogCandidateReadApi;
import com.danasea.backend.modules.service.application.api.AiCatalogReadApi.Query;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import java.sql.Connection;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class AiCatalogCandidateReader implements AiCatalogCandidateReadApi {
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private volatile Boolean postgres;
    private static final String DISTANCE = "6371 * acos(least(1.0, greatest(-1.0, sin(radians(:lat)) * sin(radians(cast(s.latitude as double precision))) + cos(radians(:lat)) * cos(radians(cast(s.latitude as double precision))) * cos(radians(cast(s.longitude as double precision)) - radians(:lon)))))";
    private static final String TEXT = "lower(coalesce(s.name,'') || ' ' || coalesce(s.name_en,'') || ' ' || coalesce(s.description,'') || ' ' || coalesce(s.description_en,'') || ' ' || coalesce(c.name,'') || ' ' || coalesce(c.slug,''))";
    private static final String ACCENTS = "áàảãạăắằẳẵặâấầẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđ";
    private static final String ASCII = "aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd";

    @Override
    public List<UUID> candidateIds(Query criteria, int offset, int batchSize) {
        if (postgres == null) postgres = Boolean.TRUE.equals(jdbc.execute((Connection connection) -> connection.getMetaData().getDatabaseProductName().contains("PostgreSQL")));
        String text = postgres ? "translate(" + TEXT + ", '" + ACCENTS + "', '" + ASCII + "')" : TEXT;
        StringBuilder sql = new StringBuilder("select s.id from services s left join categories c on c.id=s.category_id where s.status='PUBLISHED'");
        Map<String, Object> params = new LinkedHashMap<>();
        if (criteria.categoryId() != null) { sql.append(" and s.category_id=:category"); params.put("category", criteria.categoryId()); }
        List<String> included = criteria.includedActivities() == null ? List.of() : criteria.includedActivities();
        if (included.isEmpty() && criteria.keyword() != null && !criteria.keyword().isBlank()) included = List.of(criteria.keyword());
        if (!included.isEmpty()) {
            sql.append(" and (");
            List<String> predicates = new ArrayList<>();
            for (int index = 0; index < included.size(); index++) {
                String key = "include" + index;
                String predicate = text + " like :" + key;
                params.put(key, "%" + escape(normalize(included.get(index))) + "%");
                if (postgres) {
                    String ftsKey = "fts" + index;
                    predicate = "(" + predicate + " or s.search_vector @@ plainto_tsquery('simple', :" + ftsKey + "))";
                    params.put(ftsKey, included.get(index));
                }
                predicates.add(predicate);
            }
            sql.append(String.join(" or ", predicates)).append(')');
        }
        String exclusionText = "lower(coalesce(s.name,'') || ' ' || coalesce(s.name_en,'') || ' ' || coalesce(c.name,'') || ' ' || coalesce(c.slug,''))";
        if (postgres) exclusionText = "translate(" + exclusionText + ", '" + ACCENTS + "', '" + ASCII + "')";
        if (criteria.excludedActivities() != null) {
            for (int index = 0; index < criteria.excludedActivities().size(); index++) {
                String key = "exclude" + index;
                sql.append(" and ").append(exclusionText).append(" not like :").append(key);
                params.put(key, "%" + escape(normalize(criteria.excludedActivities().get(index))) + "%");
            }
        }
        if (criteria.latitude() != null) {
            params.put("lat", criteria.latitude()); params.put("lon", criteria.longitude());
            if ("NEARBY".equals(criteria.mode()) || criteria.radiusKm() != null) sql.append(" and s.latitude is not null and s.longitude is not null");
            if (criteria.radiusKm() != null) { sql.append(" and ").append(DISTANCE).append(" <= :radius"); params.put("radius", criteria.radiusKm()); }
        }
        sql.append(" and exists (select 1 from service_options o where o.service_id=s.id and o.status='ACTIVE' and o.price>=0 and (o.pricing_unit='PER_PERSON' or (o.pricing_unit='PER_PACKAGE' and o.max_pax_per_package>0))");
        params.put("party", criteria.partySize());
        if (criteria.totalBudget() != null) {
            sql.append(" and o.price * (case when o.pricing_unit='PER_PACKAGE' then ceil(cast(:party as decimal)/nullif(o.max_pax_per_package,0)) else :party end) <= :budget");
            params.put("budget", criteria.totalBudget());
        }
        sql.append(" and exists(select 1 from service_slots sl where sl.service_id=s.id and sl.status='OPEN' and sl.date between :fromDate and :toDate and sl.start_time>=:dayStart");
        params.put("fromDate", criteria.from()); params.put("toDate", criteria.to());
        params.put("dayStart", criteria.dayStart() == null ? LocalTime.MIN : criteria.dayStart());
        params.put("dayEnd", criteria.dayEnd() == null ? LocalTime.MAX : criteria.dayEnd());
        sql.append(" and (sl.end_time is null or sl.end_time<=:dayEnd)" );
        sql.append("))");
        String preferenceOrder = "";
        if (criteria.rankingInterests() != null && !criteria.rankingInterests().isEmpty() && !"NEARBY".equals(criteria.mode())) {
            List<String> contributions = new ArrayList<>();
            for (int index = 0; index < criteria.rankingInterests().size(); index++) {
                String key = "interest" + index;
                contributions.add("case when " + text + " like :" + key + " then 1 else 0 end");
                params.put(key, "%" + escape(normalize(criteria.rankingInterests().get(index))) + "%");
            }
            preferenceOrder = "(" + String.join(" + ", contributions) + ") desc, ";
        }
        if (criteria.latitude() != null) sql.append(" order by ").append(preferenceOrder).append(DISTANCE).append(" asc nulls last, coalesce(s.avg_rating,0) desc, s.id");
        else sql.append(" order by ").append(preferenceOrder).append("coalesce(s.avg_rating,0) * least(coalesce(s.rating_count,0),10)/10.0 desc, s.id");
        var query = entityManager.createNativeQuery(sql.toString());
        // party is used only by the option-quote budget predicate.
        if (criteria.totalBudget() == null) params.remove("party");
        params.forEach(query::setParameter);
        query.setHint("jakarta.persistence.query.timeout", 5000);
        query.setFirstResult(offset); query.setMaxResults(Math.min(15, Math.max(1, batchSize)));
        return query.getResultList().stream().map(value -> value instanceof UUID id ? id : UUID.fromString(value.toString())).toList();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd').toLowerCase(Locale.ROOT);
    }

    private String escape(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
}
