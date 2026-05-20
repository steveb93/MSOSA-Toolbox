/*
 * UAFStereotypeRegistry Reconciliation Script — issue #75 RC #6
 *
 * Run inside the MSOSA scripting console (Tools → Macros → Macro Engine, language
 * = Groovy) with a project open that has the UAF / SysML / BPMN profiles loaded.
 *
 * Output is written to MagicDraw's notification panel via GUILog.log() — look
 * at the bottom panel of the IDE, not the IDE-attached system console.
 *
 * What it does:
 *   1. Enumerates every Stereotype defined in the project's loaded profiles via
 *      StereotypesHelper.getAllStereotypes(project).
 *   2. Compares against the names known to UAFStereotypeRegistry. Two sources:
 *        a) Live reflection — if the plugin jar is on the classpath, the actual
 *           registry keys are used (preferred; always in sync).
 *        b) Embedded fallback — the hardcoded list at the bottom of this script.
 *   3. Walks the current project and counts APPLIED stereotypes that are not in
 *      the registry — exactly the set the post-#75 export summary surfaces in
 *      its "Unmatched Stereotypes" tab.
 *   4. Prints four sections:
 *        IN_REGISTRY_NOT_IN_PROFILE — registry entries no loaded profile defines
 *        IN_PROFILE_NOT_IN_REGISTRY — profile stereotypes the registry doesn't know
 *        APPLIED_BUT_UNKNOWN        — stereotypes applied to elements that are
 *                                     not in the registry (highest priority)
 *        SUGGESTED_REG_LINES        — copy-pasteable reg(...) lines and Cypher
 *                                     MERGE statements with a heuristic Domain
 *                                     and language guess
 *
 * After running:
 *   - Eyeball SUGGESTED_REG_LINES. Domain heuristic is profile- and name-based;
 *     verify each line before committing.
 *   - Add the verified entries to:
 *       msosa-model-exporter/src/main/java/com/uaf/neo4j/plugin/model/UAFStereotypeRegistry.java
 *       cypher/init_uaf_graph.cypher   (matching :Stereotype seed nodes)
 */

import com.nomagic.magicdraw.core.Application
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype

// ─────────────────────────────────────────────────────────────────────────────
// Output sink — everything goes to MagicDraw's notification panel.
// ─────────────────────────────────────────────────────────────────────────────

def guiLog = Application.getInstance().getGUILog()
def log    = { line -> guiLog.log(line == null ? "" : line.toString()) }

// ─────────────────────────────────────────────────────────────────────────────
// Closures (defined upfront so script-body code below can call them freely).
// ─────────────────────────────────────────────────────────────────────────────

def profileNameOf = { Stereotype s ->
    if (s == null) return "?"
    try {
        def profile = s.getProfile()
        return profile != null ? profile.getName() : "?"
    } catch (Throwable ignored) {
        return "?"
    }
}

def guessLanguage = { String profile ->
    if (profile == null) return "UAF"
    def p = profile.toLowerCase()
    if (p.contains("bpmn"))  return "BPMN"
    if (p.contains("sysml")) return "SysML"
    return "UAF"
}

def guessDomain = { String profile, String name ->
    def p = (profile ?: "").toLowerCase()
    def n = name.toLowerCase()
    if (p.contains("strategic"))   return "STRATEGIC"
    if (p.contains("operational")) return "OPERATIONAL"
    if (p.contains("resource"))    return "RESOURCE"
    if (p.contains("service"))     return "SERVICE"
    if (p.contains("personnel"))   return "PERSONNEL"
    if (p.contains("acquisition")) return "ACQUISITION"
    if (p.contains("security"))    return "SECURITY"
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
    return null
}

// walkAppliedUnknown is recursive — declare it before the entry point that calls it.
def walkAppliedUnknown
walkAppliedUnknown = { Element element, Set knownNames, Map acc ->
    if (element == null) return
    try {
        def stereos = StereotypesHelper.getStereotypes(element) ?: []
        stereos.each { Stereotype s ->
            def name = s?.getName()
            if (name && !knownNames.contains(name)) {
                acc[name] = (acc[name] ?: 0) + 1
            }
        }
        def owned = element.getOwnedElement() ?: []
        owned.each { child ->
            walkAppliedUnknown(child as Element, knownNames, acc)
        }
    } catch (Throwable ignored) {
        // Skip the element on traversal error; matches the production traverser.
    }
}

