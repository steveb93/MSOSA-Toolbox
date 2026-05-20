package com.uaf.neo4j.plugin.model;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.jmi.helpers.ModelHelper;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Classifier;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;

import java.util.*;
import java.util.logging.Logger;

/**
 * Walks the full MSOSA project model tree, identifies stereotyped elements across
 * UAF 1.2, SysML 1.6, and BPMN 2.0, extracts tagged values, and returns typed
 * DTOs ready for Neo4j export.
 *
 * Language origin is resolved from UAFStereotypeRegistry and written to every
 * element and relationship DTO so hybrid models remain queryable by language.
 *
 * Selection rules (see issue #75 for the bug history these address):
 * <ul>
 *   <li>Stereotype priority is UAF → BPMN → SysML (in {@link #LANGUAGE_RANK}).
 *       Without this, an element stereotyped as both {@code OperationalPerformer}
 *       and SysML {@code Block} can resolve to {@code Block} depending on the
 *       order MSOSA returns stereotypes, dropping the UAF domain context.</li>
 *   <li>Among same-language matches, the most specific stereotype wins (the one
 *       that is not in another candidate's general chain). This handles profiles
 *       where users apply both a parent and a derived stereotype.</li>
 *   <li>Inherited stereotypes are honoured: if a directly-applied stereotype is
 *       not in the registry, the general chain is walked to find a registered
 *       ancestor (e.g., a custom {@code MyOperationalPerformer} that inherits
 *       from {@code OperationalPerformer} resolves to OPERATIONAL).</li>
 *   <li>Recursion descends into any element with {@code getOwnedElement()}, not
 *       just {@code Package}. This is essential for Resource/Operational
 *       internal-block-diagram content (parts, ports, nested classifiers) and
 *       activity-owned actions.</li>
 * </ul>
 *
 * Stereotype names that appear in the model but resolve to nothing — even via
 * inheritance — are recorded in {@link #getUnmatchedStereotypes()} so future
 * profile drift is visible in the export summary rather than buried in WARNING
 * logs.
 */
public class UAFModelTraverser {

    private static final Logger LOG = Logger.getLogger(UAFModelTraverser.class.getName());

    // UML metaclass name → Neo4j relationship type (base mapping before stereotype override)
    private static final Map<String, String> RELATION_TYPE_MAP = new LinkedHashMap<>();

    // Multi-language relationship stereotype name → Neo4j relationship type.
    // Kept separate from UAFStereotypeRegistry so that relationship stereotypes
    // (applied to UML relationship elements, not to blocks/classes/tasks)
    // are never mistaken for element stereotypes and never create nodes.
    // Package-private so unit tests can verify membership.
    static final Map<String, String> RELATIONSHIP_STEREOTYPE_MAP = new LinkedHashMap<>();

    // Language priority for stereotype selection — lower = preferred.
    // Package-private for unit testing.
    static final Map<String, Integer> LANGUAGE_RANK = new LinkedHashMap<>();
    static {
        LANGUAGE_RANK.put("UAF",   0);
        LANGUAGE_RANK.put("BPMN",  1);
        LANGUAGE_RANK.put("SysML", 2);
    }

