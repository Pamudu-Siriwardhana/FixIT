package com.serviceportal.service;

import com.serviceportal.model.Profession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Stub matcher: counts whole-word hits of each profession's name and admin-maintained keywords. */
@Service
@ConditionalOnProperty(name = "app.matching.provider", havingValue = "keyword", matchIfMissing = true)
public class KeywordMatchingService implements MatchingService {

    private static final int NAME_WEIGHT = 3;

    @Override
    public List<ProfessionMatch> match(String problemDescription, List<Profession> professions) {
        String text = problemDescription == null ? "" : problemDescription.toLowerCase(Locale.ROOT);
        List<ProfessionMatch> out = new ArrayList<>();
        for (Profession p : professions) {
            int score = 0;
            if (hits(text, p.getName().toLowerCase(Locale.ROOT))) score += NAME_WEIGHT;
            if (p.getKeywords() != null) {
                for (String k : p.getKeywords().split(",")) {
                    String kw = k.trim().toLowerCase(Locale.ROOT);
                    if (!kw.isEmpty() && hits(text, kw)) score++;
                }
            }
            if (score > 0) out.add(new ProfessionMatch(p, score));
        }
        out.sort(Comparator.comparingInt(ProfessionMatch::score).reversed()
                .thenComparing(m -> m.profession().getName()));
        return out;
    }

    /** Whole-word match that tolerates a trailing plural, and works for non-Latin scripts. */
    static boolean hits(String text, String phrase) {
        Pattern p = Pattern.compile("(?<![\\p{L}\\p{M}])" + Pattern.quote(phrase) + "(s|es)?(?![\\p{L}\\p{M}])");
        return p.matcher(text).find();
    }
}