// Embedded registry list — mirrors `preview` HEAD of UAFStereotypeRegistry.
// Refresh after registry-adding PRs land. Regeneration snippet (paste into the
// macro engine with the plugin loaded):
//
//   import com.uaf.neo4j.plugin.model.UAFStereotypeRegistry
//   def guiLog = com.nomagic.magicdraw.core.Application.getInstance().getGUILog()
//   UAFStereotypeRegistry.allStereotypeNames().each { guiLog.log('        "' + it + '",') }
def EMBEDDED_REGISTRY_KEYS = [
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

// ─────────────────────────────────────────────────────────────────────────────
// 1. Determine the registry's known stereotype names.
// ─────────────────────────────────────────────────────────────────────────────

def liveRegistryKeys = null
try {
    def regClass = Class.forName("com.uaf.neo4j.plugin.model.UAFStereotypeRegistry")
    def method   = regClass.getMethod("allStereotypeNames")
    liveRegistryKeys = method.invoke(null) as Set
    log "[OK] Loaded ${liveRegistryKeys.size()} registry keys via reflection from UAFStereotypeRegistry."
} catch (Throwable t) {
    log "[INFO] UAFStereotypeRegistry not on classpath (${t.class.simpleName}); using embedded list."
    log "       (Install the msosa-model-exporter plugin to use the live class.)"
}
def REGISTRY_KEYS = liveRegistryKeys ?: EMBEDDED_REGISTRY_KEYS

// ─────────────────────────────────────────────────────────────────────────────
// 2. Project sanity check.
// ─────────────────────────────────────────────────────────────────────────────

def project = Application.getInstance().getProject()
if (project == null) {
    log "ERROR: No project open. Open a model with the UAF / SysML / BPMN profiles"
    log "       loaded and run this script again."
    return
}

log ""
log "=== UAFStereotypeRegistry Reconciliation ==="
log "Project: ${project.getName()}"
log "Date:    ${new Date()}"
log ""

// ─────────────────────────────────────────────────────────────────────────────
// 3. Enumerate every Stereotype defined in the loaded profiles.
// ─────────────────────────────────────────────────────────────────────────────

def definedStereos   = StereotypesHelper.getAllStereotypes(project) ?: []
def profileNames     = new TreeSet<String>()
def stereotypeByName = [:]
definedStereos.each { Stereotype s ->
    def name = s?.getName()
    if (name) {
        profileNames.add(name)
        if (!stereotypeByName.containsKey(name)) stereotypeByName[name] = s
    }
}

log "Stereotypes defined in loaded profiles : ${profileNames.size()}"
log "Stereotypes in registry                 : ${REGISTRY_KEYS.size()}"
log ""

// ─────────────────────────────────────────────────────────────────────────────
// 4. Set diffs.
// ─────────────────────────────────────────────────────────────────────────────

def inRegistryNotInProfile = (REGISTRY_KEYS - profileNames).sort()
def inProfileNotInRegistry = (profileNames - REGISTRY_KEYS).sort()

log "─── IN_REGISTRY_NOT_IN_PROFILE (${inRegistryNotInProfile.size()}) ───"
log "Registry entries no loaded profile defines — likely renamed/removed:"
if (inRegistryNotInProfile.isEmpty()) {
    log "  (none — every registry entry has a matching stereotype)"
} else {
    inRegistryNotInProfile.each { log "  - ${it}" }
}
log ""

log "─── IN_PROFILE_NOT_IN_REGISTRY (${inProfileNotInRegistry.size()}) ───"
log "Loaded-profile stereotypes the registry does not know — candidates for addition:"
if (inProfileNotInRegistry.isEmpty()) {
    log "  (none — the registry covers every defined stereotype)"
} else {
    inProfileNotInRegistry.each { name ->
        def s = stereotypeByName[name]
        log "  - ${name}   (profile: ${profileNameOf(s)})"
    }
}
log ""

// ─────────────────────────────────────────────────────────────────────────────
// 5. Walk the current model and find APPLIED stereotypes outside the registry.
// ─────────────────────────────────────────────────────────────────────────────

def appliedUnknown = new HashMap<String, Integer>()
walkAppliedUnknown(project.getPrimaryModel() as Element, REGISTRY_KEYS, appliedUnknown)

log "─── APPLIED_BUT_UNKNOWN (${appliedUnknown.size()}) ───"
log "Stereotypes applied on at least one model element that the registry does not know."
log "These are the elements that would be silently dropped at export today:"
if (appliedUnknown.isEmpty()) {
    log "  (none — every applied stereotype in this project is in the registry)"
} else {
    appliedUnknown.entrySet().sort { -it.value }.each { e ->
        log "  - ${e.key}: ${e.value} element(s)"
    }
}
log ""

// ─────────────────────────────────────────────────────────────────────────────
// 6. Suggested registry additions.
// ─────────────────────────────────────────────────────────────────────────────

log "─── SUGGESTED_REG_LINES ───"
log "Best-guess reg(...) lines for UAFStereotypeRegistry.java. Domain assignment"
log "is a heuristic based on stereotype name and profile path — verify each line"
log "before committing. Lines marked CHECK need manual classification."
log ""
inProfileNotInRegistry.each { name ->
    def s       = stereotypeByName[name]
    def profile = profileNameOf(s)
    def lang    = guessLanguage(profile)
    def domain  = guessDomain(profile, name)
    if (lang == "UAF" && domain != null) {
        log "  reg(\"${name}\", \"${name}\", Domain.${domain});"
    } else if (lang != "UAF") {
        log "  reg(\"${name}\", \"${name}\", \"${lang}\");"
    } else {
        log "  reg(\"${name}\", \"${name}\", Domain.SHARED);  // CHECK: profile=${profile}"
    }
}
log ""

if (!inProfileNotInRegistry.isEmpty()) {
    log "─── SUGGESTED_CYPHER_SEEDS (init_uaf_graph.cypher) ───"
    inProfileNotInRegistry.each { name ->
        def s       = stereotypeByName[name]
        def profile = profileNameOf(s)
        def lang    = guessLanguage(profile)
        def domain  = guessDomain(profile, name)
        if (lang == "UAF" && domain != null) {
            log "  MERGE (:Stereotype {name: '${name}', domain: '${domain}'});"
        } else if (lang != "UAF") {
            log "  MERGE (:Stereotype {name: '${name}', language: '${lang}'});"
        } else {
            log "  MERGE (:Stereotype {name: '${name}', domain: 'SHARED'});  // CHECK"
        }
    }
    log ""
}

log "Done. Copy the relevant sections into the follow-up registry-reconciliation PR."
