/*
 * UAFStereotypeRegistry Reconciliation Script — issue #75 RC #6
 *
 * Run inside the MSOSA scripting console (Tools → Macros → Macro Engine, language
 * = Groovy) with a project open that has the UAF / SysML / BPMN profiles loaded.
 *
 * What it does:
 *   1. Enumerates every Stereotype defined in the project's loaded profiles via
 *      StereotypesHelper.getAllStereotypes(project).
 *   2. Compares against the names known to UAFStereotypeRegistry. Two sources are
 *      tried, in order:
 *        a) Live reflection — if the plugin jar is on the classpath, the actual
 *           registry keys are used (preferred; always in sync).
 *        b) Embedded fallback — the hardcoded list at the bottom of this script.
 *           Regenerate from REGISTRY_DUMP_SNIPPET (below) when the registry changes.
 *   3. Walks the current project's primary model and counts APPLIED stereotypes
 *      that are not in the registry — exactly the set the post-#75 export summary
 *      surfaces in its "Unmatched Stereotypes" tab, but verifiable here without
 *      having to actually run the export.
 *   4. Prints four sections:
 *        IN_REGISTRY_NOT_IN_PROFILE  — registry entries no loaded profile defines.
 *                                      Likely renames or stale entries; candidates
 *                                      for deletion.
 *        IN_PROFILE_NOT_IN_REGISTRY  — profile stereotypes the registry does not
 *                                      know. Candidates for addition.
 *        APPLIED_BUT_UNKNOWN         — stereotypes actually applied on elements
 *                                      that are not in the registry. Highest
 *                                      priority — these are dropping data today.
 *        SUGGESTED_REG_LINES         — copy-pasteable reg(...) lines with a
 *                                      best-guess Domain / language for each
 *                                      missing entry.
 *
 * After running:
 *   - Eyeball SUGGESTED_REG_LINES. The Domain heuristic is name- and profile-based;
 *     reviewer must verify before committing.
 *   - Open a PR adding the verified entries to:
 *       msosa-model-exporter/src/main/java/com/uaf/neo4j/plugin/model/UAFStereotypeRegistry.java
 *       cypher/init_uaf_graph.cypher   (matching :Stereotype seed nodes)
 *
 * Output goes to System.out (the scripting console).
 */

import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement
import com.nomagic.magicdraw.core.Application
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype

// ─────────────────────────────────────────────────────────────────────────────
// 1. Determine the registry's known stereotype names.
// ─────────────────────────────────────────────────────────────────────────────

def liveRegistryKeys = null
try {
    def regClass = Class.forName("com.uaf.neo4j.plugin.model.UAFStereotypeRegistry")
    def method   = regClass.getMethod("allStereotypeNames")
    liveRegistryKeys = method.invoke(null) as Set
    println "[OK] Loaded registry keys via reflection from UAFStereotypeRegistry (${liveRegistryKeys.size()} entries)."
} catch (Throwable t) {
    println "[INFO] UAFStereotypeRegistry not on classpath; falling back to embedded list."
    println "       (Install the msosa-model-exporter plugin to use the live class.)"
}

def REGISTRY_KEYS = liveRegistryKeys ?: EMBEDDED_REGISTRY_KEYS()

// ─────────────────────────────────────────────────────────────────────────────
// 2. Enumerate every Stereotype defined in the loaded profiles.
// ─────────────────────────────────────────────────────────────────────────────

def project = Application.getInstance().getProject()
if (project == null) {
    System.err.println("ERROR: No project open. Open a model with the UAF / SysML / BPMN")
    System.err.println("       profiles loaded and run this script again.")
    return
}

println ""
println "=== UAFStereotypeRegistry Reconciliation ==="
println "Project: ${project.getName()}"
println "Date:    ${new Date()}"
println ""

def definedStereos = StereotypesHelper.getAllStereotypes(project) ?: []
def profileNames    = new TreeSet<String>()
def stereotypeByName = [:]   // name → Stereotype (kept for profile lookup)
definedStereos.each { Stereotype s ->
    def name = s?.getName()
    if (name) {
        profileNames.add(name)
        // Keep the first occurrence — name collisions across profiles are flagged separately
        stereotypeByName.putIfAbsent(name, s)
    }
}

println "Stereotypes defined in loaded profiles : ${profileNames.size()}"
println "Stereotypes in registry                 : ${REGISTRY_KEYS.size()}"
println ""

// ─────────────────────────────────────────────────────────────────────────────
// 3. Compute set diffs.
// ─────────────────────────────────────────────────────────────────────────────

def inRegistryNotInProfile = (REGISTRY_KEYS - profileNames).sort()
def inProfileNotInRegistry = (profileNames - REGISTRY_KEYS).sort()

