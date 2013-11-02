package org.broad.igv.hic.data;

import org.apache.commons.math.linear.*;
import org.apache.commons.math.stat.correlation.PearsonsCorrelation;
import org.apache.log4j.Logger;
import org.broad.igv.data.BasicScore;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.hic.HiC;
import org.broad.igv.hic.HiCZoom;
import org.broad.igv.hic.NormalizationType;
import org.broad.igv.hic.matrix.BasicMatrix;
import org.broad.igv.hic.matrix.RealMatrixWrapper;
import org.broad.igv.hic.track.HiCFixedGridAxis;
import org.broad.igv.hic.track.HiCFragmentAxis;
import org.broad.igv.hic.track.HiCGridAxis;
import org.broad.igv.util.Pair;
import org.broad.igv.util.collections.FloatArrayList;
import org.broad.igv.util.collections.IntArrayList;
import org.broad.igv.util.collections.LRUCache;
import org.broad.tribble.util.LittleEndianInputStream;
import org.broad.tribble.util.LittleEndianOutputStream;

import java.io.*;
import java.util.*;
import java.util.List;

/**
 * @author jrobinso
 * @date Aug 10, 2010
 */
public class MatrixZoomData {

    private static Logger log = Logger.getLogger(MatrixZoomData.class);

    DatasetReader reader;

    private Chromosome chr1;  // Chromosome on the X axis
    private Chromosome chr2;  // Chromosome on the Y axis
    private HiCZoom zoom;    // Unit and bin size

    HiCGridAxis xGridAxis;
    HiCGridAxis yGridAxis;

    // Observed values are ogranized into sub-matrices ("blocks")
    private int blockBinCount;   // block size in bins
    private int blockColumnCount;     // number of block columns

    HashMap<NormalizationType, BasicMatrix> pearsonsMap;
    HashSet<NormalizationType> missingPearsonFiles;
    int[] nonCentromereColumns;
    private double averageCount = -1;

    boolean useCache = true;
    // Cache the last 20 blocks loaded
    LRUCache<String, Block> blockCache = new LRUCache<String, Block>(this, 20);


//    float sumCounts;
//    float avgCounts;
//    float stdDev;
//    float percent95 = -1;
//    float percent80 = -1;


    /**
     * @param chr1
     * @param chr2
     * @return
     * @throws IOException
     */
    public MatrixZoomData(Chromosome chr1, Chromosome chr2, HiCZoom zoom, int blockBinCount, int blockColumnCount,
                          int[] chr1Sites, int[] chr2Sites, DatasetReader reader) throws IOException {

        this.reader = reader;

        this.chr1 = chr1;
        this.chr2 = chr2;
        this.zoom = zoom;
        this.blockBinCount = blockBinCount;
        this.blockColumnCount = blockColumnCount;


        int[] xSites = chr1Sites;
        int[] ySites = chr2Sites;
        if (zoom.getUnit() == HiC.Unit.BP) {
            this.xGridAxis = new HiCFixedGridAxis(blockBinCount * blockColumnCount, zoom.getBinSize(), xSites);
            this.yGridAxis = new HiCFixedGridAxis(blockBinCount * blockColumnCount, zoom.getBinSize(), ySites);
        } else {
            this.xGridAxis = new HiCFragmentAxis(zoom.getBinSize(), xSites, chr1.getLength());
            this.yGridAxis = new HiCFragmentAxis(zoom.getBinSize(), ySites, chr2.getLength());

        }

        pearsonsMap = new HashMap<NormalizationType, BasicMatrix>();
        missingPearsonFiles = new HashSet<NormalizationType>();
    }

    public Chromosome getChr1() {
        return chr1;
    }

    public Chromosome getChr2() {
        return chr2;
    }

    public HiCGridAxis getXGridAxis() {
        return xGridAxis;
    }

    public HiCGridAxis getYGridAxis() {
        return yGridAxis;
    }

    public int getBinSize() {
        return zoom.getBinSize();
    }

    public int getChr1Idx() {
        return chr1.getIndex();
    }


    public int getChr2Idx() {
        return chr2.getIndex();
    }

    public HiCZoom getZoom() {
        return zoom;
    }

    public int getBlockColumnCount() {
        return blockColumnCount;
    }

    public String getKey() {
        return chr1.getName() + "_" + chr2.getName() + "_" + zoom.getKey();
    }