    static {
        RELATION_TYPE_MAP.put("Realization",          UAFRelationshipDTO.REL_REALISES);
        RELATION_TYPE_MAP.put("Abstraction",          UAFRelationshipDTO.REL_TRACES_TO);
        RELATION_TYPE_MAP.put("Dependency",           UAFRelationshipDTO.REL_DEPENDENCY);
        RELATION_TYPE_MAP.put("Association",          UAFRelationshipDTO.REL_ASSOCIATED_WITH);
        RELATION_TYPE_MAP.put("Generalization",       UAFRelationshipDTO.REL_GENERALIZATION);
        RELATION_TYPE_MAP.put("InformationFlow",      UAFRelationshipDTO.REL_INFORMATION_FLOW);
        RELATION_TYPE_MAP.put("ControlFlow",          UAFRelationshipDTO.REL_CONTROL_FLOW);
        RELATION_TYPE_MAP.put("ObjectFlow",           UAFRelationshipDTO.REL_FLOWS_TO);
        RELATION_TYPE_MAP.put("Usage",                UAFRelationshipDTO.REL_DEPENDS_ON);
        RELATION_TYPE_MAP.put("InterfaceRealization", UAFRelationshipDTO.REL_IMPLEMENTS);
        RELATION_TYPE_MAP.put("Allocation",           UAFRelationshipDTO.REL_ALLOCATED_TO);
        RELATION_TYPE_MAP.put("Trace",                UAFRelationshipDTO.REL_TRACES_TO);
        RELATION_TYPE_MAP.put("Refine",               UAFRelationshipDTO.REL_REFINES);
        RELATION_TYPE_MAP.put("Satisfy",              UAFRelationshipDTO.REL_SATISFIES);
        RELATION_TYPE_MAP.put("Derive",               UAFRelationshipDTO.REL_INFLUENCES);
        RELATION_TYPE_MAP.put("ComponentRealization", UAFRelationshipDTO.REL_REALISES);

        RELATIONSHIP_STEREOTYPE_MAP.put("Exhibits",      UAFRelationshipDTO.REL_EXHIBITS);
        RELATIONSHIP_STEREOTYPE_MAP.put("Refines",       UAFRelationshipDTO.REL_REFINES);
        RELATIONSHIP_STEREOTYPE_MAP.put("Satisfies",     UAFRelationshipDTO.REL_SATISFIES);
        RELATIONSHIP_STEREOTYPE_MAP.put("Exposes",       UAFRelationshipDTO.REL_EXPOSES);
        RELATIONSHIP_STEREOTYPE_MAP.put("Provides",      UAFRelationshipDTO.REL_PROVIDES);
        // UAF relationship-bearing stereotypes (#75 RC #3). These names also live in the
        // element registry for the rare case they're applied to a Class, but when MSOSA
        // applies them to a UML InformationFlow / Association / Connector the element
        // registry would silently drop the edge — they must appear here too.
        RELATIONSHIP_STEREOTYPE_MAP.put("OperationalExchange", UAFRelationshipDTO.REL_INFORMATION_FLOW);
        RELATIONSHIP_STEREOTYPE_MAP.put("ResourceInteraction", UAFRelationshipDTO.REL_CONNECTED_TO);
        RELATIONSHIP_STEREOTYPE_MAP.put("NeedLine",            UAFRelationshipDTO.REL_INFORMATION_FLOW);
        // SysML relationship stereotypes
        RELATIONSHIP_STEREOTYPE_MAP.put("Allocate",      UAFRelationshipDTO.REL_ALLOCATED_TO);
        RELATIONSHIP_STEREOTYPE_MAP.put("DeriveReqt",    UAFRelationshipDTO.REL_INFLUENCES);
        RELATIONSHIP_STEREOTYPE_MAP.put("Copy",          UAFRelationshipDTO.REL_TRACES_TO);
        // BPMN relationship stereotypes
        RELATIONSHIP_STEREOTYPE_MAP.put("SequenceFlow",  UAFRelationshipDTO.REL_SEQUENCE_FLOW);
        RELATIONSHIP_STEREOTYPE_MAP.put("MessageFlow",   UAFRelationshipDTO.REL_MESSAGE_FLOW);
    }

    private final Project project;
    private final String modelFileName;

    // diagram membership: elementId → list of diagram names
    private final Map<String, List<String>> diagramIndex = new HashMap<>();
    private final Map<String, String> diagramIdIndex = new HashMap<>();

    private final List<UAFElementDTO>      elements          = new ArrayList<>();
    private final List<UAFRelationshipDTO> relationships     = new ArrayList<>();
    private final Set<String>              visitedIds        = new HashSet<>();
    private final Map<String, Integer>     unmatchedStereos  = new LinkedHashMap<>();
    private boolean traversed = false;

    public UAFModelTraverser(Project project) {
        this.project       = project;
        this.modelFileName = project.getName();
    }

    public String getSystemModelId()   { return modelFileName; }
    public String getSystemModelName() { return modelFileName; }

    public List<UAFElementDTO> getElements() {
        ensureTraversed();
        return Collections.unmodifiableList(elements);
    }

