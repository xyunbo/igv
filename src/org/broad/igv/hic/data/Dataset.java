package org.broad.igv.hic.data;

import org.apache.log4j.Logger;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.hic.HiC;
import org.broad.igv.hic.HiCZoom;
import org.broad.igv.hic.MainWindow;
import org.broad.igv.hic.NormalizationType;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.collections.LRUCache;

import java.io.*;
import java.util.*;

/**
 * @author jrobinso
 * @since Aug 12, 2010
 */
public class Dataset {

    private static Logger log = Logger.getLogger(Dataset.class);

    private boolean caching = true;

    //Chromosome lookup table
    public List<Chromosome> chromosomes;

    Map<String, Matrix> matrices = new HashMap<String, Matrix>(25 * 25);

    private DatasetReader reader;

    Map<String, ExpectedValueFunction> expectedValueFunctionMap;
    Map<String, Integer> fragCountMap = null;

    String genomeId;
    String restrictionEnzyme = null;

    List<HiCZoom> bpZooms;
    List<HiCZoom> fragZooms;
    private Map<String, String> attributes;

    LRUCache<String, double[]> eigenvectorCache;
    LRUCache<String, NormalizationVector> normalizationVectorCache;
    Map<String, NormalizationVector> loadedNormalizationVectors;
    private List<NormalizationType> normalizationTypes;

    public Dataset(DatasetReader reader) {
        this.reader = reader;
        eigenvectorCache = new LRUCache<String, double[]>(this, 20);
        normalizationVectorCache = new LRUCache<String, NormalizationVector>(this, 20);
        normalizationTypes = new ArrayList<NormalizationType>();
    }


    public Matrix getMatrix(Chromosome chr1, Chromosome chr2) {

        // order is arbitrary, convention is lower # chr first
        int t1 = Math.min(chr1.getIndex(), chr2.getIndex());
        int t2 = Math.max(chr1.getIndex(), chr2.getIndex());

        String key = Matrix.generateKey(t1, t2);
        Matrix m = matrices.get(key);

        if (m == null && reader != null) {
            try {
                m = reader.readMatrix(key);

                if (caching) matrices.put(key, m);
            } catch (IOException e) {
                log.error("Error fetching matrix for: " + chr1.getName() + "-" + chr2.getName(), e);
            }
        }

        return m;

    }


    public void setAttributes(Map<String, String> map) {
        this.attributes = map;
    }


    public List<NormalizationType> getNormalizationTypes() {
        return normalizationTypes;
    }


    public void addNormalizationType(NormalizationType type) {
        if (!normalizationTypes.contains(type)) normalizationTypes.add(type);
    }


    public int getNumberZooms(HiC.Unit unit) {
        return unit == HiC.Unit.BP ? bpZooms.size() : fragZooms.size();
    }


    public HiCZoom getZoom(HiC.Unit unit, int index) {
        return unit == HiC.Unit.BP ? bpZooms.get(index) : fragZooms.get(index);
    }


    public ExpectedValueFunction getExpectedValues(HiCZoom zoom, NormalizationType type) {
        if (expectedValueFunctionMap == null) return null;
        String key = zoom.getKey() + "_" + type.toString(); // getUnit() + "_" + zoom.getBinSize();
        return expectedValueFunctionMap.get(key);
    }


    public void setExpectedValueFunctionMap(Map<String, ExpectedValueFunction> df) {
        this.expectedValueFunctionMap = df;
    }


    public Map<String, ExpectedValueFunction> getExpectedValueFunctionMap() {
        return expectedValueFunctionMap;
    }


    public List<Chromosome> getChromosomes() {
        return chromosomes;
    }


    public void setChromosomes(List<Chromosome> chromosomes) {
        this.chromosomes = chromosomes;
    }


    public int getVersion() {
        return reader.getVersion();
    }


    public void setGenomeId(String genomeId) {
        this.genomeId = genomeId;
    }


    public String getGenomeId() {
        return genomeId;
    }


    public Map<String, Integer> getFragCountMap() {
        if (fragCountMap != null) return fragCountMap;
        else if (getVersion() > 1) {
            return ((DatasetReaderV2) reader).getFragCountMap();
        } else return null;
    }


