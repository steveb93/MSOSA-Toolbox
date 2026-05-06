package com.uaf.neo4j.plugin.neo4j;

import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.model.UAFRelationshipDTO;

import org.neo4j.driver.*;

import java.util.*;
import java.util.logging.Logger;

/**
 * Manages the Neo4j Bolt driver lifecycle and performs batched, idempotent
 * writes of UAF nodes, relationships, and metamodel links.
 *
 * Implements AutoCloseable — use in a try-with-resources block.
 */
public class Neo4jExportService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Neo4jExportService.class.getName());

    private final String uri;
    private final String user;
    private final String password;
    private final String database;
    private final int    batchSize;

    private Driver driver;
    private final ExportResult result = new ExportResult();

    public Neo4jExportService(Properties config) {
        this.uri      = config.getProperty("neo4j.uri",      "bolt://localhost:7687");
        this.user     = config.getProperty("neo4j.user",     "neo4j");
        this.password = config.getProperty("neo4j.password", "Password123");
        this.database = config.getProperty("neo4j.database", "neo4j");
        this.batchSize = Integer.parseInt(
            config.getProperty("neo4j.batch.size", "500"));
    }

    /** Opens the driver and verifies connectivity. Throws if connection fails. */
    public void init() {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password),
            Config.builder()
                .withMaxConnectionPoolSize(
                    Integer.parseInt(System.getProperty("neo4j.max.connections", "10")))
                .build());
        driver.verifyConnectivity();
        LOG.info("Neo4jExportService: connected to " + uri);
    }

    // -------------------------------------------------------------------------

    public void exportNodes(List<UAFElementDTO> elements) {
        exportNodes(elements, true);
    }

    public void exportNodes(List<UAFElementDTO> elements, boolean includeTaggedValues) {
        int total = elements.size();
        for (int i = 0; i < total; i += batchSize) {
            List<UAFElementDTO> batch = elements.subList(i, Math.min(i + batchSize, total));
            try (Session session = session()) {
                session.writeTransaction(tx -> {
                    for (UAFElementDTO dto : batch) {
                        tx.run(Neo4jCypherBuilder.nodeMergeCypher(dto),
                               Neo4jCypherBuilder.nodeParams(dto, includeTaggedValues));
                    }
                    return null;
                });
                result.nodesWritten += batch.size();
            } catch (Exception e) {
                LOG.warning("Node batch failed at index " + i + ": " + e.getMessage());
                result.errors.add("Node batch [" + i + "–" + (i + batch.size()) + "]: " + e.getMessage());
            }
        }
    }

    public void exportRelationships(List<UAFRelationshipDTO> rels) {
        int total = rels.size();
        for (int i = 0; i < total; i += batchSize) {
            List<UAFRelationshipDTO> batch = rels.subList(i, Math.min(i + batchSize, total));
            try (Session session = session()) {
                session.writeTransaction(tx -> {
                    for (UAFRelationshipDTO dto : batch) {
                        tx.run(Neo4jCypherBuilder.relationshipMergeCypher(dto),
                               Neo4jCypherBuilder.relationshipParams(dto));
                    }
                    return null;
                });
                result.relationshipsWritten += batch.size();
            } catch (Exception e) {
                LOG.warning("Relationship batch failed at index " + i + ": " + e.getMessage());
                result.errors.add("Rel batch [" + i + "–" + (i + batch.size()) + "]: " + e.getMessage());
            }
        }
    }

    /** MERGEs the :SystemModel node for the project being exported. */
    public void exportSystemModel(String id, String name) {
        try (Session session = session()) {
            session.writeTransaction(tx -> {
                tx.run(Neo4jCypherBuilder.SYSTEM_MODEL_MERGE_CYPHER,
                       Neo4jCypherBuilder.systemModelParams(id, name));
                return null;
            });
        } catch (Exception e) {
            LOG.warning("SystemModel node write failed: " + e.getMessage());
            result.errors.add("SystemModel: " + e.getMessage());
        }
    }

    /** MERGEs :DEFINES relationships from the SystemModel to each exported element. */
    public void exportDefinesLinks(String systemModelId, List<UAFElementDTO> elements) {
        int total = elements.size();
        for (int i = 0; i < total; i += batchSize) {
            List<UAFElementDTO> batch = elements.subList(i, Math.min(i + batchSize, total));
            try (Session session = session()) {
                session.writeTransaction(tx -> {
                    for (UAFElementDTO dto : batch) {
                        tx.run(Neo4jCypherBuilder.DEFINES_CYPHER,
                               Neo4jCypherBuilder.definesParams(systemModelId, dto.id));
                    }
                    return null;
                });
                result.definesLinksWritten += batch.size();
            } catch (Exception e) {
                LOG.warning("DEFINES batch failed at index " + i + ": " + e.getMessage());
                result.errors.add("Defines batch [" + i + "–" + (i + batch.size()) + "]: " + e.getMessage());
            }
        }
    }

    /**
     * Links each UAF element node to the corresponding :Stereotype node that
     * already exists in the domain metamodel graph.
     */
    public void exportInstanceOfLinks(List<UAFElementDTO> elements) {
        int total = elements.size();
        for (int i = 0; i < total; i += batchSize) {
            List<UAFElementDTO> batch = elements.subList(i, Math.min(i + batchSize, total));
            try (Session session = session()) {
                session.writeTransaction(tx -> {
                    for (UAFElementDTO dto : batch) {
                        tx.run(Neo4jCypherBuilder.INSTANCE_OF_CYPHER,
                               Neo4jCypherBuilder.instanceOfParams(dto));
                    }
                    return null;
                });
                result.instanceLinksWritten += batch.size();
            } catch (Exception e) {
                LOG.warning("INSTANCE_OF batch failed at index " + i + ": " + e.getMessage());
                result.errors.add("InstanceOf batch [" + i + "–" + (i + batch.size()) + "]: " + e.getMessage());
            }
        }
    }

    /**
     * Fetches a summary of every :UAFElement node from Neo4j for the Graph Inspector.
     * Returns core properties only (no tv_* tagged values) for performance.
     */
    public List<Map<String, Object>> fetchAllUAFElements() {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Session session = session()) {
            session.readTransaction(tx -> {
                org.neo4j.driver.Result res = tx.run(
                    "MATCH (n:UAFElement) " +
                    "RETURN n.id AS id, n.name AS name, n.stereotype AS stereotype, " +
                    "n.domain AS domain, n.packageName AS packageName, " +
                    "n.qualifiedName AS qualifiedName, n.documentation AS documentation " +
                    "ORDER BY n.domain, n.name LIMIT 10000"
                );
                while (res.hasNext()) {
                    org.neo4j.driver.Record rec = res.next();
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String key : rec.keys()) {
                        org.neo4j.driver.Value v = rec.get(key);
                        row.put(key, v.isNull() ? "" : v.asString());
                    }
                    out.add(row);
                }
                return null;
            });
        } catch (Exception e) {
            LOG.warning("fetchAllUAFElements failed: " + e.getMessage());
        }
        return out;
    }

    /** Test-only: verifies that the connection is alive without writing. */
    public boolean testConnection() {
        try {
            driver.verifyConnectivity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public ExportResult getResult() {
        return result;
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }

    // -------------------------------------------------------------------------

    private Session session() {
        return driver.session(SessionConfig.forDatabase(database));
    }

    // -------------------------------------------------------------------------

    public static final class ExportResult {
        public int nodesWritten        = 0;
        public int relationshipsWritten = 0;
        public int instanceLinksWritten = 0;
        public int definesLinksWritten  = 0;
        public final List<String> errors = new ArrayList<>();

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}