    public List<UAFRelationshipDTO> getRelationships() {
        ensureTraversed();
        return Collections.unmodifiableList(relationships);
    }

    /**
     * Stereotype names that appeared on elements in the model but did not resolve
     * to any {@link UAFStereotypeRegistry} entry — neither directly nor via their
     * general chain. Used by the export summary dialog to surface profile drift.
     *
     * Key: stereotype name. Value: how many distinct elements carried it.
     */
    public Map<String, Integer> getUnmatchedStereotypes() {
        ensureTraversed();
        return Collections.unmodifiableMap(unmatchedStereos);
    }

    // -------------------------------------------------------------------------

    private void ensureTraversed() {
        if (!traversed) {
            buildDiagramIndex();
            processElement(project.getPrimaryModel(), "");
            traversed = true;
            LOG.info(String.format("UAFModelTraverser: %d elements, %d relationships, %d unmatched stereotypes",
                elements.size(), relationships.size(), unmatchedStereos.size()));
        }
    }

    private void buildDiagramIndex() {
        for (DiagramPresentationElement diagram : project.getDiagrams()) {
            String dName = diagram.getName();
            String dId   = diagram.getID();
            for (Element el : diagram.getUsedModelElements()) {
                String elId = el.getID() != null ? el.getID() : el.toString();
                diagramIndex.computeIfAbsent(elId, k -> new ArrayList<>()).add(dName);
                diagramIdIndex.putIfAbsent(elId, dId);
            }
        }
    }

    private void processElement(Element element, String parentQName) {
        String id = safeId(element);
        if (!visitedIds.add(id)) return;  // cycle / multi-owner guard

        StereotypeMatch matched = selectStereotype(element);

        if (matched != null) {
            String name     = element instanceof NamedElement
                                ? ((NamedElement) element).getName() : "";
            String qname    = qualifiedName(element, parentQName);
            String diagId   = diagramIdIndex.getOrDefault(id, "");
            String diagName = "";
            List<String> diagNames = diagramIndex.get(id);
            if (diagNames != null && !diagNames.isEmpty()) {
                diagName = String.join("; ", diagNames);
            }

            String docs = ModelHelper.getComment(element);

            UAFElementDTO.Builder eb = UAFElementDTO.builder(id, name != null ? name : "", matched.stereotype.getName())
                .qualifiedName(qname)
                .neo4jLabel(matched.info.neo4jLabel)
                .domain(matched.info.domain != null ? matched.info.domain.name() : "NONE")
                .language(matched.info.language)
                .packageName(parentQName)
                .diagramId(diagId)
                .diagramName(diagName)
                .documentation(docs != null ? docs : "")
                .modelFileName(modelFileName);

            extractTaggedValues(element, matched.stereotype, eb);
            extractOwnedAttributes(element, eb);

            elements.add(eb.build());

            extractRelationships(element, matched.info);
        }

        // Descend into containers — Packages always, Classifiers (Block, Class, Activity,
        // StructuredClassifier, etc.) so internal block diagram content and activity actions
        // get visited. Per #75 RC #2, restricting descent to Package was the largest source
        // of missing operational/resource instances.
        if (shouldDescend(element)) {
            String qname = qualifiedName(element, parentQName);
            for (Element owned : element.getOwnedElement()) {
                processElement(owned, qname);
            }
        }
    }

    // ── Stereotype selection ──────────────────────────────────────────────────

