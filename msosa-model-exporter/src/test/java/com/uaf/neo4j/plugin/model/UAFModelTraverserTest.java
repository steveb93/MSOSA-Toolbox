package com.uaf.neo4j.plugin.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static / map-level coverage for {@link UAFModelTraverser}. Full traversal
 * coverage requires a live MSOSA project and is gated on the manual regression
 * harness under {@code Test/queries/regression_traverser.cypher}; this file
 * pins the deterministic pieces.
 */
class UAFModelTraverserTest {

    // ── Stereotype-priority ordering (#75 RC #1) ──────────────────────────────

    @Test
    void languageRank_uafBeatsEverythingElse() {
        assertTrue(UAFModelTraverser.languageRank("UAF")
                 < UAFModelTraverser.languageRank("BPMN"));
        assertTrue(UAFModelTraverser.languageRank("UAF")
                 < UAFModelTraverser.languageRank("SysML"));
    }

    @Test
    void languageRank_bpmnBeatsSysml() {
        assertTrue(UAFModelTraverser.languageRank("BPMN")
                 < UAFModelTraverser.languageRank("SysML"));
    }

    @Test
    void languageRank_unknownLanguageRanksLast() {
        int unknown = UAFModelTraverser.languageRank("Klingon");
        assertTrue(unknown > UAFModelTraverser.languageRank("SysML"),
            "Unknown languages must never beat SysML");
        assertEquals(Integer.MAX_VALUE, unknown);
    }

    @Test
    void languageRank_nullLanguageRanksLast() {
        assertEquals(Integer.MAX_VALUE, UAFModelTraverser.languageRank(null));
    }

    // ── Relationship-stereotype map additions (#75 RC #3) ─────────────────────

    @Test
    void relationshipStereotypeMap_includesOperationalExchange() {
        assertEquals(UAFRelationshipDTO.REL_INFORMATION_FLOW,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("OperationalExchange"),
                     "OperationalExchange on a UML InformationFlow must produce an INFORMATION_FLOW edge, "
                     + "not be dropped as an un-mapped relationship stereotype");
    }

    @Test
    void relationshipStereotypeMap_includesResourceInteraction() {
        assertEquals(UAFRelationshipDTO.REL_CONNECTED_TO,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("ResourceInteraction"));
    }

    @Test
    void relationshipStereotypeMap_includesNeedLine() {
        assertEquals(UAFRelationshipDTO.REL_INFORMATION_FLOW,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("NeedLine"));
    }

    @Test
    void relationshipStereotypeMap_preservesExistingEntries() {
        // Sanity: the new additions must not have displaced the SysML / BPMN entries.
        assertNotNull(UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("Allocate"));
        assertNotNull(UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("SequenceFlow"));
        assertNotNull(UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("MessageFlow"));
    }

    @Test
    void relationshipStereotypeMap_allValuesAreKnownRelConstants() {
        // Each mapped value must be one of the REL_* constants. Stops a typo in the
        // map from emitting a relationship type Cypher does not know how to sanitise.
        java.util.Set<String> known = new java.util.HashSet<>();
        for (java.lang.reflect.Field f : UAFRelationshipDTO.class.getDeclaredFields()) {
            int mods = f.getModifiers();
            if (java.lang.reflect.Modifier.isPublic(mods)
                && java.lang.reflect.Modifier.isStatic(mods)
                && f.getName().startsWith("REL_")
                && f.getType() == String.class) {
                try { known.add((String) f.get(null)); }
                catch (IllegalAccessException ignored) { /* unreachable for public static */ }
            }
        }
        for (var e : UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.entrySet()) {
            assertTrue(known.contains(e.getValue()),
                "RELATIONSHIP_STEREOTYPE_MAP[" + e.getKey() + "] = " + e.getValue()
                + " is not a recognised REL_* constant");
        }
    }
}
