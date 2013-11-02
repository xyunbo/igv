package org.broad.igv.hic;

import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.hic.track.*;
import org.broad.igv.hic.data.*;
import org.broad.igv.ui.color.ColorUtilities;
import org.broad.igv.util.ParsingUtils;
import org.broad.igv.util.ResourceLocator;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * This is the "model" class for the HiC viewer.
 *
 * @author Jim Robinson
 * @date 4/8/12
 */
public class HiC {

    private static Logger log = Logger.getLogger(HiC.class);

    public enum Unit {BP, FRAG}

    private MainWindow mainWindow;
    private MainWindow.MatrixType displayOption;
    private NormalizationType normalizationType;
    private java.util.List<Chromosome> chromosomes;

    private List<Dataset> datasets;
    private Dataset controlDataset;
    private HiCZoom zoom;

    private Context xContext;
    private Context yContext;

    private Map<String, Feature2DList> loopLists;
    private boolean showLoops;

    private EigenvectorTrack eigenvectorTrack;

    private HiCTrackManager trackManager;
    private ResourceTree resourceTree;

    private Point cursorPoint;
    private Point selectedBin;

    private boolean linkedMode;


    public HiC(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.trackManager = new HiCTrackManager(mainWindow, this);
        this.loopLists = new HashMap<String, Feature2DList>();
        this.datasets = new ArrayList<Dataset>();
    }

    public void reset() {
        datasets.clear();
        xContext = null;
        yContext = null;
        eigenvectorTrack = null;
        resourceTree = null;
        trackManager.clearTracks();
        loopLists.clear();
        showLoops = true;
    }


    public void setControlDataset(Dataset controlDataset) {
        this.controlDataset = controlDataset;
    }

    public void setEigenvectorTrack(EigenvectorTrack eigenvectorTrack) {
        this.eigenvectorTrack = eigenvectorTrack;
    }

    public EigenvectorTrack getEigenvectorTrack() {
        if (eigenvectorTrack == null) {
            eigenvectorTrack = new EigenvectorTrack("eigen", "Eigenvector", this);
        }
        return eigenvectorTrack;
    }

    public ResourceTree getResourceTree() {
        return resourceTree;
    }

    public void setResourceTree(ResourceTree rTree) {
        resourceTree = rTree;
    }

    public List<Feature2D> getVisibleLoopList(int chrIdx1, int chrIdx2) {
        if (!showLoops) return null;
        List<Feature2D> visibleLoopList = new ArrayList<Feature2D>();
        for (Feature2DList list : loopLists.values()) {
            if (list.isVisible()) {
                List<Feature2D> currList = list.get(chrIdx1, chrIdx2);
                if (currList != null) {
                    for (Feature2D feature2D : currList) {
                        visibleLoopList.add(feature2D);
                    }
                }
            }
        }
        return visibleLoopList;
    }

    public void setShowLoops(boolean showLoops1) {
        showLoops = showLoops1;

    }

    public void setLoopsInvisible(String path) {
        loopLists.get(path).setVisible(false);
    }

    public boolean isLinkedMode() {
        return linkedMode;
    }

    public void setLinkedMode(boolean linkedMode) {
        this.linkedMode = linkedMode;
    }

    public java.util.List<HiCTrack> getLoadedTracks() {
        return trackManager == null ? new ArrayList<HiCTrack>() : trackManager.getLoadedTracks();
    }

    public void loadHostedTracks(List<ResourceLocator> locators) {
        trackManager.load(locators);
    }

    public void loadTrack(String path) {
        trackManager.loadTrack(path);
    }


    public void loadCoverageTrack(NormalizationType no) {
        trackManager.loadCoverageTrack(no);
    }

    public void removeTrack(HiCTrack track) {
        if (resourceTree != null) resourceTree.remove(track.getLocator());

        trackManager.removeTrack(track);
    }

    public void removeTrack(ResourceLocator locator) {
        trackManager.removeTrack(locator);
    }

    public Dataset getDataset() {
        return datasets.isEmpty() ? null : datasets.get(0);
    }

    public void setDataset(Dataset dataset) {
        this.datasets.add(dataset);
    }

    public void setSelectedChromosomes(Chromosome chrX, Chromosome chrY) {
        this.xContext = new Context(chrX);
        this.yContext = new Context(chrY);

        if (eigenvectorTrack != null) {
            eigenvectorTrack.forceRefresh();
        }

    }

    public HiCZoom getZoom() {
        return zoom;
    }

    public MatrixZoomData getZd() {
        Matrix matrix = getMatrix();
        if (matrix == null || zoom == null) {
            return null;
        } else {
            return getMatrix().getZoomData(zoom);
        }
    }

