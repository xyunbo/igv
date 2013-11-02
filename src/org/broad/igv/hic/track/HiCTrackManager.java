/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not
 * responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL), Version 2.1 which is
 * available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.hic.track;

import org.apache.log4j.Logger;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.feature.tribble.CodecFactory;
import org.broad.igv.hic.HiC;
import org.broad.igv.hic.MainWindow;
import org.broad.igv.hic.NormalizationType;
import org.broad.igv.track.*;
import org.broad.igv.ui.color.ColorUtilities;
import org.broad.igv.util.ResourceLocator;
import org.broad.tribble.AbstractFeatureReader;
import org.broad.tribble.Feature;
import org.broad.tribble.FeatureCodec;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author Jim Robinson
 * @date 5/8/12
 */
public class HiCTrackManager {

    private static Logger log = Logger.getLogger(HiCTrackManager.class);

    static String path = "http://www.broadinstitute.org/igvdata/hic/tracksMenu.xml";
    //static String path = "/Users/jrobinso/Documents/IGV/hg19_encode.xml";

    // Category => list of locators
    private Map<String, List<ResourceLocator>> categoryLocatorMap = null;

    // track name => locator
    private Map<String, ResourceLocator> locatorMap;

    private java.util.List<HiCTrack> loadedTracks = new ArrayList();
    private Map<NormalizationType, HiCTrack> coverageTracks = new HashMap<NormalizationType, HiCTrack>();
    private Set<HiCTrack> fileLoadedTracks = new HashSet<HiCTrack>();
    private MainWindow mainWindow;
    private HiC hic;

    public HiCTrackManager(MainWindow mainWindow, HiC hic) {
        this.mainWindow = mainWindow;
        this.hic = hic;
    }

    public void loadTrack(final String path) {
        Runnable runnable = new Runnable() {
            public void run() {
                loadTrack(new ResourceLocator(path));
                mainWindow.updateTrackPanel();
            }
        };
       // mainWindow.executeLongRunningTask(runnable);
         runnable.run();
    }

    public void loadCoverageTrack(NormalizationType no) {

        if(coverageTracks.containsKey(no)) return; // Already loaded

        HiCDataSource source = new HiCCoverageDataSource(hic, no);
        ResourceLocator locator = new ResourceLocator(no.getLabel());
        HiCDataTrack track = new HiCDataTrack(hic, locator, source);
        coverageTracks.put(no, track);
        loadedTracks.add(track);
        mainWindow.updateTrackPanel();
    }


    public void load(final List<ResourceLocator> locators) {

        Runnable runnable = new Runnable() {
            public void run() {
                for (ResourceLocator locator : locators) {
                    loadTrack(locator);
                }

                mainWindow.updateTrackPanel();
            }
        };
        mainWindow.executeLongRunningTask(runnable);
    }

    private void loadTrack(final ResourceLocator locator) {

        Genome genome = loadGenome();
        String path = locator.getPath();
        String pathLC = path.toLowerCase();
        // genome = GenomeManager.getInstance().getCurrentGenome();

        if (pathLC.endsWith(".wig") || pathLC.endsWith(".bedgraph")) {
            HiCWigAdapter da = new HiCWigAdapter(hic, path);
            HiCDataTrack hicTrack = new HiCDataTrack(hic, locator, da);
            loadedTracks.add(hicTrack);
            fileLoadedTracks.add(hicTrack);

        } else if (pathLC.endsWith(".tdf") || pathLC.endsWith(".bigwig")) {
            List<Track> tracks = (new TrackLoader()).load(locator, genome);

            for (Track t : tracks) {
                HiCDataAdapter da = new HiCIGVDataAdapter(hic, (DataTrack) t);
                HiCDataTrack hicTrack = new HiCDataTrack(hic, locator, da);
                loadedTracks.add(hicTrack);
                fileLoadedTracks.add(hicTrack);
            }
        } else {
            FeatureCodec codec = CodecFactory.getCodec(locator.getPath(), genome);
            if (codec != null) {
                AbstractFeatureReader<Feature, ?> bfs = AbstractFeatureReader.getFeatureReader(locator.getPath(), codec, false);
                try {

                    Iterable<Feature> iter = bfs.iterator();
                    FeatureCollectionSource src = new FeatureCollectionSource(iter, genome);
                    HiCFeatureTrack track = new HiCFeatureTrack(hic, locator, src);
                    track.setName(locator.getTrackName());
                    loadedTracks.add(track);
                    fileLoadedTracks.add(track);

                } catch (IOException e) {
                    log.error("Error loading track: " + path, e);
                }
                Object header = bfs.getHeader();
                //TrackProperties trackProperties = getTrackProperties(header);

            }
        }

    }

