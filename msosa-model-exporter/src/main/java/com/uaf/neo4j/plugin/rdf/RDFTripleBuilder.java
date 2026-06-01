package com.uaf.neo4j.plugin.rdf;

import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.model.UAFRelationshipDTO;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Translates {@link UAFElementDTO} and {@link UAFRelationshipDTO} into RDF triples,
 * adding them to a caller-owned Jena {@link Model}.
 *
 * Mirrors the static-method shape of {@code Neo4jCypherBuilder} so the two emitters
 * are visually parallel.
 *
 * IRI conventions are kept byte-identical to {@code ontology/codegen/dump_to_rdf.py}
 * so SPARQL queries written against the Python-generated dump still match the output
 * of this emitter. If you change a namespace or naming rule here, change it there too.
 */
public final class RDFTripleBuilder {

    /** UAF class namespace. Used for UAF stereotypes, predicate IRIs, and core data properties. */
    public static final String NS_UAF     = "http://msosa-toolbox.local/uaf#";
    /** SysML 1.6 class namespace. */
    public static final String NS_SYSML   = "http://msosa-toolbox.local/sysml#";
    /** BPMN 2.0 class namespace. */
    public static final String NS_BPMN    = "http://msosa-toolbox.local/bpmn#";
    /** Instance-individual namespace. */
    public static final String NS_INST    = "http://msosa-toolbox.local/uaf/instance#";
    /** Tagged-value property namespace. */
    public static final String NS_TAG     = "http://msosa-toolbox.local/uaf/tag#";
    /**
     * Analytics outputs written back to LPG nodes by Neo4j GDS algorithms
     * (see {@code cypher/gds-cookbook.cypher} §6). Kept distinct from
     * {@link #NS_TAG} (modeller tagged values) and {@code uafprop:} (T-Box
     * annotation properties) so SPARQL consumers can tell algorithm-derived
     * data from authored data. The plugin's MSOSA-side path will normally
     * have nothing to emit here — GDS results only exist after a Neo4j
     * write-back, so the Python {@code dump_to_rdf.py} script is the
     * canonical path. The helper is mirrored on both emitters so a future
     * post-export Bolt enrichment can use it without divergence.
     */
    public static final String NS_GDS     = "http://msosa-toolbox.local/uaf/gds#";

    /** Element / instance core properties already covered by typed RDF triples — skip when flattening tagged values. */
    private static final Pattern UNSAFE_ID_CHAR = Pattern.compile("[^A-Za-z0-9_\\-]");
    /** Mirrors {@code GDS_PROP} in {@code dump_to_rdf.py}. {@code gdsPagerank} → {@code uafgds:pagerank}. */
    private static final Pattern GDS_PROP_KEY = Pattern.compile("^gds([A-Z][A-Za-z0-9]*)$");

    private RDFTripleBuilder() {}

    // ── IRI builders ──────────────────────────────────────────────────────────

    /** {@code id} → {@code uafinst:<sanitised-id>}. Sanitisation matches the Python dump script. */
    public static Resource instanceIri(Model model, String id) {
        return model.createResource(NS_INST + sanitiseId(id));
    }

    /** {@code (Capability, "UAF")} → {@code uaf:Capability}; {@code (Block, "SysML")} → {@code sysml:Block}. */
    public static Resource classIri(Model model, String label, String language) {
        return model.createResource(namespaceForLanguage(language) + label);
    }

    /** {@code "PERFORMS"} → {@code uaf:performs}; {@code "INFORMATION_FLOW"} → {@code uaf:informationFlow}. */
    public static Property predicateIri(Model model, String neo4jType) {
        return model.createProperty(NS_UAF + toCamelCase(neo4jType));
    }

    /** {@code "tv_nationality"} → {@code uaftv:nationality}; strips the {@code tv_} prefix then sanitises. */
    public static Property tagPropertyIri(Model model, String tagKey) {
        String key = tagKey.startsWith("tv_") ? tagKey.substring(3) : tagKey;
        return model.createProperty(NS_TAG + sanitiseId(key));
    }

    /**
     * {@code "gdsPagerank"} → {@code uafgds:pagerank}; returns {@code null} if
     * the key is not a GDS write-back property (must be lowercase {@code gds}
     * prefix followed by an uppercase letter). Mirrors {@code gds_property_uri}
     * in {@code dump_to_rdf.py}.
     */
    public static Property gdsPropertyIri(Model model, String key) {
        if (key == null) return null;
        java.util.regex.Matcher m = GDS_PROP_KEY.matcher(key);
        if (!m.matches()) return null;
        String suffix = m.group(1);
        String local  = Character.toLowerCase(suffix.charAt(0))
                      + (suffix.length() > 1 ? suffix.substring(1) : "");
        return model.createProperty(NS_GDS + local);
    }