    public MatrixZoomData getControlZd() {

        if (controlDataset == null || xContext == null || zoom == null) return null;

        Matrix matrix = controlDataset.getMatrix(xContext.getChromosome(), yContext.getChromosome());
        return matrix.getZoomData(zoom);

    }

    public Context getXContext() {
        return xContext;
    }

    public Context getYContext() {
        return yContext;
    }

    public void resetContexts() {
        this.xContext = null;
        this.yContext = null;
    }

    public void setCursorPoint(Point point) {
        this.cursorPoint = point;

    }

    public Point getCursorPoint() {
        return cursorPoint;
    }

    public Matrix getMatrix() {
        return datasets == null || xContext == null ? null : getDataset().getMatrix(xContext.getChromosome(), yContext.getChromosome());

    }

    public void setSelectedBin(Point point) {
        if (point.equals(this.selectedBin)) {
            this.selectedBin = null;
        } else {
            this.selectedBin = point;
        }
    }

    public MainWindow.MatrixType getDisplayOption() {
        return displayOption;
    }


    public boolean isWholeGenome() {
        return xContext != null && xContext.getChromosome().getName().equals("All");
    }

    public void setChromosomes(List<Chromosome> chromosomes) {
        this.chromosomes = chromosomes;
    }

    public java.util.List<Chromosome> getChromosomes() {
        return chromosomes;
    }

    public static boolean isPrivateHic(String string) {
        return string.contains("igvdata/hic/files");
    }

    /**
     * Change zoom level and recenter.  Triggered by the resolutionSlider, or by a double-click in the
     * heatmap panel.
     */
    public boolean setZoom(HiCZoom newZoom, final double centerGenomeX, final double centerGenomeY) {

        final Chromosome chr1 = xContext.getChromosome();
        final Chromosome chr2 = yContext.getChromosome();

        // Verify that all datasets have this zoom level
        HiCGridAxis xGridAxis = null;
        HiCGridAxis yGridAxis = null;
        for (Dataset ds : datasets) {
            Matrix matrix = ds.getMatrix(chr1, chr2);
            if (matrix == null) return false;

            MatrixZoomData newZD;
            if (chr1.getName().equals("All")) {
                newZD = matrix.getFirstZoomData(Unit.BP);
            } else {
                newZD = matrix.getZoomData(newZoom);
            }
            if (newZD == null) {
                JOptionPane.showMessageDialog(mainWindow, "Sorry, this zoom is not available", "Zoom unavailable", JOptionPane.WARNING_MESSAGE);
                return false;
            }

            if (xGridAxis == null) {
                // Assumption is all datasets share the same grid axis
                xGridAxis = newZD.getXGridAxis();
                yGridAxis = newZD.getYGridAxis();
            }
        }

        zoom = newZoom;

        xContext.setZoom(zoom);
        yContext.setZoom(zoom);

        int xBinCount = xGridAxis.getBinCount();
        int yBinCount = yGridAxis.getBinCount();
        int maxBinCount = Math.max(xBinCount, yBinCount);

        double scalefactor = Math.max(1.0, (double) mainWindow.getHeatmapPanel().getMinimumDimension() / maxBinCount);

        xContext.setScaleFactor(scalefactor);
        yContext.setScaleFactor(scalefactor);

        //Point binPosition = zd.getBinPosition(genomePositionX, genomePositionY);
        int binX = xGridAxis.getBinNumberForGenomicPosition((int) centerGenomeX);
        int binY = yGridAxis.getBinNumberForGenomicPosition((int) centerGenomeY);

        center(binX, binY);

        if(linkedMode) {
            broadcastState();
        }

        return true;
    }


    // Called from alt-drag
    public void zoomTo(final int xBP0, final int yBP0, double targetBinSize) {


        if (zoom == null || datasets.isEmpty()) return;  // No data in view

        Dataset ds = datasets.get(0);

        final Chromosome chr1 = xContext.getChromosome();
        final Chromosome chr2 = yContext.getChromosome();
        final Matrix matrix = ds.getMatrix(chr1, chr2);


        HiC.Unit unit = zoom.getUnit();

        // Find the new resolution,
        HiCZoom newZoom = zoom;
        if (!mainWindow.isResolutionLocked()) {
            List<HiCZoom> zoomList = unit == HiC.Unit.BP ? ds.getBpZooms() : ds.getFragZooms();
            zoomList.get(zoomList.size() - 1);   // Highest zoom level by defaul
            for (int i = zoomList.size() - 1; i >= 0; i--) {
                if (zoomList.get(i).getBinSize() > targetBinSize) {
                    newZoom = zoomList.get(i);
                    break;
                }
            }
        }

        final MatrixZoomData newZD = matrix.getZoomData(newZoom);

        int binX0 = newZD.getXGridAxis().getBinNumberForGenomicPosition((int) xBP0);
        int binY0 = newZD.getYGridAxis().getBinNumberForGenomicPosition((int) yBP0);

        final double scaleFactor = newZD.getBinSize() / targetBinSize;

        zoom = newZD.getZoom();


        mainWindow.updateZoom(zoom);

        xContext.setScaleFactor(scaleFactor);
        yContext.setScaleFactor(scaleFactor);

        xContext.setBinOrigin(binX0);
        yContext.setBinOrigin(binY0);


        if(linkedMode) {
            broadcastState();
        }

        mainWindow.refresh();


    }

