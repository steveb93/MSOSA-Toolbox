package com.uaf.neo4j.plugin.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

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

    // ── Data-flow REL_* constants (#76) ───────────────────────────────────────

    @Test
    void relConstants_includeDataInputAndDataOutput() {
        // BPMN DataInputAssociation / DataOutputAssociation must map to dedicated
        // Cypher relationship types so data artefacts are connected to the Tasks
        // that consume / produce them.
        assertEquals("DATA_INPUT",  UAFRelationshipDTO.REL_DATA_INPUT);
        assertEquals("DATA_OUTPUT", UAFRelationshipDTO.REL_DATA_OUTPUT);
    }

    @Test
    void relConstants_includeHasAttributeAndOfType() {
        // First-class ERD attribute representation (#76 design A) — the entity →
        // attribute and attribute → datatype edges live here.
        assertEquals("HAS_ATTRIBUTE", UAFRelationshipDTO.REL_HAS_ATTRIBUTE);
        assertEquals("OF_TYPE",       UAFRelationshipDTO.REL_OF_TYPE);
    }

    // ── Tier-1 #75 RC #6 relationship-stereotype map additions ────────────────

    @Test
    void relationshipStereotypeMap_includesImplementsAndCapabilityMappings() {
        // Stereotypes applied to UML relationship elements in iSCP that pre-#75-RC6
        // were silently dropped because the map didn't know them.
        assertEquals(UAFRelationshipDTO.REL_IMPLEMENTS,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("Implements"));
        assertEquals(UAFRelationshipDTO.REL_PERFORMS,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("IsCapableToPerform"));
        assertEquals(UAFRelationshipDTO.REL_PERFORMS,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("PerformsInContext"));
        assertEquals(UAFRelationshipDTO.REL_REALISES,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("MapsToCapability"));
    }

    @Test
    void relationshipStereotypeMap_includesDomainAssociations() {
        assertEquals(UAFRelationshipDTO.REL_FLOWS_TO,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("DataAssociation"));
        assertEquals(UAFRelationshipDTO.REL_ASSOCIATED_WITH,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("ServiceAssociation"));
        assertEquals(UAFRelationshipDTO.REL_ASSOCIATED_WITH,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("OperationalAssociation"));
        assertEquals(UAFRelationshipDTO.REL_ASSOCIATED_WITH,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("ResourceAssociation"));
    }

    // ── Module / attached-project traversal (#75 RC #4) ───────────────────────

    @Test
    void constructor_exposesAttachedModuleFlag() {
        // The two-arg constructor signature exists; a default-true single-arg
        // overload also exists. We can't instantiate without a MSOSA Project,
        // but reflection confirms the API surface so callers (ExportConfigDialog)
        // can opt out via the second arg.
        Constructor<?>[] ctors = UAFModelTraverser.class.getDeclaredConstructors();
        boolean foundSingleArg = Arrays.stream(ctors).anyMatch(c ->
            c.getParameterCount() == 1
            && c.getParameterTypes()[0].getName().equals("com.nomagic.magicdraw.core.Project"));
        boolean foundTwoArg = Arrays.stream(ctors).anyMatch(c ->
            c.getParameterCount() == 2
            && c.getParameterTypes()[0].getName().equals("com.nomagic.magicdraw.core.Project")
            && c.getParameterTypes()[1] == boolean.class);
        assertTrue(foundSingleArg, "Single-arg Project constructor must remain for back-compat");
        assertTrue(foundTwoArg,    "Two-arg (Project, boolean) constructor must be present for opt-out");
    }

    // ── Unnamed-typed-part name fallback (Role stereotypes on UML Properties) ─

    @Test
    void resolveName_emptyOwnName_fallsBackToType_forAnyTypeName() {
        // Role stereotypes (OperationalRole / ResourceRole / CapabilityRole /
        // ServiceRole / ProjectRole / ...) applied to a UML Property with no
        // name display as ":<Type>" in MSOSA but getName() returns "". The
        // fallback is purely a name-or-type passthrough — it must not be
        // anchored to any specific type name. Exercise several to make that
        // contract explicit.
        for (String typeName : new String[] {
                "Time", "OperationalPerformer", "ResourceArtifact",
                "Capability", "Service", "Person", "Vehicle",
                "Some Type With Spaces", "Type-With-Hyphens", "T"}) {
            assertEquals(typeName, UAFModelTraverser.resolveName("",   typeName),
                "Empty own-name must fall back to the type name (was: " + typeName + ")");
            assertEquals(typeName, UAFModelTraverser.resolveName(null, typeName),
                "Null own-name must fall back to the type name (was: " + typeName + ")");
        }
    }

    @Test
    void resolveName_keepsOwnNameWhenPresent_forAnyTypeName() {
        // Named typed parts (e.g. "Month:Time") keep their own name regardless
        // of what the type happens to be — the type is already preserved via
        // the OF_TYPE edge / Neo4j typeName tagged value.
        assertEquals("Month",   UAFModelTraverser.resolveName("Month",   "Time"));
        assertEquals("HQ",      UAFModelTraverser.resolveName("HQ",      "Organisation"));
        assertEquals("driver",  UAFModelTraverser.resolveName("driver",  "Person"));
        assertEquals("payload", UAFModelTraverser.resolveName("payload", "ResourceArtifact"));
    }

    @Test
    void resolveName_bothBlankReturnsEmptyString() {
        // Never returns null — downstream Cypher/RDF emitters branch on isEmpty().
        assertEquals("", UAFModelTraverser.resolveName(null, null));
        assertEquals("", UAFModelTraverser.resolveName("",   ""));
        assertEquals("", UAFModelTraverser.resolveName(null, ""));
        assertEquals("", UAFModelTraverser.resolveName("",   null));
    }

    @Test
    void traverseAttachedModulesField_isFinalAndBoolean() {
        // The flag stores once at construction time and is consulted by ensureTraversed.
        // Final + boolean keeps the contract simple — no toggle mid-traversal.
        Field field;
        try {
            field = UAFModelTraverser.class.getDeclaredField("traverseAttachedModules");
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Expected field traverseAttachedModules to exist", e);
        }
        assertEquals(boolean.class, field.getType(), "Field must be a primitive boolean");
        assertTrue(Modifier.isFinal(field.getModifiers()), "Field must be final");
    }
}
