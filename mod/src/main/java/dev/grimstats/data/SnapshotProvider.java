package dev.grimstats.data;

import dev.grimstats.data.model.StatsSnapshot;

/** Read-side contract the HTTP layer depends on, decoupling it from how snapshots are produced. */
public interface SnapshotProvider {

    /** The most recently published snapshot. Never null (returns {@link StatsSnapshot#empty()}). */
    StatsSnapshot current();

    /** Requests an out-of-band snapshot rebuild as soon as possible. */
    void requestRefresh();
}