    /**
     * Typed literal for a GDS-derived value. Doubles for PageRank/betweenness,
     * longs for WCC/Louvain components, booleans pass through, anything else
     * stringifies. Mirrors {@code gds_literal} in {@code dump_to_rdf.py}.
     */
    public static org.apache.jena.rdf.model.Literal gdsLiteral(Model model, Object value) {
        if (value instanceof Boolean) {
            return model.createTypedLiteral(((Boolean) value).booleanValue());
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return model.createTypedLiteral(((Number) value).longValue(), XSD.xlong.getURI());
        }
        if (value instanceof Float || value instanceof Double) {
            return model.createTypedLiteral(((Number) value).doubleValue(), XSD.xdouble.getURI());
        }
        return model.createTypedLiteral(String.valueOf(value), XSD.xstring.getURI());
    }

    // ── High-level emitters ───────────────────────────────────────────────────

    /**
     * Emit all triples for one element: rdf:type per label, rdfs:label, core data props,
     * and tagged-value props (when {@code includeTaggedValues}).
     */
    public static void addElement(Model model, UAFElementDTO dto, boolean includeTaggedValues) {
        if (dto.neo4jLabel == null || dto.neo4jLabel.isEmpty()) {
            return; // skip elements with no resolved class — mirrors Python guard
        }
        Resource iri = instanceIri(model, dto.id);
        model.add(iri, RDF.type, classIri(model, dto.neo4jLabel, dto.language));

        if (notBlank(dto.name)) {
            model.add(iri, RDFS.label, model.createTypedLiteral(dto.name, XSD.xstring.getURI()));
        }
        addDataProp(model, iri, "qualifiedName", dto.qualifiedName);
        addDataProp(model, iri, "documentation", dto.documentation);
        addDataProp(model, iri, "domain",        dto.domain);
        addDataProp(model, iri, "language",      dto.language);
        addDataProp(model, iri, "packageName",   dto.packageName);

        if (includeTaggedValues) {
            for (Map.Entry<String, Object> e : dto.taggedValues.entrySet()) {
                String key = e.getKey();
                Object value = e.getValue();
                if (value == null) continue;
                // Mirror Python: tv_* -> uaftv: (string), gds* -> uafgds: (typed).
                if (key.startsWith("tv_")) {
                    model.add(iri, tagPropertyIri(model, key),
                              model.createTypedLiteral(String.valueOf(value), XSD.xstring.getURI()));
                    continue;
                }
                Property gdsProp = gdsPropertyIri(model, key);
                if (gdsProp != null) {
                    model.add(iri, gdsProp, gdsLiteral(model, value));
                }
            }
        }
    }

    /** Emit {@code src uaf:predicate tgt} for one relationship. INSTANCE_OF is skipped (covered by rdf:type). */
    public static void addRelationship(Model model, UAFRelationshipDTO dto) {
        if (dto.sourceId == null || dto.targetId == null || dto.neo4jType == null) return;
        if (UAFRelationshipDTO.REL_INSTANCE_OF.equals(dto.neo4jType)) return;
        model.add(instanceIri(model, dto.sourceId),
                  predicateIri(model, dto.neo4jType),
                  instanceIri(model, dto.targetId));
    }

    /** Bind the canonical prefixes — call once after {@code ModelFactory.createDefaultModel()}. */
    public static void bindPrefixes(Model model) {
        model.setNsPrefix("uaf",     NS_UAF);
        model.setNsPrefix("sysml",   NS_SYSML);
        model.setNsPrefix("bpmn",    NS_BPMN);
        model.setNsPrefix("uafinst", NS_INST);
        model.setNsPrefix("uaftv",   NS_TAG);
        model.setNsPrefix("uafgds",  NS_GDS);
        model.setNsPrefix("rdfs",    RDFS.getURI());
        model.setNsPrefix("xsd",     XSD.getURI());
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    static String sanitiseId(String id) {
        return UNSAFE_ID_CHAR.matcher(id == null ? "" : id).replaceAll("_");
    }

    static String namespaceForLanguage(String language) {
        if (language == null) return NS_UAF;
        switch (language) {
            case "SysML": return NS_SYSML;
            case "BPMN":  return NS_BPMN;
            default:      return NS_UAF;
        }
    }

    /** {@code PERFORMS} → {@code performs}; {@code INFORMATION_FLOW} → {@code informationFlow}. */
    static String toCamelCase(String snakeUpper) {
        if (snakeUpper == null || snakeUpper.isEmpty()) return "";
        String[] parts = snakeUpper.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) sb.append(parts[i].substring(1));
        }
        return sb.toString();
    }

    private static void addDataProp(Model model, Resource iri, String localName, String value) {
        if (notBlank(value)) {
            model.add(iri, model.createProperty(NS_UAF + localName),
                      model.createTypedLiteral(value, XSD.xstring.getURI()));
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isEmpty();
    }
}
