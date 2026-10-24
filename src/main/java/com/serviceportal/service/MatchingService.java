package com.serviceportal.service;

import com.serviceportal.model.Profession;

import java.util.List;

/**
 * Decides which profession best fits a free-text problem description.
 * The built-in implementation is a keyword stub; to use an AI API, add another implementation of this
 * interface (and set app.matching.provider to its name) without touching the callers.
 */
public interface MatchingService {

    record ProfessionMatch(Profession profession, int score) {}

    /** Matches ordered best first; professions with no evidence are left out. */
    List<ProfessionMatch> match(String problemDescription, List<Profession> professions);
}