    public void removeTrack(HiCTrack track) {
        loadedTracks.remove(track);

        NormalizationType key = null;
        for(Map.Entry<NormalizationType, HiCTrack> entry : coverageTracks.entrySet()) {
              if(entry.getValue() == track) {
                  key = entry.getKey();
              }
        }

        if(key != null) {
            coverageTracks.remove(key);
        }

    }

    public void removeTrack(ResourceLocator locator) {
        HiCTrack track = null;
        for (HiCTrack tmp: loadedTracks){
            if (((HiCDataTrack) tmp).getLocator().equals(locator)) {
                track = tmp;
                break;
            }
        }
        loadedTracks.remove(track);

        NormalizationType key = null;
        for(Map.Entry<NormalizationType, HiCTrack> entry : coverageTracks.entrySet()) {
            if(entry.getValue() == track) {
                key = entry.getKey();
            }
        }

        if(key != null) {
            coverageTracks.remove(key);
        }

    }



    public List<HiCTrack> getLoadedTracks() {
        return loadedTracks;
    }

    public void clearTracks() {
        loadedTracks.clear();
    }

    public synchronized Map<String, List<ResourceLocator>> getTrackLocators() {
        if (categoryLocatorMap == null) {
            try {
                categoryLocatorMap = parseResourceFile(path);
                locatorMap = new HashMap<String, ResourceLocator>();
                for (java.util.List<ResourceLocator> locatorList : categoryLocatorMap.values()) {
                    for (ResourceLocator loc : locatorList) {
                        locatorMap.put(loc.getTrackName(), loc);
                    }
                }
            } catch (ParserConfigurationException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        return categoryLocatorMap;
    }

    private Genome loadGenome() {
        Genome genome = GenomeManager.getInstance().getCurrentGenome();
        if (genome == null) {
            String genomePath = "http://igvdata.broadinstitute.org/genomes/hg19.genome";
            try {
                genome = GenomeManager.getInstance().loadGenome(genomePath, null);
            } catch (IOException e) {
                log.error("Error loading genome: " + genomePath, e);
            }
        }
        return genome;
    }

    private static Map<String, List<ResourceLocator>> parseResourceFile(String file) throws ParserConfigurationException, IOException, SAXException {

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(file);

        Map<String, List<ResourceLocator>> locators = Collections.synchronizedMap(new LinkedHashMap<String, List<ResourceLocator>>());

        NodeList categoryNodes = doc.getElementsByTagName("Category");
        int nNodes = categoryNodes.getLength();
        for (int i = 0; i < nNodes; i++) {
            Node node = categoryNodes.item(i);
            String nodeName = node.getAttributes().getNamedItem("name").getNodeValue();

            NodeList resourceNodes = node.getChildNodes();
            List<ResourceLocator> locatorList = new ArrayList(resourceNodes.getLength());
            for (int j = 0; j < resourceNodes.getLength(); j++) {
                Node resourceNode = resourceNodes.item(j);
                if (resourceNode.getNodeName().equals("Resource")) {
                    NamedNodeMap attributes = resourceNode.getAttributes();
                    Node nameNode = attributes.getNamedItem("name");
                    Node pathNode = attributes.getNamedItem("path");
                    Node trackLineNode = attributes.getNamedItem("trackLine");
                    Node colorNode = attributes.getNamedItem("color");
                    if (nameNode == null || pathNode == null) {
                        System.out.println("Skipping " + node.toString());
                        continue;
                    }

                    ResourceLocator rl = new ResourceLocator(pathNode.getNodeValue());
                    rl.setName(nameNode.getNodeValue());

                    if (trackLineNode != null) {
                        rl.setTrackLine(trackLineNode.getNodeValue());
                    }
                    if (colorNode != null) {
                        try {
                            rl.setColor(ColorUtilities.stringToColor(colorNode.getNodeValue()));
                        } catch (Exception e) {
                            log.error("Error parsing color value: " + colorNode.getNodeValue(), e);
                        }
                    }

                    locatorList.add(rl);

                }
            }

            locators.put(nodeName, locatorList);
        }
        return locators;


    }

}
