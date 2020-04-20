package org.broad.igv.track;

import htsjdk.tribble.Feature;
import org.broad.igv.feature.*;

import java.util.*;

/**
 * The GFF spec is available at http://www.sequenceontology.org/gff3.shtml
 * <p/>
 * GFF Combiner is needed because IGV represents a transcript (e.g. an mRNAs) as a single feature with,
 * optionally, a collection of child exons.  The feature can have a "thick" start and end, corresponding
 * to coding start and end.  This representation comes from a literal transcript of certain UCSC formats.
 * The same feature in a GFF file, on the other hand, is represented as a graph of sub features with
 * defined by parent-child relations.  GFF3 formalizes the feature types and their relationships to some
 * degree in the Sequence Ontology,
 */
public class GFFCombiner {

    List<Feature> igvFeatures;
    Map<String, GFFFeature> gffFeatures;
    List<BasicFeature> gffExons;
    Map<String, GFFCdsCltn> gffCdss;
    List<BasicFeature> gffUtrs;
    List<BasicFeature> gffMrnaParts;

    public GFFCombiner() {
        int numElements = 10000;
        igvFeatures = new ArrayList<Feature>(numElements);
        gffFeatures = new HashMap<String, GFFFeature>(numElements);
        gffExons = new ArrayList<BasicFeature>(numElements);
        gffCdss = new LinkedHashMap<String, GFFCdsCltn>(numElements);
        gffUtrs = new ArrayList<BasicFeature>(numElements);
        gffMrnaParts = new ArrayList<>(numElements);

    }

    /**
     * First pass, create transcripts so everything
     * has a parent (if it exists in the iterator)
     *
     * @param rawIter
     * @return this, for chaining
     */
    public GFFCombiner addFeatures(Iterator<Feature> rawIter) {
        while (rawIter.hasNext()) {
            addFeature((BasicFeature) rawIter.next());
        }
        return this;
    }


    public void addFeature(BasicFeature bf) {

        String featureType = bf.getType();
        String[] parentIDs = bf.getParentIds();
        String id = bf.getIdentifier();
        if (SequenceOntology.mrnaParts.contains(featureType) && parentIDs != null) {
            gffMrnaParts.add(bf);
            if (SequenceOntology.exonTypes.contains(featureType) && parentIDs != null) {
                gffExons.add(bf);
            } else if (SequenceOntology.utrTypes.contains(featureType) && parentIDs != null) {
                gffUtrs.add(bf);
            } else if (SequenceOntology.cdsTypes.contains(featureType) && parentIDs != null) {
                for (String pid : parentIDs) {
                    GFFCdsCltn cds = gffCdss.get(pid);
                    if (cds == null) {
                        cds = new GFFCdsCltn(pid);
                        gffCdss.put(pid, cds);
                    }
                    cds.addPart(bf);
                }
            }
        } else if (id != null) {
            gffFeatures.put(id, new GFFFeature(bf));
        } else {
            igvFeatures.add(bf); // Just use this feature  as is.
        }
    }


