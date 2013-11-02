package org.broad.igv.hic.track;

/**
 * @author jrobinso
 *         Date: 9/14/12
 *         Time: 8:54 AM
 */
public interface HiCGridAxis {

    enum Direction {x, y}

    int getGenomicStart(double binNumber);

    int getGenomicEnd(double binNumber);

    int getGenomicMid(double binNumber);

    int getIGVZoom();

    int getBinCount();

    int getBinNumberForGenomicPosition(int genomePosition);

    int getBinNumberForFragment(int fragmentX);
}
