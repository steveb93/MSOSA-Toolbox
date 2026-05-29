package com.uaf.neo4j.plugin.preview;

import com.uaf.neo4j.plugin.neo4j.Neo4jExportService.NeighbourhoodResult;
import com.uaf.neo4j.plugin.ui.GraphInspectorDialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Drives {@link GraphInspectorDialog} from canned sample data so the table,
 * property inspector and neighbourhood graph can be rendered without a live
 * Neo4j. The two data-fetch seams are overridden; the only remaining MSOSA
 * coupling (the "Locate in MSOSA" button) stays disabled because the project
 * is null.
 */
public class PreviewGraphInspectorDialog extends GraphInspectorDialog {

    public PreviewGraphInspectorDialog(Properties config) {
        super(config, null); // null project — sample data via the overrides below
    }

    // Static so it is safe to reference from methods invoked by the superclass
    // constructor (subclass instance fields are not initialised at that point).
    private static final List<Map<String, Object>> SAMPLE = buildSample();

    @Override
    protected List<Map<String, Object>> fetchAllNodes() {
        return SAMPLE;
    }

    @Override
    protected NeighbourhoodResult fetchNeighbourhood(String nodeId) {
        Map<String, Object> centre = byId(nodeId);
        if (centre == null) centre = SAMPLE.get(0);
        String cid = String.valueOf(centre.get("id"));

        // Centre + a handful of neighbours drawn from the sample set, with edges
        // that radiate from the centre using representative UAF relationship types.
        String[][] spokes = {
            {"op-1",  "PERFORMS"},
            {"op-2",  "REALISES"},
            {"res-1", "ASSIGNED_TO"},
            {"svc-1", "EXPOSES"},
            {"cap-2", "COMPOSED_OF"},
        };

        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(graphNode(centre));
        List<Map<String, Object>> rels = new ArrayList<>();
        for (String[] spoke : spokes) {
            Map<String, Object> nb = byId(spoke[0]);
            if (nb == null || spoke[0].equals(cid)) continue;
            nodes.add(graphNode(nb));
            rels.add(edge(cid, spoke[0], spoke[1]));
        }
        return new NeighbourhoodResult(nodes, rels);
    }

    // ── Sample data ─────────────────────────────────────────────────────────────

    private static List<Map<String, Object>> buildSample() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(node("cap-1",  "Provide Maritime Security",   "Capability",           "STRATEGIC",   "UAF",   "Strategic::Capabilities"));
        rows.add(node("cap-2",  "Conduct Surveillance",        "Capability",           "STRATEGIC",   "UAF",   "Strategic::Capabilities"));
        rows.add(node("cap-3",  "Command and Control",         "Capability",           "STRATEGIC",   "UAF",   "Strategic::Capabilities"));
        rows.add(node("vis-1",  "Secure Seas 2035",            "Vision",               "STRATEGIC",   "UAF",   "Strategic::Vision"));
        rows.add(node("op-1",   "Patrol Area of Interest",     "OperationalActivity",  "OPERATIONAL", "UAF",   "Operational::Activities"));
        rows.add(node("op-2",   "Detect Surface Contact",      "OperationalActivity",  "OPERATIONAL", "UAF",   "Operational::Activities"));
        rows.add(node("op-3",   "Maritime Patrol Node",        "OperationalPerformer", "OPERATIONAL", "UAF",   "Operational::Performers"));
        rows.add(node("op-4",   "Coastal Command Post",        "OperationalPerformer", "OPERATIONAL", "UAF",   "Operational::Performers"));
        rows.add(node("res-1",  "Offshore Patrol Vessel",      "ResourcePerformer",    "RESOURCE",    "UAF",   "Resources::Systems"));
        rows.add(node("res-2",  "Maritime Radar System",       "ResourcePerformer",    "RESOURCE",    "UAF",   "Resources::Systems"));
        rows.add(node("res-3",  "Tactical Data Link",          "ResourceConnector",    "RESOURCE",    "UAF",   "Resources::Connectors"));
        rows.add(node("svc-1",  "Track Reporting Service",     "ServiceSpecification",  "SERVICE",     "UAF",   "Services::Specifications"));
        rows.add(node("svc-2",  "Common Operating Picture",    "ServiceSpecification",  "SERVICE",     "UAF",   "Services::Specifications"));
        rows.add(node("per-1",  "Watch Officer",               "Post",                 "PERSONNEL",   "UAF",   "Personnel::Posts"));
        rows.add(node("sec-1",  "Access Control Policy",       "SecurityControl",      "SECURITY",    "UAF",   "Security::Controls"));
        rows.add(node("blk-1",  "Radar Subsystem",             "Block",                "RESOURCE",    "SysML", "Actual Resources::Blocks"));
        rows.add(node("blk-2",  "Navigation Subsystem",        "Block",                "RESOURCE",    "SysML", "Actual Resources::Blocks"));
        rows.add(node("bpm-1",  "Contact Handling Process",    "Process",              "OPERATIONAL", "BPMN",  "Metadata::Processes"));
        return rows;
    }

    private static Map<String, Object> byId(String id) {
        for (Map<String, Object> row : SAMPLE) {
            if (String.valueOf(row.get("id")).equals(id)) return row;
        }
        return null;
    }

    /** Row for the main table / property inspector (full property set). */
    private static Map<String, Object> node(String id, String name, String stereotype,
                                            String domain, String language, String pkg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("qualifiedName", pkg + "::" + name);
        m.put("stereotype", stereotype);
        m.put("domain", domain);
        m.put("language", language);
        m.put("packageName", pkg);
        m.put("documentation", "Sample documentation for " + name + ".");
        return m;
    }

    /** Reduced row for the neighbourhood graph (keys GraphPanel reads). */
    private static Map<String, Object> graphNode(Map<String, Object> full) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", full.get("id"));
        m.put("name", full.get("name"));
        m.put("stereotype", full.get("stereotype"));
        m.put("domain", full.get("domain"));
        m.put("language", full.get("language"));
        return m;
    }

    private static Map<String, Object> edge(String fromId, String toId, String relType) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fromId", fromId);
        m.put("toId", toId);
        m.put("relType", relType);
        return m;
    }
}