    /**
     * Pick the best-matching registry entry for {@code element}, or {@code null} if
     * nothing matches. Per #75 RC #1/#5, this:
     *   1. Walks each directly-applied stereotype to find the closest registered
     *      ancestor (handles custom stereotypes that inherit from registered ones).
     *   2. Ranks candidates by language (UAF > BPMN > SysML).
     *   3. Breaks ties within a language by preferring the most specific
     *      (not-an-ancestor-of-another) candidate.
     *   4. Records direct stereotypes that produced no candidate at all in
     *      {@link #unmatchedStereos} for the export summary.
     */
    private StereotypeMatch selectStereotype(Element element) {
        List<Stereotype> applied = StereotypesHelper.getStereotypes(element);
        if (applied.isEmpty()) return null;

        List<StereotypeMatch> candidates = new ArrayList<>();
        for (Stereotype s : applied) {
            StereotypeMatch m = findRegisteredAncestor(s);
            if (m != null) {
                candidates.add(m);
            } else if (s.getName() != null && !s.getName().isEmpty()) {
                unmatchedStereos.merge(s.getName(), 1, Integer::sum);
            }
        }
        if (candidates.isEmpty()) return null;

        // Best language rank wins
        int bestRank = Integer.MAX_VALUE;
        for (StereotypeMatch c : candidates) {
            int r = languageRank(c.info.language);
            if (r < bestRank) bestRank = r;
        }
        List<StereotypeMatch> sameLang = new ArrayList<>();
        for (StereotypeMatch c : candidates) {
            if (languageRank(c.info.language) == bestRank) sameLang.add(c);
        }
        if (sameLang.size() == 1) return sameLang.get(0);

        // Tie-break: prefer the one that is not an ancestor of any other candidate
        for (StereotypeMatch c : sameLang) {
            boolean isAncestorOfAnother = false;
            for (StereotypeMatch other : sameLang) {
                if (other == c) continue;
                if (isAncestor(c.stereotype, other.stereotype)) {
                    isAncestorOfAnother = true;
                    break;
                }
            }
            if (!isAncestorOfAnother) return c;
        }
        // Cycle in the stereotype hierarchy or pure tie — fall back to the first
        return sameLang.get(0);
    }

    /**
     * Walk {@code s} and its general chain (BFS); return the first match in the
     * UAF stereotype registry, or {@code null} if none exists. Direct match wins
     * over ancestor match because BFS visits {@code s} first.
     */
    private static StereotypeMatch findRegisteredAncestor(Stereotype s) {
        Deque<Stereotype> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(s);
        while (!queue.isEmpty()) {
            Stereotype cur = queue.poll();
            String curName = cur.getName();
            if (curName == null || !seen.add(curName)) continue;
            Optional<UAFStereotypeRegistry.StereotypeInfo> info =
                UAFStereotypeRegistry.get(curName);
            if (info.isPresent()) {
                return new StereotypeMatch(cur, info.get());
            }
            for (Classifier general : cur.getGeneral()) {
                if (general instanceof Stereotype) {
                    queue.add((Stereotype) general);
                }
            }
        }
        return null;
    }

    /** True if {@code maybeAncestor} appears in {@code s}'s general chain. */
    private static boolean isAncestor(Stereotype maybeAncestor, Stereotype s) {
        if (maybeAncestor == s) return false;
        String ancestorName = maybeAncestor.getName();
        if (ancestorName == null) return false;
        Deque<Stereotype> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        for (Classifier general : s.getGeneral()) {
            if (general instanceof Stereotype) queue.add((Stereotype) general);
        }
        while (!queue.isEmpty()) {
            Stereotype cur = queue.poll();
            String n = cur.getName();
            if (n == null || !seen.add(n)) continue;
            if (ancestorName.equals(n)) return true;
            for (Classifier general : cur.getGeneral()) {
                if (general instanceof Stereotype) queue.add((Stereotype) general);
            }
        }
        return false;
    }

    static int languageRank(String language) {
        Integer r = LANGUAGE_RANK.get(language);
        return r != null ? r : Integer.MAX_VALUE;
    }

    /** True if descending into {@code e}'s {@code getOwnedElement()} is worthwhile. */
    private static boolean shouldDescend(Element e) {
        return e instanceof Package || e instanceof Classifier;
    }

    // ── Attribute / tag-value extraction ──────────────────────────────────────