    /**
     * Return the blocks of normalized, observed values overlapping the rectangular region specified.
     * The units are "bins"
     *
     * @param binY1 leftmost position in "bins"
     * @param binX2 rightmost position in "bins"
     * @param binY2 bottom position in "bins"
     * @param no
     * @return
     */
    public List<Block> getNormalizedBlocksOverlapping(int binX1, int binY1, int binX2, int binY2, final NormalizationType no) {

        int col1 = binX1 / blockBinCount;
        int row1 = binY1 / blockBinCount;

        int col2 = binX2 / blockBinCount;
        int row2 = binY2 / blockBinCount;

        int maxSize = (col2 - col1 + 1) * (row2 - row1 + 1);
        List<Integer> blockNumbers = new ArrayList<Integer>(maxSize);
        for (int r = row1; r <= row2; r++) {
            for (int c = col1; c <= col2; c++) {
                int blockNumber = r * getBlockColumnCount() + c;
                blockNumbers.add(blockNumber);
            }
        }

        return getBlocks(no, blockNumbers);
    }


    private List<Block> getBlocks(final NormalizationType no, List<Integer> blockNumbers) {
        final List<Block> blockList = new ArrayList<Block>(blockNumbers.size());
        final List<Integer> blocksToLoad = new ArrayList<Integer>();
        for (int blockNumber : blockNumbers) {

            String key = getKey() + "_" + blockNumber + "_" + no;
            Block b;
            if (useCache && blockCache.containsKey(key)) {
                b = blockCache.get(key);
                blockList.add(b);
            } else {
                blocksToLoad.add(blockNumber);
            }
        }


        List<Thread> threads = new ArrayList<Thread>();
        for (final int blockNumber : blocksToLoad) {
            Runnable loader = new Runnable() {
                @Override
                public void run() {
                    try {
                        String key = getKey() + "_" + blockNumber + "_" + no;
                        Block b = reader.readNormalizedBlock(blockNumber, MatrixZoomData.this, no);
                        if (b == null) {
                            b = new Block(blockNumber);   // An empty block
                        }
                        if (useCache) {
                            blockCache.put(key, b);
                        }
                        blockList.add(b);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };

            Thread t = new Thread(loader);
            threads.add(t);
            t.start();
        }

        // Wait for all threads to complete
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException ignore) {
            }
        }

        return blockList;
    }


    /**
     * Return the observed value at the specified location.   Supports tooltip text
     * This implementation is naive, but might get away with it for tooltip.
     *
     * @param binX
     * @param binY
     */
    public float getObservedValue(int binX, int binY, NormalizationType normalizationType) {

        // Intra stores only lower diagonal
        if (chr1 == chr2) {
            if (binX > binY) {
                int tmp = binX;
                binX = binY;
                binY = tmp;

            }
        }

        List<Block> blocks = getNormalizedBlocksOverlapping(binX, binY, binX, binY, normalizationType);
        if (blocks == null) return 0;
        for (Block b : blocks) {
            for (ContactRecord rec : b.getContactRecords()) {
                if (rec.getBinX() == binX && rec.getBinY() == binY) {
                    return rec.getCounts();
                }
            }
        }
        // No record found for this bin
        return 0;
    }

