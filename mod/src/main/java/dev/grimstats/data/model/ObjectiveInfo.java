package dev.grimstats.data.model;

import java.util.List;

/** A scoreboard objective and its ranked scores. */
public record ObjectiveInfo(
        String name,
        String displayName,
        String criterion,
        String renderType,
        List<Entry> entries) {

    /** A score for a holder (player name or other score holder). */
    public record Entry(String holder, int value) {
    }
}
