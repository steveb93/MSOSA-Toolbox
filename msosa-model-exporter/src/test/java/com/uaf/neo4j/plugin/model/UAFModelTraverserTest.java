package com.uaf.neo4j.plugin.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void relationshipStereotypeMap_includesResourceExchange() {
        assertEquals(UAFRelationshipDTO.REL_INFORMATION_FLOW,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("ResourceExchange"),
                     "ResourceExchange on a UML InformationFlow/Connector between resource ports "
                     + "must produce an INFORMATION_FLOW edge, not become an orphan node with only "
                     + "DEFINES/INSTANCE_OF relationships");
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
        // Stereotypes applied to UML relationship elements in real-world UAF profiles
        // that pre-#75-RC6 were silently dropped because the map didn't know them.
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

    @Test
    void relationshipStereotypeMap_includesDominates() {
        // UAF Security domain dominance — applied to a UML Dependency between two
        // SecurityElements. The owl:TransitiveProperty axiom on uaf:dominates in
        // ontology/uaf-mvo-axioms.ttl closes the lattice in Fuseki automatically.
        assertEquals(UAFRelationshipDTO.REL_DOMINATES,
                     UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP.get("Dominates"));
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

    // ── Resource Internal Connectivity wiring (Connector ends, Property
    //    ownership, reference-typed stereotype properties) ────────────────────

    @Test
    void connectorEndExtractor_method_exists() {
        // Connectors (typically ResourceConnector / OperationalConnector) must
        // emit edges to their ownedEnd[].role and to their typing classifier,
        // otherwise the whole IBD-style internal-structure wiring is missing.
        // Method exists with the expected signature on the traverser.
        boolean found = Arrays.stream(UAFModelTraverser.class.getDeclaredMethods())
            .anyMatch(m -> m.getName().equals("extractConnectorEnds")
                        && m.getParameterCount() == 3);
        assertTrue(found, "extractConnectorEnds(Element, String, StereotypeInfo) must exist — "
                        + "ResourceConnector orphans depend on it");
    }

    @Test
    void propertyOwnershipExtractor_method_exists() {
        // UAF Roles (ResourceRole / ResourceInformationRole / OperationalRole / ...)
        // export as nodes but were previously not linked back to their owning
        // architecture. Method emits COMPOSED_OF from owner Classifier to the Property.
        boolean found = Arrays.stream(UAFModelTraverser.class.getDeclaredMethods())
            .anyMatch(m -> m.getName().equals("extractPropertyOwnership")
                        && m.getParameterCount() == 3);
        assertTrue(found, "extractPropertyOwnership(Element, String, StereotypeInfo) must exist — "
                        + "architecture→port navigability depends on it");
    }

    @Test
    void referenceEdgeEmitter_method_exists() {
        // Reference-typed stereotype properties (e.g. ResourceExchange.Source/
        // Target/conveyed) previously stringified into lossy tv_* values. Method
        // emits an ASSOCIATED_WITH edge with uafType=<Stereotype>.<property> so
        // the references are reachable via graph traversal.
        boolean found = Arrays.stream(UAFModelTraverser.class.getDeclaredMethods())
            .anyMatch(m -> m.getName().equals("emitReferenceEdge")
                        && m.getParameterCount() == 5);
        assertTrue(found, "emitReferenceEdge(srcId, target, stereoName, tag, info) must exist — "
                        + "ResourceExchange Source/Target/conveyed wiring depends on it");
    }

    @Test
    void referenceTargetResolver_method_exists() {
        // emitReferenceEdge previously handed the raw NamedElement straight to the
        // Cypher writer. The writer uses MATCH (not MERGE) on both endpoints, so
        // raw UML Property memberEnds — which are never themselves MERGE'd as UAF
        // nodes — caused the edge to be silently dropped. The resolver walks up
        // (Property.getType() → owner chain) to find a UAF-stereotyped element
        // before the edge is emitted.
        boolean found = Arrays.stream(UAFModelTraverser.class.getDeclaredMethods())
            .anyMatch(m -> m.getName().equals("resolveReferenceTarget")
                        && m.getParameterCount() == 1);
        assertTrue(found, "resolveReferenceTarget(NamedElement) must exist — "
                        + "ASSOCIATED_WITH edges silently drop without it");
    }

    @Test
    void registeredUafElementProbe_isStaticAndNonMutating() {
        // The probe used by resolveReferenceTarget must NOT go through
        // selectStereotype — that method mutates unmatchedStereos as a side effect
        // and would double-count every reference-edge probe as an unmatched
        // stereotype miss. Static-method contract pins the non-mutating helper.
        boolean found = Arrays.stream(UAFModelTraverser.class.getDeclaredMethods())
            .anyMatch(m -> m.getName().equals("isRegisteredUAFElement")
                        && m.getParameterCount() == 1
                        && Modifier.isStatic(m.getModifiers()));
        assertTrue(found, "isRegisteredUAFElement(Element) must exist as a static probe — "
                        + "selectStereotype mutates unmatchedStereos and cannot be used for probing");
    }

    @Test
    void internalWiringExtractors_useExpectedRelConstants() {
        // Pin the chosen rel types so an accidental change to one of these
        // breaks here, not silently in production.
        assertEquals("CONNECTED_TO", UAFRelationshipDTO.REL_CONNECTED_TO,
            "Connector ends emit CONNECTED_TO");
        assertEquals("OF_TYPE", UAFRelationshipDTO.REL_OF_TYPE,
            "Connector.type emits OF_TYPE");
        assertEquals("COMPOSED_OF", UAFRelationshipDTO.REL_COMPOSED_OF,
            "Property owner emits COMPOSED_OF");
        assertEquals("ASSOCIATED_WITH", UAFRelationshipDTO.REL_ASSOCIATED_WITH,
            "Reference-typed stereotype properties emit ASSOCIATED_WITH");
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

    // ── Fallback-aware ancestor resolution (bare-noun-ancestor class of bug) ──
    //
    // In real-world UAF profiles, operational-domain custom stereotypes have been
    // observed being assigned :Resource (RESOURCE domain) even though their
    // qualifiedName ended in `Operational Taxonomy::Internal Performer::<name>`.
    // Root cause: BFS in findRegisteredAncestor returned the FIRST registered
    // name it hit, so a custom op-side stereotype whose general chain passed
    // through `Resource` (which IS in the registry) returned `Resource` before
    // reaching the operational ancestor further up. The fix marks bare-noun
    // catch-alls as fallback so the BFS prefers a non-fallback ancestor at any
    // distance.
    //
    // resolveBestRegistered is the pure-function core of the new algorithm and
    // can be exercised here with synthetic stereotype hierarchies; full traversal
    // remains gated on the manual MSOSA regression harness.

    /** Build a minimal registry of {name → StereotypeInfo} for the algorithm tests. */
    private static Function<String, Optional<UAFStereotypeRegistry.StereotypeInfo>> registry(
            Map<String, UAFStereotypeRegistry.StereotypeInfo> map) {
        return name -> Optional.ofNullable(map.get(name));
    }

    /** Build a name → list-of-parents adapter from a map. Missing keys return empty. */
    private static Function<String, List<String>> parents(Map<String, List<String>> graph) {
        return name -> graph.getOrDefault(name, Collections.emptyList());
    }

    private static UAFStereotypeRegistry.StereotypeInfo concrete(String label,
                                                                  UAFStereotypeRegistry.Domain d) {
        return new UAFStereotypeRegistry.StereotypeInfo(label, d, "UAF", false);
    }

    private static UAFStereotypeRegistry.StereotypeInfo fallback(String label,
                                                                  UAFStereotypeRegistry.Domain d) {
        return new UAFStereotypeRegistry.StereotypeInfo(label, d, "UAF", true);
    }

    @Test
    void resolveBestRegistered_directNonFallback_returnsImmediately() {
        // Start name is itself a registered non-fallback entry — return it at distance 0.
        Map<String, UAFStereotypeRegistry.StereotypeInfo> reg = new LinkedHashMap<>();
        reg.put("OperationalPerformer", concrete("OperationalPerformer",
                                                 UAFStereotypeRegistry.Domain.OPERATIONAL));
        UAFModelTraverser.RegisteredHit hit = UAFModelTraverser.resolveBestRegistered(
            "OperationalPerformer",
            parents(Collections.emptyMap()),
            registry(reg));
        assertNotNull(hit);
        assertEquals("OperationalPerformer", hit.stereotypeName);
        assertEquals(0, hit.distance);
        assertEquals(UAFStereotypeRegistry.Domain.OPERATIONAL, hit.info.domain);
    }

    @Test
    void resolveBestRegistered_nonFallbackAtDistanceBeatsFallbackAtZero() {
        // The bare-noun-ancestor case in miniature: starting stereotype
        // "MyInternalPerformer" is NOT registered. Its parents reach `Resource`
        // (fallback, distance 1) and `OperationalPerformer` (non-fallback,
        // distance 2). Expected result: OperationalPerformer wins despite being
        // further away, because fallbacks never compete with non-fallbacks.
        Map<String, UAFStereotypeRegistry.StereotypeInfo> reg = new LinkedHashMap<>();
        reg.put("Resource",             fallback("Resource",
                                                  UAFStereotypeRegistry.Domain.RESOURCE));
        reg.put("OperationalPerformer", concrete("OperationalPerformer",
                                                  UAFStereotypeRegistry.Domain.OPERATIONAL));

        Map<String, List<String>> hierarchy = new LinkedHashMap<>();
        hierarchy.put("MyInternalPerformer", Arrays.asList("Resource", "Performer"));
        hierarchy.put("Performer",           Arrays.asList("OperationalPerformer"));

        UAFModelTraverser.RegisteredHit hit = UAFModelTraverser.resolveBestRegistered(
            "MyInternalPerformer", parents(hierarchy), registry(reg));
        assertNotNull(hit);
        assertEquals("OperationalPerformer", hit.stereotypeName,
            "Non-fallback ancestor must win even when a fallback ancestor is closer");
        assertEquals(UAFStereotypeRegistry.Domain.OPERATIONAL, hit.info.domain);
    }

    @Test
    void resolveBestRegistered_fallbackUsedWhenNoNonFallbackExists() {
        // No non-fallback ancestor anywhere in the chain. The fallback IS the
        // correct answer in this case — a model that legitimately applies a bare
        // `Resource` stereotype to an element with no more specific stereotype
        // should still export as :Resource / RESOURCE domain.
        Map<String, UAFStereotypeRegistry.StereotypeInfo> reg = new LinkedHashMap<>();
        reg.put("Resource", fallback("Resource",
                                      UAFStereotypeRegistry.Domain.RESOURCE));

        Map<String, List<String>> hierarchy = new LinkedHashMap<>();
        hierarchy.put("PlainResourceSubtype", Collections.singletonList("Resource"));

        UAFModelTraverser.RegisteredHit hit = UAFModelTraverser.resolveBestRegistered(
            "PlainResourceSubtype", parents(hierarchy), registry(reg));
        assertNotNull(hit);
        assertEquals("Resource", hit.stereotypeName);
        assertTrue(hit.info.isFallback);
        assertEquals(UAFStereotypeRegistry.Domain.RESOURCE, hit.info.domain);
    }

    @Test
    void resolveBestRegistered_closestNonFallbackWinsAmongMultiple() {
        // Two non-fallback ancestors at different distances — the closer one wins.
        Map<String, UAFStereotypeRegistry.StereotypeInfo> reg = new LinkedHashMap<>();
        reg.put("OperationalRole",      concrete("OperationalRole",
                                                  UAFStereotypeRegistry.Domain.OPERATIONAL));
        reg.put("OperationalPerformer", concrete("OperationalPerformer",
                                                  UAFStereotypeRegistry.Domain.OPERATIONAL));

        Map<String, List<String>> hierarchy = new LinkedHashMap<>();
        hierarchy.put("CustomOpRole",  Collections.singletonList("OperationalRole"));
        hierarchy.put("OperationalRole", Collections.singletonList("OperationalPerformer"));

        UAFModelTraverser.RegisteredHit hit = UAFModelTraverser.resolveBestRegistered(
            "CustomOpRole", parents(hierarchy), registry(reg));
        assertNotNull(hit);
        assertEquals("OperationalRole", hit.stereotypeName,
            "The closer non-fallback ancestor must win over the further one");
        assertEquals(1, hit.distance);
    }

    @Test
    void resolveBestRegistered_unknownChainReturnsNull() {
        // Starting stereotype is unknown and so are all its ancestors.
        Map<String, UAFStereotypeRegistry.StereotypeInfo> reg = new LinkedHashMap<>();
        reg.put("OperationalPerformer", concrete("OperationalPerformer",
                                                  UAFStereotypeRegistry.Domain.OPERATIONAL));

        Map<String, List<String>> hierarchy = new LinkedHashMap<>();
        hierarchy.put("Foo", Collections.singletonList("Bar"));
        hierarchy.put("Bar", Collections.singletonList("Baz"));

        assertNull(UAFModelTraverser.resolveBestRegistered("Foo",
            parents(hierarchy), registry(reg)));
    }

    @Test
    void resolveBestRegistered_handlesCyclesWithoutInfiniteLoop() {
        // Cycle in the general chain — must terminate via seen-set, not stack overflow.
        Map<String, UAFStereotypeRegistry.StereotypeInfo> reg = new LinkedHashMap<>();
        reg.put("CycleAnchor", concrete("CycleAnchor",
                                         UAFStereotypeRegistry.Domain.SHARED));

        Map<String, List<String>> hierarchy = new LinkedHashMap<>();
        hierarchy.put("A", Arrays.asList("B", "CycleAnchor"));
        hierarchy.put("B", Collections.singletonList("A"));   // cycle

        UAFModelTraverser.RegisteredHit hit = UAFModelTraverser.resolveBestRegistered(
            "A", parents(hierarchy), registry(reg));
        assertNotNull(hit);
        assertEquals("CycleAnchor", hit.stereotypeName);
    }

    @Test
    void resolveBestRegistered_nullStartReturnsNull() {
        assertNull(UAFModelTraverser.resolveBestRegistered(null,
            parents(Collections.emptyMap()),
            registry(Collections.emptyMap())));
    }

    @Test
    void resolveBestRegistered_directFallback_yieldsToFurtherNonFallback() {
        // The start name IS a registered fallback (distance 0) — but a non-fallback
        // ancestor exists further up. Non-fallback wins regardless of distance.
        Map<String, UAFStereotypeRegistry.StereotypeInfo> reg = new LinkedHashMap<>();
        reg.put("Resource",             fallback("Resource",
                                                  UAFStereotypeRegistry.Domain.RESOURCE));
        reg.put("OperationalPerformer", concrete("OperationalPerformer",
                                                  UAFStereotypeRegistry.Domain.OPERATIONAL));

        Map<String, List<String>> hierarchy = new LinkedHashMap<>();
        hierarchy.put("Resource", Collections.singletonList("OperationalPerformer"));

        UAFModelTraverser.RegisteredHit hit = UAFModelTraverser.resolveBestRegistered(
            "Resource", parents(hierarchy), registry(reg));
        assertNotNull(hit);
        assertEquals("OperationalPerformer", hit.stereotypeName,
            "Fallback at distance 0 must yield to non-fallback further up the chain");
    }
}