    /**
     * Return a slice of the matrix as a list of wiggle scores.  Currently this only works for intra-chr matrices.
     *
     * @param
     */
    public Slice getSlice(int startBin, int endBin, int centerBin, NormalizationType normalizationType) {

        // TODO -- intra only for now
        if (chr1.getIndex() != chr2.getIndex()) return null;

        List<Pair<Integer, Float>> binValues = new ArrayList<Pair<Integer, Float>>();

        // Left side
        List<Block> blocks = getNormalizedBlocksOverlapping(startBin, centerBin, centerBin, centerBin, normalizationType);
        if (blocks != null) {
            for (Block b : blocks) {
                for (ContactRecord rec : b.getContactRecords()) {
                    if (rec.getBinY() == centerBin && rec.getBinX() <= centerBin) {
                        binValues.add(new Pair(rec.getBinX(), rec.getCounts()));
                    }
                }
            }
        }

        // Right side
        blocks = getNormalizedBlocksOverlapping(centerBin, centerBin, centerBin, endBin, normalizationType);
        if (blocks != null) {
            for (Block b : blocks) {
                for (ContactRecord rec : b.getContactRecords()) {
                    if (rec.getBinX() == centerBin && rec.getBinY() >= centerBin) {
                        binValues.add(new Pair(rec.getBinY(), rec.getCounts()));
                    }
                }
            }
        }

        Collections.sort(binValues, new Comparator<Pair<Integer, Float>>() {
            @Override
            public int compare(Pair<Integer, Float> o1, Pair<Integer, Float> o2) {
                return o1.getFirst() - o2.getFirst();
            }
        });

        // merge
        List<Pair<Integer, Float>> mergedPairs = new ArrayList(binValues.size());
        Pair<Integer, Float> lastPair = null;
        for (Pair<Integer, Float> pair : binValues) {
            if (lastPair != null && pair.getFirst() == lastPair.getFirst()) {
                lastPair = new Pair(lastPair.getFirst(), lastPair.getSecond().floatValue() + pair.getSecond().floatValue());
            } else {
                lastPair = pair;
                mergedPairs.add(lastPair);
            }
        }

        return new Slice(mergedPairs);
    }

    public boolean isSmallEnoughForPearsonCalculation(NormalizationType option) {


        if (!missingPearsonFiles.contains(option) || blockBinCount * blockColumnCount < 1000) {
            return true;
        } else {
            return false;
        }
    }


    public double[] computeEigenvector(ExpectedValueFunction df, int which, NormalizationType no) {
        BasicMatrix pearsons = pearsonsMap.get(df.getType());
        if (pearsons == null) {
            pearsons = computePearsons(df, no);
            pearsonsMap.put(df.getType(), pearsons);
        }

        RealMatrix subMatrix = ((RealMatrixWrapper) pearsons).getMatrix().getSubMatrix(nonCentromereColumns, nonCentromereColumns);


        RealVector rv;
        rv = (new EigenDecompositionImpl(subMatrix, 0)).getEigenvector(which);

        double[] ev = rv.toArray();

        int size = pearsons.getColumnDimension();
        double[] eigenvector = new double[size];
        int num = 0;
        for (int i = 0; i < size; i++) {
            if (num < nonCentromereColumns.length && i == nonCentromereColumns[num]) {
                eigenvector[i] = ev[num];
                num++;
            } else {
                eigenvector[i] = Double.NaN;
            }
        }
        return eigenvector;

    }