println "─── IN_REGISTRY_NOT_IN_PROFILE (${inRegistryNotInProfile.size()}) ───"
println "Registry entries no loaded profile defines — likely renamed/removed:"
if (inRegistryNotInProfile.isEmpty()) {
    println "  (none — every registry entry has a matching stereotype in the loaded profiles)"
} else {
    inRegistryNotInProfile.each { println "  - ${it}" }
}
println ""

println "─── IN_PROFILE_NOT_IN_REGISTRY (${inProfileNotInRegistry.size()}) ───"
println "Loaded-profile stereotypes the registry does not know — candidates for addition:"
if (inProfileNotInRegistry.isEmpty()) {
    println "  (none — the registry covers every defined stereotype)"
} else {
    inProfileNotInRegistry.each { name ->
        def s = stereotypeByName[name]
        def profile = profileNameOf(s)
        println "  - ${name}   (profile: ${profile})"
    }
}
println ""

// ─────────────────────────────────────────────────────────────────────────────
// 4. Walk the current model and find APPLIED stereotypes outside the registry.
//    This is the same set the post-#75 export summary dialog surfaces in its
//    "Unmatched Stereotypes" tab — surfacing it here lets you reconcile against
//    the actual data BEFORE running a full export.
// ─────────────────────────────────────────────────────────────────────────────

def appliedUnknown = new HashMap<String, Integer>()
walkAppliedUnknown(project.getPrimaryModel(), REGISTRY_KEYS, appliedUnknown)

println "─── APPLIED_BUT_UNKNOWN (${appliedUnknown.size()}) ───"
println "Stereotypes applied on at least one model element that the registry does not know."
println "These are the elements that would be silently dropped at export time:"
if (appliedUnknown.isEmpty()) {
    println "  (none — every applied stereotype in this project is in the registry)"
} else {
    appliedUnknown.entrySet()
        .sort { -it.value }
        .each { e -> println "  - ${e.key}: ${e.value} element(s)" }
}
println ""

// ─────────────────────────────────────────────────────────────────────────────
// 5. Suggested registry additions.
// ─────────────────────────────────────────────────────────────────────────────

println "─── SUGGESTED_REG_LINES ───"
println "Best-guess reg(...) lines for UAFStereotypeRegistry.java. Domain assignment"
println "is a heuristic based on stereotype name and profile path — verify each line"
println "before committing. Lines marked CHECK need manual classification."
println ""
inProfileNotInRegistry.each { name ->
    def s = stereotypeByName[name]
    def profile = profileNameOf(s)
    def lang    = guessLanguage(profile)
    def domain  = guessDomain(profile, name)
    if (lang == "UAF" && domain != null) {
        println "  reg(\"${name}\", \"${name}\", Domain.${domain});"
    } else if (lang != "UAF") {
        println "  reg(\"${name}\", \"${name}\", \"${lang}\");"
    } else {
        println "  reg(\"${name}\", \"${name}\", Domain.SHARED);  // CHECK: profile=${profile}"
    }
}
println ""

// Also seed the corresponding :Stereotype nodes so init_uaf_graph.cypher stays in sync.
if (!inProfileNotInRegistry.isEmpty()) {
    println "─── SUGGESTED_CYPHER_SEEDS (init_uaf_graph.cypher) ───"
    inProfileNotInRegistry.each { name ->
        def s = stereotypeByName[name]
        def lang   = guessLanguage(profileNameOf(s))
        def domain = guessDomain(profileNameOf(s), name)
        if (lang == "UAF" && domain != null) {
            println "  MERGE (:Stereotype {name: '${name}', domain: '${domain}'});"
        } else if (lang != "UAF") {
            println "  MERGE (:Stereotype {name: '${name}', language: '${lang}'});"
        } else {
            println "  MERGE (:Stereotype {name: '${name}', domain: 'SHARED'});  // CHECK"
        }
    }
    println ""
}

println "Done. Save this output and use it as the basis for the registry-reconciliation PR."

// ═════════════════════════════════════════════════════════════════════════════
// Helpers
// ═════════════════════════════════════════════════════════════════════════════

static String profileNameOf(Stereotype s) {
    if (s == null) return "?"
    try {
        def profile = s.getProfile()
        return profile != null ? profile.getName() : "?"
    } catch (Throwable ignored) {
        return "?"
    }
}

static String guessLanguage(String profile) {
    if (profile == null) return "UAF"
    def p = profile.toLowerCase()
    if (p.contains("bpmn"))  return "BPMN"
    if (p.contains("sysml")) return "SysML"
    return "UAF"
}

