package org.broad.igv.hic.track;

import org.apache.commons.math.stat.StatUtils;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.hic.HiC;
import org.broad.igv.hic.HiCZoom;
import org.broad.igv.hic.NormalizationType;
import org.broad.igv.hic.data.NormalizationVector;
import org.broad.igv.renderer.DataRange;
import org.broad.igv.track.WindowFunction;

/**
 * @author jrobinso
 *         Date: 8/1/13
 *         Time: 7:53 PM
 */
public class HiCCoverageDataSource extends HiCAbstractDataSource {

    NormalizationType normalizationType;

    public HiCCoverageDataSource(HiC hic, NormalizationType no) {
        super(hic, no.getLabel());
        this.normalizationType = no;
    }


    public void initDataRange() {
        if (hic.getZd() != null) {
            int chrIdx = hic.getZd().getChr1Idx();
            HiCZoom zoom = hic.getZd().getZoom();
            NormalizationVector nv = hic.getDataset().getNormalizationVector(chrIdx, zoom, normalizationType);
            if (nv == null) {
                setDataRange(new DataRange(0, 1));
            } else {
                double max = StatUtils.percentile(nv.getData(), 95);
                setDataRange(new DataRange(0, (float) max));
            }

        }
    }

    @Override
    public HiCDataPoint[] getData(Chromosome chr, int startBin, int endBin, HiCGridAxis gridAxis, double scaleFactor, WindowFunction windowFunction) {

        HiCZoom zoom = hic.getZd().getZoom();

        NormalizationVector nv = hic.getDataset().getNormalizationVector(chr.getIndex(), zoom, normalizationType);
        if (nv == null) return null;

        double[] data = nv.getData();


        CoverageDataPoint[] dataPoints = new CoverageDataPoint[endBin - startBin + 1];

        for (int b = startBin; b <= endBin; b++) {
            int gStart = gridAxis.getGenomicStart(b);
            int gEnd = gridAxis.getGenomicEnd(b);
            int idx = b - startBin;
            double value = b < data.length ? data[b] : 0;
            dataPoints[idx] = new CoverageDataPoint(b, gStart, gEnd, value);
        }

        return dataPoints;
    }

    public static class CoverageDataPoint implements HiCDataPoint {

        int binNumber;
        int genomicStart;
        int genomicEnd;
        double value;


        public CoverageDataPoint(int binNumber, int genomicStart, int genomicEnd, double value) {
            this.binNumber = binNumber;
            this.genomicEnd = genomicEnd;
            this.genomicStart = genomicStart;
            this.value = value;
        }

        @Override
        public double getBinNumber() {
            return binNumber;
        }

        @Override
        public double getWithInBins() {
            return 1;
        }

        @Override
        public int getGenomicStart() {
            return genomicStart;
        }

        @Override
        public double getValue(WindowFunction windowFunction) {
            return value;
        }


        @Override
        public int getGenomicEnd() {
            return genomicEnd;
        }

    }
}