    public List<Feature> combineFeatures() {

        // Exons
        for (BasicFeature gffExon : gffExons) {
            final String[] parentIds = gffExon.getParentIds();
            for (String parentId : parentIds) {
                GFFFeature parent = gffFeatures.get(parentId);
                if (parent == null) {
                    parent = createParent(gffExon);
                    parent.setIdentifier(parentId);
                    parent.setName(parentId);
                    gffFeatures.put(parentId, parent);
                }
                final Exon exon = new Exon(gffExon);
                exon.setNonCoding(!SequenceOntology.isCoding(gffExon.getType()));
                parent.addExon(exon);

            }
        }

        // Now process utrs.  Modify exon if its already defined, create a new one if not.
        for (BasicFeature utr : gffUtrs) {
            for (String parentId : utr.getParentIds()) {
                GFFFeature parent = gffFeatures.get(parentId);
                if (parent == null) {
                    parent = createParent(utr);
                    parent.setIdentifier(parentId);
                    parent.setName(parentId);
                    gffFeatures.put(parentId, parent);
                }
                parent.addUTRorCDS(utr);

            }
        }

        // Overlay cdss
        for (GFFCdsCltn gffCdsCltn : gffCdss.values()) {

            // Get the parent.
            String parentId = gffCdsCltn.getParentId();
            GFFFeature parent = gffFeatures.get(parentId);
            if (parent == null) {
                // Create a "dummy" transcript for the orphaned cds records
                parent = new GFFFeature(gffCdsCltn.chr, gffCdsCltn.start, gffCdsCltn.end, gffCdsCltn.strand);
                parent.setIdentifier(parentId);
                parent.setName(parentId);
                gffFeatures.put(parentId, parent);
            }
            // Now add the cds objects.  There are 2 conventions in use for describing the coding section of mRNAs
            // (1) All cds records for the same isoform get the same id.  CDS objects with different ids then
            // imply different isoforms.  In IGV we need to create a parent object for each.  (2) each CDS has
            // a unique ID, and all cds records with the same parent id belong to the same isoform.

            if (gffCdsCltn.isUniqueIds()) {
                for (BasicFeature cdsPart : gffCdsCltn.getParts()) {
                    parent.addUTRorCDS(cdsPart);
                }

            } else {
                Map<String, List<BasicFeature>> cdsPartsMap = gffCdsCltn.getPartsById();
                boolean first = true;
                for (Map.Entry<String, List<BasicFeature>> entry : cdsPartsMap.entrySet()) {
                    List<BasicFeature> cdsParts = entry.getValue();

                    BasicFeature isoform;
                    if (first) {
                        isoform = parent;
                    } else {
                        isoform = copyForCDS(parent);
                        igvFeatures.add(isoform);
                    }
                    for (BasicFeature cds : cdsParts) {
                        isoform.addUTRorCDS(cds);
                    }
                    first = false;
                }
            }
        }


        // Merge attributes (column 9)
        for (BasicFeature mrnaPart : gffMrnaParts) {
            for (String parentId : mrnaPart.getParentIds()) {
                GFFFeature parent = gffFeatures.get(parentId);
                if (parent == null) {
                    // This shouldn't happen, but if it does use feature directly
                    igvFeatures.add(mrnaPart);
                } else {
                    parent.mergeAttributes(mrnaPart);
                }

            }
        }


        igvFeatures.addAll(gffFeatures.values());


        for (Feature f : igvFeatures) {
            BasicFeature bf = (BasicFeature) f;
            if (bf.hasExons()) {
                bf.sortExons();
                List<Exon> exons = bf.getExons();
                int exonNumber = bf.getStrand() == Strand.NEGATIVE ? exons.size() : 1;
                int increment = bf.getStrand() == Strand.NEGATIVE ? -1 : 1;
                for (Exon ex : exons) {
                    ex.setNumber(exonNumber);
                    exonNumber += increment;
                }
            }
        }

        FeatureUtils.sortFeatureList(igvFeatures);
        return igvFeatures;

    }

    private GFFFeature createParent(BasicFeature gffExon) {

        return new GFFFeature(gffExon.getChr(), gffExon.getStart(), gffExon.getEnd(), gffExon.getStrand());

    }


    private static BasicFeature copyForCDS(BasicFeature bf) {

        BasicFeature copy = new BasicFeature(bf.getChr(), bf.getStart(), bf.getEnd(), bf.getStrand());
        copy.setName(bf.getName());
        copy.setColor(bf.getColor());
        copy.setIdentifier(bf.getIdentifier());
        copy.setURL(bf.getURL());
        copy.setType(bf.getType());
        for (Exon ex : bf.getExons()) {
            Exon newExon = new Exon(ex);
            newExon.setNonCoding(true);
            copy.addExon(newExon);

        }

        return copy;
    }

    /**
     * Container to hold all the cds records for a given parent (usually an mRNA).
     */
    public static class GFFCdsCltn {
        String parentId;
        List<BasicFeature> cdsParts;
        String chr;
        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        Strand strand;

        public GFFCdsCltn(String parentId) {
            this.parentId = parentId;
            cdsParts = new ArrayList(5);
        }

        public void addPart(BasicFeature bf) {
            cdsParts.add(bf);
            this.chr = bf.getChr();
            this.start = Math.min(this.start, bf.getStart());
            this.end = Math.max(this.end, bf.getEnd());
            this.strand = bf.getStrand();
        }

        public String getParentId() {
            return parentId;
        }

        public List<BasicFeature> getParts() {
            return cdsParts;
        }

        public Map<String, List<BasicFeature>> getPartsById() {

            Map<String, List<BasicFeature>> map = new HashMap<String, List<BasicFeature>>();
            for (BasicFeature bf : cdsParts) {
                String id = bf.getIdentifier();
                List<BasicFeature> parts = map.get(id);
                if (parts == null) {
                    parts = new ArrayList<BasicFeature>();
                    map.put(id, parts);
                }
                parts.add(bf);

            }
            return map;
        }

        public boolean isUniqueIds() {
            if (cdsParts.isEmpty()) return true;
            Iterator<BasicFeature> iter = cdsParts.iterator();
            String firstId = iter.next().getIdentifier();
            while (iter.hasNext()) {
                BasicFeature bf = iter.next();
                if (firstId.equals(bf.getIdentifier())) return false;
            }
            return true;
        }
    }

}