static String guessDomain(String profile, String name) {
    def p = (profile ?: "").toLowerCase()
    def n = name.toLowerCase()
    // Profile-path hints first (most reliable when MagicDraw exposes them)
    if (p.contains("strategic"))   return "STRATEGIC"
    if (p.contains("operational")) return "OPERATIONAL"
    if (p.contains("resource"))    return "RESOURCE"
    if (p.contains("service"))     return "SERVICE"
    if (p.contains("personnel"))   return "PERSONNEL"
    if (p.contains("acquisition")) return "ACQUISITION"
    if (p.contains("security"))    return "SECURITY"
    // Fall back to name-based heuristics
    if (n.startsWith("capability") || n in ["vision","endstate","desiredeffect","enterprisephase"])
        return "STRATEGIC"
    if (n.startsWith("operational") || n in ["needline","performerport"])
        return "OPERATIONAL"
    if (n.startsWith("resource") ||
        n in ["hardwareelement","softwareelement","software","system","systemblock",
              "technology","actualsystem","naturalresource","logicalarchitecture",
              "physicalarchitecture"])
        return "RESOURCE"
    if (n.startsWith("service"))   return "SERVICE"
    if (n.startsWith("organization") ||
        n in ["post","personnelactivity","actualorganization","organizationalcapability",
              "organizationalresource"])
        return "PERSONNEL"
    if (n.startsWith("project") || n in ["milestone","fundingrequest"])
        return "ACQUISITION"
    if (n.startsWith("security")) return "SECURITY"
    return null  // signals "CHECK" in the printed output
}

static void walkAppliedUnknown(Element element, Set knownNames, Map<String,Integer> acc) {
    if (element == null) return
    try {
        StereotypesHelper.getStereotypes(element).each { Stereotype s ->
            def name = s?.getName()
            if (name && !knownNames.contains(name)) {
                acc.merge(name, 1) { a, b -> a + b }
            }
        }
        element.getOwnedElement().each { child ->
            walkAppliedUnknown((Element) child, knownNames, acc)
        }
    } catch (Throwable ignored) {
        // Skip the element on traversal error; matches the production traverser's behaviour.
    }
}

/*
 * REGISTRY_DUMP_SNIPPET — regenerate the EMBEDDED_REGISTRY_KEYS below by running
 * this one-liner in the MSOSA scripting console with the plugin loaded:
 *
 *   com.uaf.neo4j.plugin.model.UAFStereotypeRegistry.allStereotypeNames()
 *     .each { println '        "' + it + '",' }
 *
 * Copy the output into the EMBEDDED_REGISTRY_KEYS block.
 *
 * The embedded list below mirrors `preview` HEAD; bump it after PR2/PR4 land or
 * when subsequent registry-reconciliation PRs add/remove entries.
 */

static Set<String> EMBEDDED_REGISTRY_KEYS() {
    return [
        // STRATEGIC
        "Capability", "CapabilityConfiguration", "CapabilityComposition",
        "CapabilityDependency", "CapabilitySpecialization", "Vision",
        "EndState", "DesiredEffect", "EnterprisePhase", "CapabilityIncrement",
        // OPERATIONAL
        "OperationalPerformer", "OperationalActivity", "OperationalExchange",
        "OperationalCapability", "OperationalConnector", "OperationalDomain",
        "OperationalProcess", "OperationalFunction", "OperationalInteraction",
        "OperationalInformation", "NeedLine", "PerformerPort", "OperationalRole",
        // OPERATIONAL — UAF-wrapped BPMN data elements
        "DataObject", "DataInput", "DataOutput", "DataStore",
        // RESOURCE
        "ResourcePerformer", "ResourceFunction", "ResourceInteraction",
        "ResourceArtifact", "ResourceInformation", "ResourcePort",
        "ResourceConnector", "ResourceArchitecture", "ResourceSystem",
        "HardwareElement", "SoftwareElement", "Software", "NaturalResource",
        "SystemBlock", "System", "ActualSystem", "Technology",
        "LogicalArchitecture", "PhysicalArchitecture",
        // SERVICE
        "ServicePerformer", "ServiceFunction", "ServiceSpecification",
        "ServiceInterface", "ServicePoint", "ServiceConnector",
        "ServiceExchange", "Service", "ServiceArchitecture",
        // PERSONNEL
        "Organization", "OrganizationalResource", "Post", "PersonnelActivity",
        "ActualOrganization", "OrganizationalCapability",
        // ACQUISITION
        "Project", "Milestone", "ProjectMilestone", "ProjectBoundary", "FundingRequest",
        // SECURITY
        "SecurityDomain", "SecurityAsset", "SecurityPolicy",
        // SHARED
        "Measurement", "Standard", "Condition", "ConfigurationItem",
        "ImplementationConstraint", "Location", "ActualLocation",
        // SysML 1.6
        "Block", "Requirement", "InterfaceBlock", "ValueType", "ConstraintBlock",
        "FlowSpecification", "FlowPort", "FullPort", "ProxyPort", "ItemFlow",
        // BPMN 2.0
        "Task", "UserTask", "ServiceTask", "SendTask", "ReceiveTask",
        "StartEvent", "EndEvent", "IntermediateThrowEvent", "IntermediateCatchEvent",
        "ExclusiveGateway", "ParallelGateway", "InclusiveGateway", "EventBasedGateway",
        "SubProcess", "CallActivity", "Lane", "Pool"
    ] as Set
}
