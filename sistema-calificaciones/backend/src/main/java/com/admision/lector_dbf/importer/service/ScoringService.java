package com.admision.lector_dbf.importer.service;

import com.admision.lector_dbf.evaluation.model.EvaluationModels;
import com.admision.lector_dbf.evaluation.model.ScoringModels.Section;

import com.admision.lector_dbf.importer.model.ImportModels.*;
import com.admision.lector_dbf.shared.ApiException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

@Service
public class ScoringService {
    public Score score(List<String> responses, List<String> key, EvaluationModels.Rule rule) {
        return score(responses, key, rule, List.of());
    }

    public Score score(List<String> responses, List<String> key, EvaluationModels.Rule rule, List<Section> sections) {
        if (responses.size() != key.size()) throw ApiException.badRequest("La hoja y su clave tienen distinta cantidad de respuestas");
        if (!sections.isEmpty()) {
            List<SectionScore> details = new ArrayList<>();
            int correct = 0, incorrect = 0, blank = 0;
            BigDecimal points = BigDecimal.ZERO;
            for (Section section : sections) {
                SectionScore part = scoreRange(responses, key, section.fromQuestion() - 1, section.toQuestion(),
                        section.code(), section.name(), section.correct(), section.incorrect(), section.blank());
                details.add(part);
                correct += part.correct(); incorrect += part.incorrect(); blank += part.blank(); points = points.add(part.points());
            }
            return new Score(correct, incorrect, blank, points, details);
        }
        SectionScore total = scoreRange(responses, key, 0, key.size(), null, null,
                rule.correct(), rule.incorrect(), rule.blank());
        return new Score(total.correct(), total.incorrect(), total.blank(), total.points());
    }

    private SectionScore scoreRange(List<String> responses, List<String> key, int from, int to,
                                    String code, String name, BigDecimal correctPoints,
                                    BigDecimal incorrectPoints, BigDecimal blankPoints) {
        int correct = 0;
        int incorrect = 0;
        int blank = 0;
        for (int index = from; index < to; index++) {
            String answer = responses.get(index);
            if (key.get(index) == null || key.get(index).isBlank()) correct++;
            else if (answer == null || answer.isBlank()) blank++;
            else if (answer.equalsIgnoreCase(key.get(index))) correct++;
            else incorrect++;
        }
        BigDecimal points = correctPoints.multiply(BigDecimal.valueOf(correct))
                .add(incorrectPoints.multiply(BigDecimal.valueOf(incorrect)))
                .add(blankPoints.multiply(BigDecimal.valueOf(blank)));
        return new SectionScore(code, name, from + 1, to, correct, incorrect, blank,
                correctPoints, incorrectPoints, blankPoints, points);
    }
}
