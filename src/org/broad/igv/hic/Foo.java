package org.broad.igv.hic;

import org.broad.igv.feature.Chromosome;
import org.broad.igv.hic.HiC;
import org.broad.igv.hic.HiCZoom;
import org.broad.igv.hic.NormalizationType;
import org.broad.igv.hic.data.Dataset;
import org.broad.igv.hic.data.DatasetReaderV2;
import org.broad.igv.hic.data.Matrix;
import org.broad.igv.hic.data.MatrixZoomData;
import org.broad.igv.hic.track.HiCGridAxis;
import org.junit.Test;

import java.awt.*;
import java.util.List;

/**
 * @author jrobinso
 *         Date: 10/29/13
 *         Time: 8:41 AM
 */
public class Foo {


    public static void main(String [] args) throws Exception {
           testGetSlice();
    }


    public static void testGetSlice() throws Exception {

        String hicfile = "https://iwww.broadinstitute.org/igvdata/hic/files/HiSeq/K562/inter.hic";
        //String hicfile = "http://iwww.broadinstitute.org/igvdata/hic/files/HiSeq/MboI_magic_biorep/inter.hic"; // GM12878
        //String hicfile = "http://iwww.broadinstitute.org/igvdata/hic/files/HiSeq/mES/inter.hic"; // Mouse
        // ZFPM2
//        String chr = "8";
//        int focalBP = 106331171;
//        int startBP = 105600000; //focalBP - 10000000;
//        int endBP = 107000000; //focalBP +   10000000;


        String chr = "11";
        int focalBP = 5271034;  // MFNG
        //int focalBP = 110962382;  // MFNG
        //int focalBP = 37915206;  // Card10
        int startBP = focalBP - 2000000;
        int endBP = focalBP +   2000000;

        DatasetReaderV2 reader = new DatasetReaderV2(hicfile);
        Dataset dataset = reader.read();

        Chromosome c1 = null;
        Chromosome c2 = null;
        List<Chromosome> allChromosomes = dataset.getChromosomes();
        for (Chromosome c : allChromosomes) {
            if (c.getName().equals(chr)) c1 = c;
            if (c.getName().equals(chr)) c2 = c;
        }

        Matrix m = dataset.getMatrix(c1, c2);

        HiCZoom zoom = new HiCZoom(HiC.Unit.FRAG, 1);
        MatrixZoomData zd = m.getZoomData(zoom);

        final HiCGridAxis yGridAxis = zd.getYGridAxis();
        final HiCGridAxis xGridAxis = zd.getXGridAxis();

        int focalBin = yGridAxis.getBinNumberForGenomicPosition(focalBP);
        int startBin = xGridAxis.getBinNumberForGenomicPosition(startBP);
        int endBin = xGridAxis.getBinNumberForGenomicPosition(endBP);

        MatrixZoomData.Slice slice = zd.getSlice(startBin, endBin, focalBin, NormalizationType.NONE);

        //System.out.println("variableStep chrom=chr" + chr + " span=5000");
        //Fragment uses bedgraph

        for (int i = 0; i < slice.getSize(); i++) {
            int start = xGridAxis.getGenomicStart(slice.getBin(i));
            int end = xGridAxis.getGenomicEnd(slice.getBin(i));
            System.out.println(chr + "\t" + start + "\t" + end + "\t" + slice.getCounts(i));
        }

        // ExpectedValueFunction df = null;
        // if (sameChr && (displayOption == MainWindow.MatrixType.OE || displayOption == MainWindow.MatrixType.EXPECTED)) {
        //     df = hic.getDataset().getExpectedValues(zd.getZoom(), hic.getNormalizationType());
        // }


    }
}