    private void extractTaggedValues(Element element, Stereotype stereo,
                                     UAFElementDTO.Builder builder) {
        try {
            for (com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property prop
                    : StereotypesHelper.getPropertiesWithDerivedOrdered(stereo)) {
                String tag = prop.getName();
                Object val = StereotypesHelper.getTaggedValue(element, stereo, tag);
                if (val instanceof Collection) {
                    StringJoiner sj = new StringJoiner(", ");
                    for (Object v : (Collection<?>) val) {
                        if (v instanceof NamedElement) sj.add(((NamedElement) v).getName());
                        else if (v != null) sj.add(v.toString());
                    }
                    builder.taggedValue(tag, sj.toString());
                } else if (val != null) {
                    builder.taggedValue(tag, val.toString());
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to extract tagged values for " + safeId(element) + ": " + e.getMessage());
        }
    }

    private void extractOwnedAttributes(Element element, UAFElementDTO.Builder builder) {
        if (!(element instanceof Classifier)) return;
        try {
            Classifier cls = (Classifier) element;
            for (com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property attr : cls.getAttribute()) {
                String attrName = attr.getName();
                if (attrName == null || attrName.isEmpty()) continue;
                com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Type attrType = attr.getType();
                String typeName = (attrType != null && attrType.getName() != null)
                                  ? attrType.getName() : "";
                builder.taggedValue("attr_" + attrName, typeName);
                int lower = attr.getLower();
                int upper = attr.getUpper();
                String mult = (upper == -1)
                    ? lower + "..*"
                    : (lower == upper ? String.valueOf(lower) : lower + ".." + upper);
                if (!"1".equals(mult)) {
                    builder.taggedValue("attr_" + attrName + "_mult", mult);
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to extract owned attributes for " + safeId(element) + ": " + e.getMessage());
        }
    }

    private void extractRelationships(Element element, UAFStereotypeRegistry.StereotypeInfo srcInfo) {
        String srcId = safeId(element);

        for (com.nomagic.uml2.ext.magicdraw.classes.mdkernel.DirectedRelationship rel
                : element.get_directedRelationshipOfSource()) {

            String metaclass = rel.getClass().getSimpleName();
            String neo4jType = RELATION_TYPE_MAP.getOrDefault(metaclass, UAFRelationshipDTO.REL_DEPENDENCY);

            // Override base rel type with a language-specific relationship stereotype if present.
            // RELATIONSHIP_STEREOTYPE_MAP is checked first — these stereotypes are applied to
            // UML/SysML/BPMN relationship elements (not blocks/classes/tasks) and must never create nodes.
            List<Stereotype> relStereos = StereotypesHelper.getStereotypes(rel);
            for (Stereotype rs : relStereos) {
                String fromRelMap = RELATIONSHIP_STEREOTYPE_MAP.get(rs.getName());
                if (fromRelMap != null) {
                    neo4jType = fromRelMap;
                    break;
                }
                Optional<UAFStereotypeRegistry.StereotypeInfo> ri = UAFStereotypeRegistry.get(rs.getName());
                if (ri.isPresent()) {
                    neo4jType = ri.get().neo4jLabel.toUpperCase().replace(" ", "_");
                    break;
                }
            }

            for (Element target : rel.getTarget()) {
                String targetId = safeId(target);
                String relName  = rel instanceof NamedElement
                                    ? ((NamedElement) rel).getName() : "";

                relationships.add(
                    UAFRelationshipDTO.builder(safeId(rel), srcId, targetId, neo4jType)
                        .uafType(metaclass)
                        .name(relName != null ? relName : "")
                        .domain(srcInfo.domain != null ? srcInfo.domain.name() : "NONE")
                        .language(srcInfo.language)
                        .build()
                );
            }
        }
    }

    // -------------------------------------------------------------------------

    private static String safeId(Element e) {
        String id = e.getID();
        return (id != null && !id.isEmpty()) ? id : Integer.toHexString(System.identityHashCode(e));
    }

    private static String qualifiedName(Element e, String parentQName) {
        String name = e instanceof NamedElement ? ((NamedElement) e).getName() : "";
        if (name == null) name = "";
        return parentQName.isEmpty() ? name : parentQName + "::" + name;
    }

    /** Bundle of stereotype + its registry info — used to pick the best match for an element. */
    private static final class StereotypeMatch {
        final Stereotype stereotype;
        final UAFStereotypeRegistry.StereotypeInfo info;
        StereotypeMatch(Stereotype s, UAFStereotypeRegistry.StereotypeInfo i) {
            this.stereotype = s;
            this.info       = i;
        }
    }
}
