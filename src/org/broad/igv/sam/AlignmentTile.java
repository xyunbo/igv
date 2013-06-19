package org.broad.igv.sam;

import org.broad.igv.feature.SpliceJunctionFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Caches alignments, coverage, splice junctions, and downsampled intervals
 */

public class AlignmentTile {

    private boolean loaded = false;
    private int end;
    private int start;
    private AlignmentCounts counts;
    private List<Alignment> alignments;
    private List<DownsampledInterval> downsampledIntervals;
    private SpliceJunctionHelper spliceJunctionHelper;

    private boolean downsample;
    private int samplingWindowSize;
    private int samplingDepth;
    private SamplingBucket currentSamplingBucket;

    private static final Random RAND = new Random(System.currentTimeMillis());


    AlignmentTile(int start, int end,
                  SpliceJunctionHelper spliceJunctionHelper,
                  AlignmentDataManager.DownsampleOptions downsampleOptions,
                  AlignmentTrack.BisulfiteContext bisulfiteContext) {
        this.start = start;
        this.end = end;
        alignments = new ArrayList(16000);
        downsampledIntervals = new ArrayList();

        // Use a sparse array for large regions  (> 10 mb)
        if ((end - start) > 10000000) {
            this.counts = new SparseAlignmentCounts(start, end, bisulfiteContext);
        } else {
            this.counts = new DenseAlignmentCounts(start, end, bisulfiteContext);
        }

        // Set the max depth, and the max depth of the sampling bucket.
        if (downsampleOptions == null) {
            // Use default settings (from preferences)
            downsampleOptions = new AlignmentDataManager.DownsampleOptions();
        }
        this.downsample = downsampleOptions.isDownsample();
        this.samplingWindowSize = downsampleOptions.getSampleWindowSize();
        this.samplingDepth = Math.max(1, downsampleOptions.getMaxReadCount());

        this.spliceJunctionHelper = spliceJunctionHelper;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    int ignoredCount = 0;    // <= just for debugging

    /**
     * Add an alignment record to this tile.  This record is not necessarily retained after down-sampling.
     *
     * @param alignment
     */
    public void addRecord(Alignment alignment) {

        counts.incCounts(alignment);

        if (spliceJunctionHelper != null) {
            spliceJunctionHelper.addAlignment(alignment);
        }

        if (downsample) {
            final int alignmentStart = alignment.getAlignmentStart();
            if (currentSamplingBucket == null || alignmentStart >= currentSamplingBucket.end) {
                if (currentSamplingBucket != null) {
                    emptyBucket();
                }
                int end = alignmentStart + samplingWindowSize;
                currentSamplingBucket = new SamplingBucket(alignmentStart, end);
            }
            currentSamplingBucket.add(alignment);
        } else {
            alignments.add(alignment);
        }

        alignment.finish();
    }

    private void emptyBucket() {
        if (currentSamplingBucket == null) {
            return;
        }
        //List<Alignment> sampledRecords = sampleCurrentBucket();
        for (Alignment alignment : currentSamplingBucket.getAlignments()) {
            alignments.add(alignment);
        }

        if (currentSamplingBucket.isSampled()) {
            DownsampledInterval interval = new DownsampledInterval(currentSamplingBucket.start,
                    currentSamplingBucket.end, currentSamplingBucket.downsampledCount);
            downsampledIntervals.add(interval);
        }

        currentSamplingBucket = null;

    }

    public List<Alignment> getAlignments() {
        return alignments;
    }

    public List<DownsampledInterval> getDownsampledIntervals() {
        return downsampledIntervals;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;

        if (loaded) {
            // Empty any remaining alignments in the current bucket
            emptyBucket();
            currentSamplingBucket = null;
            finalizeSpliceJunctions();
            counts.finish();
        }
    }

    public AlignmentCounts getCounts() {
        return counts;
    }


    private void finalizeSpliceJunctions() {
        if (spliceJunctionHelper != null) {
            spliceJunctionHelper.finish();
        }
    }

    public List<SpliceJunctionFeature> getSpliceJunctionFeatures() {
        if(spliceJunctionHelper == null) return null;
        return spliceJunctionHelper.getFilteredJunctions();
    }

    public SpliceJunctionHelper getSpliceJunctionHelper() {
        return spliceJunctionHelper;
    }


    private class SamplingBucket {
        int start;
        int end;
        int downsampledCount = 0;
        List<Alignment> alignments;


        private SamplingBucket(int start, int end) {
            this.start = start;
            this.end = end;
            alignments = new ArrayList(samplingDepth);
        }


        public void add(Alignment alignment) {
            // If the current bucket is < max depth we keep it.  Otherwise,  keep with probability == samplingProb
            if (alignments.size() < samplingDepth) {
                alignments.add(alignment);
            } else {
                double samplingProb = ((double) samplingDepth) / (samplingDepth + downsampledCount + 1);
                if (RAND.nextDouble() < samplingProb) {
                    int idx = (int) (RAND.nextDouble() * (alignments.size() - 1));
                    // Replace random record with this one
                    alignments.set(idx, alignment);
                }
                downsampledCount++;

            }

        }

        public List<Alignment> getAlignments() {
            return alignments;
        }

        public boolean isSampled() {
            return downsampledCount > 0;
        }

        public int getDownsampledCount() {
            return downsampledCount;
        }
    }
}
