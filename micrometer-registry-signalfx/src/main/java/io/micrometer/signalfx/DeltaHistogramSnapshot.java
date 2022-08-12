/*
 * Copyright Splunk Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.signalfx;

import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;

// WARNING: This class is not available upstream yet, but will be soon, and may suffer modifications.
final class DeltaHistogramSnapshot {
    private HistogramSnapshot lastSnapshot;

    DeltaHistogramSnapshot() {
        lastSnapshot = HistogramSnapshot.empty(0, 0, 0);
    }

    // TODO: Determine if we need to synchronize, in case multiple calls in parallel.
    HistogramSnapshot calculateSnapshot(HistogramSnapshot currentSnapshot) {
        HistogramSnapshot deltaSnapshot = new HistogramSnapshot(
                currentSnapshot.count(), // Count is a step tupple, keep count from current.
                currentSnapshot.total(),// Sum is a step tupple, keep count from current.
                currentSnapshot.max(),  // Max cannot be calculated as delta, keep the current.
                currentSnapshot.percentileValues(),  // Keep the percentile from current
                deltaHistogramCounts(currentSnapshot),
                currentSnapshot::outputSummary);
        lastSnapshot = currentSnapshot;
        return deltaSnapshot;
    }

    private CountAtBucket[] deltaHistogramCounts(HistogramSnapshot currentSnapshot) {
        CountAtBucket[] currentHistogramCounts = currentSnapshot.histogramCounts();
        CountAtBucket[] lastHistogramCounts = lastSnapshot.histogramCounts();
        if (lastHistogramCounts == null || lastHistogramCounts.length == 0) {
            return currentHistogramCounts;
        }

        CountAtBucket[] retHistogramCounts = new CountAtBucket[currentHistogramCounts.length];
        for (int i = 0; i < currentHistogramCounts.length; i++) {
            retHistogramCounts[i] = new CountAtBucket(
                    currentHistogramCounts[i].bucket(),
                    currentHistogramCounts[i].count() - lastHistogramCounts[i].count());
        }
        return retHistogramCounts;
    }
}
