package com.uaf.neo4j.plugin.model;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.uml.symbols.DiagramPresentationElement;
import com.nomagic.uml2.ext.jmi.helpers.ModelHelper;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;

import java.util.*;
import java.util.logging.Logger;

/**
 * Walks the full MSOSA project model tree, identifies UAF-stereotyped elements,
 * extracts tagged values, and returns typed DTOs ready for Neo4j export.
 */
public class UAFModelTraverser {

    private static final Logger LOG = Logger.getLogger(UAFModelTraverser.class.getName());

    // Relationship metaclass names → Neo4j relationship type
    private static final Map<String, String> RELATION_TYPE_MAP = new LinkedHashMap<>();

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
    }

    private final Project project;
    private final String modelFileName;

    // diagram membership: elementId → list of diagram names
    private final Map<String, List<String>> diagramIndex = new HashMap<>();
    private final Map<String, String> diagramIdIndex = new HashMap<>();

    private final List<UAFElementDTO>      elements      = new ArrayList<>();
    private final List<UAFRelationshipDTO> relationships = new ArrayList<>();
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

    // -------------------------------------------------------------------------

    private void ensureTraversed() {
        if (!traversed) {
            buildDiagramIndex();
            traversePackage(project.getPrimaryModel(), "");
            traversed = true;
            LOG.info(String.format("UAFModelTraverser: %d elements, %d relationships",
                elements.size(), relationships.size()));
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

    private void traversePackage(Package pkg, String parentQName) {
        for (Element owned : pkg.getOwnedElement()) {
            processElement(owned, parentQName);
        }
    }

    private void processElement(Element element, String parentQName) {
        List<Stereotype> applied = StereotypesHelper.getStereotypes(element);
        if (applied.isEmpty()) {
            // Still recurse into packages/blocks even if not stereotyped
            if (element instanceof Package) {
                String qname = qualifiedName(element, parentQName);
                traversePackage((Package) element, qname);
            }
            return;
        }

        // Pick the first UAF-known stereotype
        Stereotype uafStereo = null;
        UAFStereotypeRegistry.StereotypeInfo info = null;
        for (Stereotype s : applied) {
            String sName = s.getName();
            Optional<UAFStereotypeRegistry.StereotypeInfo> found = UAFStereotypeRegistry.get(sName);
            if (found.isPresent()) {
                uafStereo = s;
                info      = found.get();
                break;
            }
        }

        if (uafStereo == null) {
            // Not a UAF element — still recurse
            if (element instanceof Package) {
                traversePackage((Package) element, qualifiedName(element, parentQName));
            }
            return;
        }

        String id       = safeId(element);
        String name     = element instanceof NamedElement
                            ? ((NamedElement) element).getName() : "";
        String qname    = qualifiedName(element, parentQName);
        String pkgName  = parentQName;
        String diagId   = diagramIdIndex.getOrDefault(id, "");
        String diagName = "";
        List<String> diagNames = diagramIndex.get(id);
        if (diagNames != null && !diagNames.isEmpty()) {
            diagName = String.join("; ", diagNames);
        }

        String docs = ModelHelper.getComment(element);

        UAFElementDTO.Builder eb = UAFElementDTO.builder(id, name != null ? name : "", uafStereo.getName())
            .qualifiedName(qname)
            .neo4jLabel(info.neo4jLabel)
            .domain(info.domain.name())
            .packageName(pkgName)
            .diagramId(diagId)
            .diagramName(diagName)
            .documentation(docs != null ? docs : "")
            .modelFileName(modelFileName);

        // Extract all tagged values for this stereotype
        extractTaggedValues(element, uafStereo, eb);

        // Extract owned UML class attributes (covers ResourceInformation / OperationalInformation
        // data properties and attributes inherited via ERD entity mappings)
        extractOwnedAttributes(element, eb);

        elements.add(eb.build());

        // Process relationships owned by this element
        extractRelationships(element, info);

        // Recurse
        if (element instanceof Package) {
            traversePackage((Package) element, qname);
        }
    }

    private void extractTaggedValues(Element element, Stereotype stereo,
                                     UAFElementDTO.Builder builder) {
        try {
            for (com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property prop
                    : StereotypesHelper.getPropertiesWithDerivedOrdered(stereo)) {
                String tag = prop.getName();
                Object val = StereotypesHelper.getTaggedValue(element, stereo, tag);
                if (val instanceof Collection) {
                    // Convert list values to comma-separated string
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
        if (!(element instanceof com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Classifier)) return;
        try {
            com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Classifier cls =
                (com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Classifier) element;
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

        // Directed relationships where this element is the source (2022x API)
        for (com.nomagic.uml2.ext.magicdraw.classes.mdkernel.DirectedRelationship rel
                : element.get_directedRelationshipOfSource()) {

            String metaclass = rel.getClass().getSimpleName();
            String neo4jType = RELATION_TYPE_MAP.getOrDefault(metaclass, UAFRelationshipDTO.REL_DEPENDENCY);

            // Determine UAF stereotype applied to relationship, if any
            List<Stereotype> relStereos = StereotypesHelper.getStereotypes(rel);
            for (Stereotype rs : relStereos) {
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
                        .domain(srcInfo.domain.name())
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
}
