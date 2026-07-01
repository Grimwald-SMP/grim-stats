package dev.grimstats.data.model;

import java.util.List;

/**
 * Describes a registered stat type and the keys it contains, so the dashboard can build filters and
 * labels for every stat, including those added by mods or datapacks.
 */
public record StatTypeInfo(
        String id,
        String translationKey,
        boolean modded,
        List<String> keys) {
}
