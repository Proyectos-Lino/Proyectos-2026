package com.admision.lector_dbf.report.model;

import com.admision.lector_dbf.evaluation.model.EvaluationModels;
import com.admision.lector_dbf.exam.model.ExamModels;
import java.util.List;
import com.admision.lector_dbf.account.model.OrganizationProfile;
import com.admision.lector_dbf.evaluation.model.ScoringModels.RankingPolicy;

public record ResultsReport(EvaluationModels.Evaluation evaluation, List<ExamModels.Exam> exams,
                            OrganizationProfile organization,byte[] logo,RankingPolicy policy,List<EvaluationModels.Group> groups,
                            boolean officialScoring) {}