    public void centerFragment(int fragmentX, int fragmentY) {
        if (zoom != null) {

            MatrixZoomData zd = getMatrix().getZoomData(zoom);
            HiCGridAxis xAxis = zd.getXGridAxis();
            HiCGridAxis yAxis = zd.getYGridAxis();

            int binX = xAxis.getBinNumberForFragment(fragmentX);
            int binY = yAxis.getBinNumberForFragment(fragmentY);
            center(binX, binY);

        }
    }

    public void centerBP(int bpX, int bpY) {
        if (zoom != null) {
            MatrixZoomData zd = getMatrix().getZoomData(zoom);
            HiCGridAxis xAxis = zd.getXGridAxis();
            HiCGridAxis yAxis = zd.getYGridAxis();

            int binX = xAxis.getBinNumberForGenomicPosition(bpX);
            int binY = yAxis.getBinNumberForGenomicPosition(bpY);
            center(binX, binY);

        }
    }

    /**
     * Center the bins in view at the current resolution.
     *
     * @param binX
     * @param binY
     */
    public void center(double binX, double binY) {

        double w = mainWindow.getHeatmapPanel().getWidth() / xContext.getScaleFactor();  // view width in bins
        int newOriginX = (int) (binX - w / 2);

        double h = mainWindow.getHeatmapPanel().getHeight() / yContext.getScaleFactor();  // view hieght in bins
        int newOriginY = (int) (binY - h / 2);
        moveTo(newOriginX, newOriginY);
    }

    /**
     * Move by the specified delta (in bins)
     *
     * @param dxBins -- delta x in bins
     * @param dyBins -- delta y in bins
     */
    public void moveBy(double dxBins, double dyBins) {
        final double newX = xContext.getBinOrigin() + dxBins;
        final double newY = yContext.getBinOrigin() + dyBins;
        moveTo(newX, newY);
    }

    /**
     * Move to the specfied origin (in bins)
     *
     * @param newBinX
     * @param newBinY
     */
    private void moveTo(double newBinX, double newBinY) {

        MatrixZoomData zd = getZd();

        final double wBins = (mainWindow.getHeatmapPanel().getWidth() / xContext.getScaleFactor());
        double maxX = zd.getXGridAxis().getBinCount() - wBins;

        final double hBins = (mainWindow.getHeatmapPanel().getHeight() / yContext.getScaleFactor());
        double maxY = zd.getYGridAxis().getBinCount() - hBins;

        double x = Math.max(0, Math.min(maxX, newBinX));
        double y = Math.max(0, Math.min(maxY, newBinY));

        xContext.setBinOrigin(x);
        yContext.setBinOrigin(y);

//        String locus1 = "chr" + (xContext.getChromosome().getName()) + ":" + x + "-" + (int) (x + bpWidthX);
//        String locus2 = "chr" + (yContext.getChromosome().getName()) + ":" + x + "-" + (int) (y + bpWidthY);
//        IGVUtils.sendToIGV(locus1, locus2);

        mainWindow.repaint();

        if(linkedMode) {
            broadcastState();
        }
    }


    public void setDisplayOption(MainWindow.MatrixType newDisplay) {

        if (this.displayOption != newDisplay) {
            this.displayOption = newDisplay;
        }
    }

    public void setNormalizationType(NormalizationType option) {
        this.normalizationType = option;
    }

    public NormalizationType getNormalizationType() {
        return normalizationType;
    }

    public double[] getEigenvector(final int chrIdx, final int n) {

        if (datasets.isEmpty()) return null;

        Chromosome chr = chromosomes.get(chrIdx);
        MainWindow.getInstance().showGlassPane();
        double[] returnValue = datasets.get(0).getEigenvector(chr, zoom, n, normalizationType);
        MainWindow.getInstance().hideGlassPane();
        return returnValue;

    }

    public ExpectedValueFunction getExpectedValues() {
        if (datasets.isEmpty()) return null;
        return datasets.get(0).getExpectedValues(zoom, normalizationType);
    }