    public String getRestrictionEnzyme() {
        if (restrictionEnzyme != null) return restrictionEnzyme;
        Map<Integer, String> reMap = createRestrictionEnzymeMap();

        if (getVersion() > 1) {
            Map<String, Integer> map = getFragCountMap();
            int numFragments;
            if (genomeId.equals("hg19")) {
                numFragments = map.get("1");
            } else if (genomeId.equals("mm9")) {
                numFragments = map.get("chr1");
            } else if (genomeId.equals("dMel")) {
                numFragments = map.get("arm_2L");
            } else if (genomeId.equals("sCerS288c")) {
                numFragments = map.get("chrI");
            } else numFragments = -1;
            restrictionEnzyme = reMap.get(numFragments);
            return restrictionEnzyme;
        } else return null;
    }


    public void setBpZooms(int[] bpBinSizes) {

        // Limit resolution in restricted mode
        int n = bpBinSizes.length;
        if (MainWindow.isRestricted()) {
            for (int i = 0; i < bpBinSizes.length; i++) {
                if (bpBinSizes[i] < 25000) {
                    n = i;
                    break;
                }
            }
        }

        this.bpZooms = new ArrayList<HiCZoom>(n);
        for (int i = 0; i < n; i++) {
            bpZooms.add(new HiCZoom(HiC.Unit.BP, bpBinSizes[i]));
        }

    }


    public void setFragZooms(int[] fragBinSizes) {

        // Don't show fragments in restricted mode
        if (MainWindow.isRestricted()) return;

        this.fragZooms = new ArrayList<HiCZoom>(fragBinSizes.length);
        for (int i = 0; i < fragBinSizes.length; i++) {
            fragZooms.add(new HiCZoom(HiC.Unit.FRAG, fragBinSizes[i]));
        }
    }


    public String getStatistics() {
        if (attributes == null) return null;
        else return attributes.get("statistics");
    }


    public String getGraphs() {
        if (attributes == null) return null;
        return attributes.get("graphs");
    }


    public List<HiCZoom> getBpZooms() {
        return bpZooms;
    }


    public List<HiCZoom> getFragZooms() {
        return fragZooms;
    }

    public boolean hasFrags() {
        return fragZooms != null && fragZooms.size() > 0;
    }

    /**
     * Return the "next" zoom level, relative to the current one, in the direction indicated
     *
     * @param zoom - current zoom level
     * @param b    -- direction, true == increasing resolution, false decreasing
     * @return Next zoom level
     */

    public HiCZoom getNextZoom(HiCZoom zoom, boolean b) {
        final HiC.Unit currentUnit = zoom.getUnit();
        List<HiCZoom> zoomList = currentUnit == HiC.Unit.BP ? bpZooms : fragZooms;

        if (b) {
            for (int i = 0; i < zoomList.size() - 1; i++) {
                if (zoom.equals(zoomList.get(i))) return zoomList.get(i + 1);
            }
            return zoomList.get(zoomList.size() - 1);

        } else {
            // Decreasing
            for (int i = zoomList.size() - 1; i > 0; i--) {
                if (zoom.equals(zoomList.get(i))) {
                    return zoomList.get(i - 1);
                }
            }
            return zoomList.get(0);
        }
    }


    public double[] getEigenvector(Chromosome chr, HiCZoom zoom, int number, NormalizationType type) {

        String key = chr.getName() + "_" + zoom.getKey() + "_" + number + "_" + type;
        if (!eigenvectorCache.containsKey(key)) {

            double[] eigenvector = null;
            eigenvector = reader.readEigenvector(chr.getName(), zoom, number, type.toString());

            if (eigenvector == null) {
                ExpectedValueFunction df = getExpectedValues(zoom, type);
                Matrix m = getMatrix(chr, chr);
                MatrixZoomData mzd = m.getZoomData(zoom);
                if (df != null && mzd.isSmallEnoughForPearsonCalculation(type)) {
                    eigenvector = mzd.computeEigenvector(df, number, type);
                } else {
                    // This causes a bug since render is called again in the process of this call
                    MessageUtils.showMessage("Eigenvector not available at this resolution");
                }
            }
            eigenvectorCache.put(key, eigenvector);
        }

        return eigenvectorCache.get(key);

    }


