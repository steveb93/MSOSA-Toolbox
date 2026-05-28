package com.uaf.neo4j.plugin.preview;

import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.ui.ExportConfigDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Drives {@link ExportConfigDialog} from canned sample data so the UI can be
 * rendered and iterated on without a live MSOSA project. The two MSOSA-coupled
 * scanning methods are overridden; nothing else in the dialog touches MSOSA
 * until the (disabled here) Export / Test Connection buttons are pressed.
 */
public class PreviewExportConfigDialog extends ExportConfigDialog {

    public PreviewExportConfigDialog() {
        super(null); // null Project — sample data supplied by the overrides below
    }

    @Override
    protected List<String> topLevelPackageNames() {
        return Arrays.asList(
            "Strategic", "Operational", "Resources", "Services",
            "Personnel", "Security", "Actual Resources", "Metadata");
    }

    @Override
    protected List<UAFElementDTO> scanModelElements() {
        List<UAFElementDTO> els = new ArrayList<>();
        addSamples(els, "Strategic",        "UAF",   "Capability",            12);
        addSamples(els, "Operational",      "UAF",   "OperationalPerformer",  23);
        addSamples(els, "Operational",      "UAF",   "OperationalActivity",   18);
        addSamples(els, "Resources",        "UAF",   "ResourcePerformer",     31);
        addSamples(els, "Services",         "UAF",   "ServiceSpecification",   9);
        addSamples(els, "Personnel",        "UAF",   "Post",                   7);
        addSamples(els, "Security",         "UAF",   "SecurityControl",        5);
        addSamples(els, "Actual Resources", "SysML", "Block",                 14);
        addSamples(els, "Metadata",         "BPMN",  "Process",                3);
        return els;
    }

    private static void addSamples(List<UAFElementDTO> out, String pkg, String lang, String stereotype, int n) {
        for (int i = 1; i <= n; i++) {
            out.add(UAFElementDTO.builder(pkg + "-" + i, stereotype + " " + i, stereotype)
                .packageName(pkg)
                .language(lang)
                .build());
        }
    }
}
