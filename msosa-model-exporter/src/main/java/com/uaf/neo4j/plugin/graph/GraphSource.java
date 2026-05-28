package com.uaf.neo4j.plugin.graph;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over the backend that supplies nodes and neighbourhoods to the
 * Graph Inspector. Two implementations are planned:
 *
 * <ul>
 *   <li>{@code Neo4jGraphSource} — wraps the existing Bolt/Cypher logic from
 *       {@code GraphInspectorDialog} so today's behaviour is preserved.</li>
 *   <li>{@code FusekiGraphSource} — issues SPARQL {@code SELECT} for the table
 *       and {@code CONSTRUCT} (one-hop) for the neighbourhood. Honours an
 *       inference toggle so OWL FB-derived triples can be revealed.</li>
 * </ul>
 *
 * <p>The Inspect mode picks the active source from a radio toggle. Beyond that,
 * the table / property pane / JGraphX panel are agnostic.
 */
public interface GraphSource extends AutoCloseable {

    /** Short name for the status strip and inspector header. */
    String name();

    /** Probe the backend; returns true if it answered. */
    boolean ping();

    /** Rows for the main table — one map per node, keys are column names. */
    List<Map<String, Object>> fetchAllNodes();

    /**
     * One-hop neighbourhood around the given node id.
     * Implementations must populate both nodes (including the centre) and edges.
     */
    Neighbourhood fetchNeighbourhood(String nodeId);

    @Override void close();

    /** Carrier for a neighbourhood — kept dumb on purpose. */
    final class Neighbourhood {
        public final List<Map<String, Object>> nodes;
        public final List<Edge> edges;
        public Neighbourhood(List<Map<String, Object>> nodes, List<Edge> edges) {
            this.nodes = nodes;
            this.edges = edges;
        }
    }

    final class Edge {
        public final String sourceId;
        public final String targetId;
        public final String type;
        public Edge(String sourceId, String targetId, String type) {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.type = type;
        }
    }
}