    public BasicMatrix getPearsons(NormalizationType option) {

        BasicMatrix pearsons = pearsonsMap.get(option);

        if (pearsons == null && !missingPearsonFiles.contains(option)) {
            try {
                pearsons = reader.readPearsons(chr1.getName(), chr2.getName(), zoom, option);
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            if (pearsons != null) {
                pearsonsMap.put(option, pearsons);
            } else {
                missingPearsonFiles.add(option);  // To keep from trying repeatedly
            }
        }

        return pearsonsMap.get(option);
    }


    public float getPearsonValue(int binX, int binY, NormalizationType type) {
        BasicMatrix pearsons = pearsonsMap.get(type);
        if (pearsons != null) {
            return pearsons.getEntry(binX, binY);
        } else {
            return 0;
        }
    }

    public BasicMatrix computePearsons(ExpectedValueFunction df, NormalizationType no) {
        RealMatrix oe = computeOE(df, no);

        // below subtracts the empirical mean - necessary for mean-centered eigenvector
        int size = oe.getRowDimension();
        int num = 0;
        for (int i = 0; i < size; i++) {
            if (num < nonCentromereColumns.length && i == nonCentromereColumns[num]) {
                RealVector v = oe.getRowVector(i);
                double m = getVectorMean(v);
                RealVector newV = v.mapSubtract(m);
                oe.setRowVector(i, newV);
                num++;
            }
        }

        RealMatrix rm = (new PearsonsCorrelation()).computeCorrelationMatrix(oe);
        RealVector v = new ArrayRealVector(size);
        v.set(Double.NaN);

        num = 0;
        for (int i = 0; i < size; i++) {
            if (num < nonCentromereColumns.length && i != nonCentromereColumns[num]) {
                rm.setRowVector(i, v);
                rm.setColumnVector(i, v);
            } else num++;
        }
        BasicMatrix pearsons = new RealMatrixWrapper(rm);
        pearsonsMap.put(df.getType(), pearsons);
        return pearsons;
    }

    public double[] getGradientXSum(NormalizationType no) {

        int nBins1 = blockBinCount * blockColumnCount;
        // How do we do this for inter chromosomal maps??
        // int nBins2 = blockBinCount * blockRowCount;
        //int nBins = chr1.getLength() / binSize + 1;

        SparseRealMatrix rm = new OpenMapRealMatrix(nBins1, nBins1);

        List<Integer> blockNumbers = reader.getBlockNumbers(this);

        for (int blockNumber : blockNumbers) {
            Block b = null;
            try {
                b = reader.readNormalizedBlock(blockNumber, this, no);
                if (b != null) {
                    for (ContactRecord rec : b.getContactRecords()) {
                        int x = rec.getBinX();
                        int y = rec.getBinY();

                        double observed = rec.getCounts(); // Observed is already normalized

                        // The apache library doesn't seem to play nice with NaNs
                        if (!Double.isNaN(observed)) {
                            rm.addToEntry(x, y, observed);
                            if (x != y) {
                                rm.addToEntry(y, x, observed);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        // rm.walkInOptimizedOrder(new GaussianFilter());

        // Halfway through this.  Notes:
        // - could try as is, or with central difference filter, on Pearson's
        // - will likely have bad results on O/E directly, need to run Gaussian filter.  Not clear how
        //    to do Gaussian filter on sparse matrix, or if it's the right solution.  Perhaps: for each value,
        //    get surrounding values, obv. zero ones stay zero, & don't do it for the zeros?  not quite proper.
        //    otherwise, have to operate on dense matrix.
        // could always do it just for what's displayed...

        rm.walkInRowOrder(new GradientXFilter());

        // need to sum then return that vector, display as track...

        return null;
    }


    private boolean isZeros(double[] array) {
        for (double anArray : array)
            if (anArray != 0 && !Double.isNaN(anArray))
                return false;
        return true;
    }

    private double getVectorMean(RealVector vector) {
        double sum = 0;
        int count = 0;
        int size = vector.getDimension();
        for (int i = 0; i < size; i++) {
            if (!Double.isNaN(vector.getEntry(i))) {
                sum += vector.getEntry(i);
                count++;
            }
        }
        return sum / count;
    }

    // TODO -- Get rid of SparseRealMatrix (deprecated, bad NaN behavior), replace with Jim's SparseSymmetricMatrix
    public SparseRealMatrix computeOE(ExpectedValueFunction df, NormalizationType no) {

        if (chr1 != chr2) {
            throw new RuntimeException("Cannot compute expected values for inter-chromosome matrices");
        }

        //int nBins = blockBinCount * blockColumnCount;
        int nBins;

        if (zoom.getUnit() == HiC.Unit.BP) {
            nBins = chr1.getLength() / zoom.getBinSize() + 1;
        } else {
            nBins = ((DatasetReaderV2) reader).getFragCountMap().get(chr1.getName()) / zoom.getBinSize() + 1;
        }

        SparseRealMatrix rm = new OpenMapRealMatrix(nBins, nBins);

        List<Integer> blockNumbers = reader.getBlockNumbers(this);


        for (int blockNumber : blockNumbers) {
            Block b = null;
            try {
                b = reader.readNormalizedBlock(blockNumber, this, no);
                if (b != null) {
                    for (ContactRecord rec : b.getContactRecords()) {
                        int x = rec.getBinX();
                        int y = rec.getBinY();

                        int dist = Math.abs(x - y);
                        double expected = 0;
                        try {
                            expected = df.getExpectedValue(chr1.getIndex(), dist);
                        } catch (Exception e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                        double observed = rec.getCounts(); // Observed is already normalized
                        double normCounts = observed / expected;
                        // The apache library doesn't seem to play nice with NaNs
                        if (!Double.isNaN(normCounts)) {
                            rm.addToEntry(x, y, normCounts);
                            if (x != y) {
                                rm.addToEntry(y, x, normCounts);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        int size = rm.getRowDimension();
        BitSet bitSet = new BitSet(size);

        for (int i = 0; i < size; i++) {
            if (isZeros(rm.getRow(i))) {
                bitSet.set(i);
            }
        }
        nonCentromereColumns = new int[size - bitSet.cardinality()];

        int num = 0;
        for (int i = 0; i < size; i++) {
            if (!bitSet.get(i)) {
                nonCentromereColumns[num++] = i;
            }
        }

        return rm;
    }

    public void printDescription() {
        System.out.println("Chromosomes: " + chr1.getName() + " - " + chr2.getName());
        System.out.println("unit: " + zoom.getUnit());
        System.out.println("binSize (bp): " + zoom.getBinSize());
        System.out.println("blockBinCount (bins): " + blockBinCount);
        System.out.println("blockColumnCount (columns): " + blockColumnCount);

        System.out.println("Block size (bp): " + blockBinCount * zoom.getBinSize());
        System.out.println("");

    }

    /**
     * Dump observed matrix to text
     *
     * @param printWriter Text output stream
     */
    public void dump(PrintWriter printWriter, double[] nv1, double[] nv2) throws IOException {
        // Get the block index keys, and sort
        List<Integer> blockNumbers = reader.getBlockNumbers(this);
        if (blockNumbers != null) {
            Collections.sort(blockNumbers);

            for (int blockNumber : blockNumbers) {
                Block b = reader.readBlock(blockNumber, this);
                if (b != null) {
                    for (ContactRecord rec : b.getContactRecords()) {
                        float counts = rec.getCounts();
                        int x = rec.getBinX();
                        int y = rec.getBinY();
                        if (nv1 != null && nv2 != null) {
                            if (nv1[x] != 0 && nv2[y] != 0 && !Double.isNaN(nv1[x]) && !Double.isNaN(nv2[y])) {
                                counts = (float) (counts / (nv1[x] * nv2[y]));
                            } else {
                                counts = Float.NaN;
                            }
                        }
                        printWriter.println(x * zoom.getBinSize() + "\t" + y * zoom.getBinSize() + "\t" + counts);
                    }
                }
            }
        }
        printWriter.close();
    }

    /**
     * Dump observed matrix to binary.
     *
     * @param les Binary output stream
     * @throws IOException
     */
    public void dump(LittleEndianOutputStream les, double[] nv1, double[] nv2) throws IOException {

        // Get the block index keys, and sort
        List<Integer> blockNumbers = reader.getBlockNumbers(this);
        if (blockNumbers != null) {
            Collections.sort(blockNumbers);

            for (int blockNumber : blockNumbers) {
                Block b = reader.readBlock(blockNumber, this);
                if (b != null) {
                    for (ContactRecord rec : b.getContactRecords()) {
                        float counts = rec.getCounts();
                        int x = rec.getBinX();
                        int y = rec.getBinY();
                        if (nv1 != null && nv2 != null) {
                            if (nv1[x] != 0 && nv2[y] != 0 && !Double.isNaN(nv1[x]) && !Double.isNaN(nv2[y])) {
                                counts = (float) (counts / (nv1[x] * nv2[y]));
                            } else {
                                counts = Float.NaN;
                            }
                        }
                        les.writeInt(x);
                        les.writeInt(y);
                        les.writeFloat(counts);
                    }

                }
            }
        }
    }

    /**
     * Dump the O/E or Pearsons matrix to standard out in ascii format.
     *
     * @param df   Density function (expected values)
     * @param type will be "oe", "pearsons", or "expected"
     * @param les  output stream
     */
    public void dumpOE(ExpectedValueFunction df, String type, NormalizationType no, LittleEndianOutputStream les, PrintWriter pw) throws IOException {
        if (les == null && pw == null) {
            pw = new PrintWriter(System.out);
        }

        if (type.equals("oe")) {
            SparseRealMatrix oe = computeOE(df, no);
            int rows = oe.getRowDimension();
            int cols = oe.getColumnDimension();
            assert (rows == cols);
            if (les != null) les.writeInt(rows);

            int num = 0;
            for (int i = 0; i < rows; i++) {
                if (num >= nonCentromereColumns.length || i == nonCentromereColumns[num]) {
                    double[] row = oe.getRow(i);
                    int num2 = 0;
                    for (int j = 0; j < cols; j++) {
                        float output = Float.NaN;
                        if (num2 >= nonCentromereColumns.length || j == nonCentromereColumns[num2]) {
                            output = (float) row[j];
                            num2++;
                        }
                        if (les != null) les.writeFloat(output);
                        else pw.print(output + " ");

                    }
                    num++;
                    if (les == null) pw.println();
                } else {
                    for (int j = 0; j < cols; j++) {
                        if (les != null) les.writeFloat(Float.NaN);
                        else pw.print("NaN ");
                    }
                    if (les == null) pw.println();
                }

            }
            if (les == null) {
                pw.println();
                pw.flush();
            }
        } else {

            RealMatrix rm = ((RealMatrixWrapper) computePearsons(df, no)).getMatrix();
            int rows = rm.getRowDimension();
            int cols = rm.getColumnDimension();
            if (les != null) les.writeInt(rows);
            double[][] matrix = rm.getData();
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    if (les != null) les.writeFloat((float) matrix[i][j]);
                    else pw.print(matrix[i][j] + " ");
                }
                pw.println();
            }
            pw.flush();
        }
    }

    public void setAverageCount(double averageCount) {
        this.averageCount = averageCount;
    }

    public double getAverageCount() {
        return averageCount;
    }

    public Iterator<ContactRecord> contactRecordIterator() {
        return new ContactRecordIterator();
    }


    public class ContactRecordIterator implements Iterator<ContactRecord> {

        int blockIdx;
        List<Integer> blockNumbers;
        Iterator<ContactRecord> currentBlockIterator;

        public ContactRecordIterator() {

            this.blockIdx = -1;
            this.blockNumbers = reader.getBlockNumbers(MatrixZoomData.this);
        }

        @Override
        public boolean hasNext() {

            if (currentBlockIterator != null && currentBlockIterator.hasNext()) {
                return true;
            } else {
                blockIdx++;
                if (blockIdx < blockNumbers.size()) {
                    try {
                        int blockNumber = blockNumbers.get(blockIdx);
                        Block nextBlock = reader.readBlock(blockNumber, MatrixZoomData.this);
                        currentBlockIterator = nextBlock.getContactRecords().iterator();
                        return true;
                    } catch (IOException e) {
                        log.error("Error fetching block ", e);
                        return false;
                    }
                }
            }

            return false;
        }

        @Override
        public ContactRecord next() {
            return currentBlockIterator == null ? null : currentBlockIterator.next();
        }

        @Override
        public void remove() {
            //Not supported
            throw new RuntimeException("remove() is not supported");
        }
    }

    private class GradientXFilter extends DefaultRealMatrixChangingVisitor {
        double previousValue = Double.MAX_VALUE;

        public double visit(int row, int column, double value) throws org.apache.commons.math.linear.MatrixVisitorException {
            double newValue;
            if (previousValue != Double.MAX_VALUE) {
                newValue = (previousValue * -1 + value) / 2;
            } else newValue = value;
            previousValue = value;
            return newValue;
        }

    }


    public static enum Orientation {X, Y}

    ;

    public static class Slice {

        List<Pair<Integer, Float>> binValues;

        public Slice(List<Pair<Integer, Float>> binValues) {
            this.binValues = binValues;
        }


        public int getSize() {
            return binValues.size();
        }

        public int getBin(int idx) {
            return binValues.get(idx).getFirst();
        }

        public float getCounts(int idx) {
            return binValues.get(idx).getSecond();
        }
    }

    /*     // Actually, this isn't smart, Gaussian filter
    private class GaussianFilter extends DefaultRealMatrixChangingVisitor {
        private double[][] filter = new double[5][5];

        public GaussianFilter() {
            super();
            filter[0][0] = 0.0232; filter[0][1] = 0.0338; filter[0][2] = 0.0383; filter[0][3] = 0.0338; filter[0][4] = 0.0232;
            filter[1][0] = 0.0338; filter[1][1] = 0.0492; filter[1][2] = 0.0558; filter[1][3] = 0.0492; filter[0][0] = 0.0338;
            filter[2][0] = 0.0383; filter[2][1] = 0.0558; filter[2][2] = 0.0632; filter[2][3] = 0.0558; filter[0][0] = 0.0383;
            filter[3][0] = 0.0338; filter[3][1] = 0.0492; filter[3][2] = 0.0558; filter[3][3] = 0.0492; filter[0][0] = 0.0338;
            filter[4][0] = 0.0232; filter[4][1] = 0.0338; filter[4][2] = 0.0383; filter[4][3] = 0.0338; filter[0][0] = 0.0232;
        public double visit(int row, int column, double value) throws org.apache.commons.math.linear.MatrixVisitorException {


        }

    }    */

}
