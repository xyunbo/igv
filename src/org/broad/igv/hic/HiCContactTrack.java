package org.broad.igv.hic;

import org.broad.igv.data.BasicScore;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.feature.LocusScore;
import org.broad.igv.hic.data.*;
import org.broad.igv.hic.track.HiCGridAxis;
import org.broad.igv.track.*;
import org.broad.igv.ui.IGV;
import org.broad.igv.util.ResourceLocator;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jrobinso
 *         Date: 10/30/13
 *         Time: 11:27 AM
 */
public class HiCContactTrack extends DataTrack {

    String hicFile;
    private Dataset dataset;
    private int anchor;
    private int achorPixelPosition;

    public HiCContactTrack(ResourceLocator locator, String id, String name) throws IOException {
        super(locator, id, name);

        this.hicFile = locator.getPath();
        DatasetReaderV2 reader = new DatasetReaderV2(hicFile);
        dataset = reader.read();

        anchor = -1;

    }

    @Override
    public List<LocusScore> getSummaryScores(String chr, int startLocation, int endLocation, int zoom) {

        if (anchor < startLocation || anchor > endLocation) return null;

        // Limit span
        final int limit = 4000000;
        if(endLocation - startLocation > limit) {
            int center = startLocation + (endLocation - startLocation) / 2;
            startLocation = center - limit/2;
            endLocation = center + limit/2;
        }
        //|| (endLocation - startLocation > 20000000)) return null;


        return getContacts(chr, startLocation, endLocation);

    }


    private List<LocusScore> getContacts(String chr, int startBP, int endBP) {

        chr = chr.replace("chr", "");

        Chromosome c1 = null;
        Chromosome c2 = null;
        List<Chromosome> allChromosomes = dataset.getChromosomes();
        for (Chromosome c : allChromosomes) {
            String shortChrName = c.getName().replace("chr", "");
            if (shortChrName.equals(chr)) c1 = c;
            if (shortChrName.equals(chr)) c2 = c;
        }

        if (c1 == null) {
            return null;
        }

        Matrix m = dataset.getMatrix(c1, c2);

        HiCZoom zoom = new HiCZoom(HiC.Unit.FRAG, 1);
        MatrixZoomData zd = m.getZoomData(zoom);

        final HiCGridAxis yGridAxis = zd.getYGridAxis();
        final HiCGridAxis xGridAxis = zd.getXGridAxis();

        int focalBin = yGridAxis.getBinNumberForGenomicPosition(anchor);
        int startBin = xGridAxis.getBinNumberForGenomicPosition(startBP);
        int endBin = xGridAxis.getBinNumberForGenomicPosition(endBP);

        MatrixZoomData.Slice slice = zd.getSlice(startBin, endBin, focalBin, NormalizationType.NONE);

        //System.out.println("variableStep chrom=chr" + chr + " span=5000");
        //Fragment uses bedgraph

        boolean oe = false;
        ExpectedValueFunction df = dataset.getExpectedValues(zoom, NormalizationType.NONE);

        List<LocusScore> scores = new ArrayList(slice.getSize());
        for (int i = 0; i < slice.getSize(); i++) {
            int start = xGridAxis.getGenomicStart(slice.getBin(i));
            int end = xGridAxis.getGenomicEnd(slice.getBin(i));
            float value = slice.getCounts(i);

            if(oe) {
                int binX = slice.getBin(i);
                int binY = focalBin;
                int dist = Math.abs(binX - binY);
                double expected = df.getExpectedValue(c1.getIndex(), dist);
                value /= expected;
            }

            scores.add(new BasicScore(start, end, value));
        }
        return scores;
    }


    @Override
    public void render(RenderContext context, Rectangle rect) {
        super.render(context, rect);

        achorPixelPosition = (int) ((anchor - context.getOrigin()) / context.getScale());

        context.getGraphic2DForColor(Color.red).drawLine(achorPixelPosition, rect.y, achorPixelPosition, rect.y + rect.height);
    }

    @Override
    public boolean handleDataClick(TrackClickEvent te) {

        anchor = (int) te.getChromosomePosition();
        clearCaches();
        IGV.getInstance().getMainPanel().repaint();

        return true;
    }
}