    public NormalizationVector getNormalizationVector(int chrIdx) {
        if (datasets.isEmpty()) return null;
        return datasets.get(0).getNormalizationVector(chrIdx, zoom, normalizationType);
    }

    // Note - this is an inefficient method, used to support tooltip text only.
    public float getNormalizedObservedValue(int binX, int binY) {

        return getZd().getObservedValue(binX, binY, normalizationType);

    }


    public void loadLoopList(String path) throws IOException {

        if (loopLists.get(path) != null) {
            loopLists.get(path).setVisible(true);
            return;
        }

        int attCol = 7;
        BufferedReader br = null;

        Feature2DList newList = new Feature2DList();

        try {
            br = ParsingUtils.openBufferedReader(path);
            String nextLine;

            // header
            nextLine = br.readLine();
            String[] headers = Globals.tabPattern.split(nextLine);

            int errorCount = 0;
            while ((nextLine = br.readLine()) != null) {
                String[] tokens = Globals.tabPattern.split(nextLine);
                if (tokens.length > headers.length) {
                    throw new IOException("Improperly formatted file");
                }
                if (tokens.length < 6) {
                    throw new IOException("Improperly formatted file");
                }
                String chr1Name = tokens[0];
                int start1 = Integer.parseInt(tokens[1]);
                int end1 = Integer.parseInt(tokens[2]);

                String chr2Name = tokens[3];
                int start2 = Integer.parseInt(tokens[4]);
                int end2 = Integer.parseInt(tokens[5]);

                Color c = tokens.length > 6 ? ColorUtilities.stringToColor(tokens[6].trim()) : Color.black;

                Map<String, String> attrs = new LinkedHashMap<String, String>();
                for (int i = attCol; i < tokens.length; i++) {

                    attrs.put(headers[i], tokens[i]);
                }

                Chromosome chr1 = this.getChromosomeNamed(chr1Name);
                Chromosome chr2 = this.getChromosomeNamed(chr2Name);
                if (chr1 == null || chr2 == null) {
                    if (errorCount < 100) {
                        log.error("Skipping line: " + nextLine);
                    } else if (errorCount == 100) {
                        log.error("Maximum error count exceeded.  Further errors will not be logged");
                    }

                    errorCount++;
                    continue;
                }

                // Convention is chr1 is lowest "index". Swap if necessary
                Feature2D feature = chr1.getIndex() <= chr2.getIndex() ?
                        new Feature2D(chr1Name, start1, end1, chr2Name, start2, end2, c, attrs) :
                        new Feature2D(chr2Name, start2, end2, chr1Name, start1, end1, c, attrs);

                newList.add(chr1.getIndex(), chr2.getIndex(), feature);

            }
            loopLists.put(path, newList);
        } finally {
            if (br != null) br.close();
        }

    }

    private Chromosome getChromosomeNamed(String token) {
        for (Chromosome chr : chromosomes) {
            if (token.equals(chr.getName())) return chr;
        }
        return null;
    }


    /**
     * Change zoom level and recenter.  Triggered by the resolutionSlider, or by a double-click in the
     * heatmap panel.
     */
    public void setState(String chrXName, String chrYName, String unitName, int binSize, double xOrigin, double yOrigin, double scalefactor) {

        if (!chrXName.equals(xContext.getChromosome().getName()) || !chrYName.equals(yContext.getChromosome().getName())) {

            Chromosome chrX = getChromosomeNamed(chrXName);
            Chromosome chrY = getChromosomeNamed(chrYName);
            this.xContext = new Context(chrX);
            this.yContext = new Context(chrY);
            mainWindow.setSelectedChromosomesNoRefresh(chrX, chrY);
            if (eigenvectorTrack != null) {
                eigenvectorTrack.forceRefresh();
            }
        }

        HiCZoom newZoom = new HiCZoom(Unit.valueOf(unitName), binSize);
        if (!newZoom.equals(zoom)) {
            zoom = newZoom;
            xContext.setZoom(zoom);
            yContext.setZoom(zoom);
            mainWindow.updateZoom(newZoom);
        }

        xContext.setScaleFactor(scalefactor);
        yContext.setScaleFactor(scalefactor);
        xContext.setBinOrigin(xOrigin);
        yContext.setBinOrigin(yOrigin);

        mainWindow.refresh();

    }

    public void broadcastState() {
        String command = "setstate " +
                xContext.getChromosome().getName() + " " +
                yContext.getChromosome().getName() + " " +
                zoom.getUnit().toString() + " " +
                zoom.getBinSize() + " " +
                xContext.getBinOrigin() + " " +
                yContext.getBinOrigin() + " " +
                xContext.getScaleFactor();

        CommandBroadcaster.broadcast(command);
    }
}