    public NormalizationVector getNormalizationVector(int chrIdx, HiCZoom zoom, NormalizationType type) {

        String key = NormalizationVector.getKey(type, chrIdx, zoom.getUnit().toString(), zoom.getBinSize());
        if (type == NormalizationType.NONE) {
            return null;
        } else if (type == NormalizationType.LOADED) {
            return loadedNormalizationVectors == null ? null : loadedNormalizationVectors.get(key);

        } else if (!normalizationVectorCache.containsKey(key)) {

            try {
                NormalizationVector nv = reader.readNormalizationVector(type, chrIdx, zoom.getUnit(), zoom.getBinSize());
                normalizationVectorCache.put(key, nv);
            } catch (IOException e) {
                normalizationVectorCache.put(key, null);
                // TODO -- warn user
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        return normalizationVectorCache.get(key);

    }


    public void putLoadedNormalizationVector(int chrIdx, int resolution, double[] data, double[] exp) {
        NormalizationVector normalizationVector = new NormalizationVector(NormalizationType.LOADED, chrIdx, HiC.Unit.BP, resolution, data);
        if (loadedNormalizationVectors == null) {
            loadedNormalizationVectors = new HashMap<String, NormalizationVector>();

        }
        loadedNormalizationVectors.put(normalizationVector.getKey(), normalizationVector);
        HiCZoom zoom = new HiCZoom(HiC.Unit.BP, resolution);
        String key = zoom.getKey() + "_LOADED";
        ExpectedValueFunctionImpl function = (ExpectedValueFunctionImpl) getExpectedValues(zoom, NormalizationType.KR);

        ExpectedValueFunctionImpl df = new ExpectedValueFunctionImpl(NormalizationType.LOADED, "BP", resolution, exp, function.getNormFactors());
        expectedValueFunctionMap.put(key, df);
    }

    private Map<Integer, String> createRestrictionEnzymeMap() {
        Map<Integer, String> restrictionEnzymeMap;
        restrictionEnzymeMap = new HashMap<Integer, String>();
        if (genomeId.equals("hg19") || genomeId.equals("hg19_contig")) {
            // chromosome 1
            restrictionEnzymeMap.put(22706, "Acc65I");
            restrictionEnzymeMap.put(4217, "AgeI");
            restrictionEnzymeMap.put(158473, "BseYI");
            restrictionEnzymeMap.put(74263, "BspHI");
            restrictionEnzymeMap.put(60834, "BstUI2");
            restrictionEnzymeMap.put(576357, "DpnII/MboI");
            restrictionEnzymeMap.put(139125, "HinP1I");
            restrictionEnzymeMap.put(64395, "HindIII");
            restrictionEnzymeMap.put(160930, "HpyCH4IV2");
            restrictionEnzymeMap.put(1632, "MluI");
            restrictionEnzymeMap.put(1428208, "MseI");
            restrictionEnzymeMap.put(194423, "MspI");
            restrictionEnzymeMap.put(59852, "NcoI");
            restrictionEnzymeMap.put(22347, "NheI");
            restrictionEnzymeMap.put(1072254, "NlaIII");
            restrictionEnzymeMap.put(1128, "NruI");
            restrictionEnzymeMap.put(2344, "SaII");
            restrictionEnzymeMap.put(1006921, "StyD4I");
            restrictionEnzymeMap.put(256163, "StyI");
            restrictionEnzymeMap.put(119506, "TaqI2");
            restrictionEnzymeMap.put(9958, "XhoI");
            restrictionEnzymeMap.put(31942, "XmaI");
        } else if (genomeId.equals("dMel")) {
            // arm_2L
            restrictionEnzymeMap.put(60924, "DpnII");
            restrictionEnzymeMap.put(6742, "HindIII");
            restrictionEnzymeMap.put(185217, "MseI");
        } else if (genomeId.equals("mm9")) {
            // chr1
            restrictionEnzymeMap.put(479082, "DpnII");
            restrictionEnzymeMap.put(62882, "HindIII");
            restrictionEnzymeMap.put(1157974, "MseI");
            restrictionEnzymeMap.put(60953, "NcoI");
            restrictionEnzymeMap.put(933321, "NlaIII");
        } else if (genomeId.equals("sCerS288c")) {
            // chrI
            restrictionEnzymeMap.put(65, "HindIII");
        }
        return restrictionEnzymeMap;
    }


    public void setAttributes(String key, String value) {
        if (this.attributes == null) {
            attributes = new HashMap<String, String>();
        }
        attributes.put(key, value);
    }


    public void setCaching(boolean caching) {
        this.caching = caching;
    }

}
