#!/usr/bin/env bash
# Linux/macOS equivalent of install-msosa-jars.ps1.
# Registers the MSOSA SDK jars (checked into /msosa-sdk/ at the repo root) into
# the local Maven repo so the plugin can be built off-MSOSA (e.g. CI, UI preview).
# Usage: ./install-msosa-jars.sh
set -euo pipefail

JARS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/msosa-sdk"
VERSION="2022x-hf2"
GROUP="com.nomagic.magicdraw"

install() { # file groupId artifactId version
  mvn -q install:install-file \
    "-Dfile=${JARS_DIR}/$1" \
    "-DgroupId=$2" "-DartifactId=$3" "-Dversion=$4" \
    -Dpackaging=jar -DgeneratePom=true
}

install "md.jar"                                                  "$GROUP"               "md"                     "$VERSION"
install "com.nomagic.ci.persistence-2022.2.0-105-acd52bbc.jar"    "com.nomagic.ci"       "persistence"            "$VERSION"
install "md_api.jar"                                              "$GROUP"               "md-api"                 "$VERSION"
install "com.nomagic.magicdraw.uml2-2022.2.0-105-acd52bbc.jar"    "$GROUP"               "uml2"                   "$VERSION"
install "com.nomagic.magicdraw.foundation-2022.2.0-105-acd52bbc.jar" "$GROUP"            "magicdraw-foundation"   "$VERSION"
install "com.nomagic.magicdraw.core.diagram-2022.2.0-105-acd52bbc.jar" "$GROUP"          "magicdraw-core-diagram" "$VERSION"
install "com.dassault_systemes.modeler.foundation-2022.2.0-105-acd52bbc.jar" "com.dassault_systemes" "modeler-foundation" "$VERSION"
install "cmof-1.4.jar"                                            "com.nomagic"          "cmof"                   "1.4"
install "com.nomagic.magicdraw.modeling-2022.2.0-105-acd52bbc.jar" "$GROUP"              "magicdraw-modeling"     "$VERSION"
install "javax.jmi-1.0.jar"                                       "javax.jmi"            "jmi"                    "1.0"
install "org.eclipse.emf.ecore-2.33.0.jar"                        "org.eclipse.emf"      "ecore"                  "2.33.0"
install "com.nomagic.utils-2022.2.0-105-acd52bbc.jar"             "com.nomagic"          "nomagic-utils"          "$VERSION"
install "org.eclipse.emf.common-2.28.0.jar"                       "org.eclipse.emf"      "emf-common"             "2.28.0"
install "common-2022.2.0-105-acd52bbc.jar"                        "$GROUP"               "magicdraw-common"       "$VERSION"
install "jide-action-3.7.13.jar"                                  "com.jidesoft"         "jide-action"            "3.7.13"

echo "All MSOSA jars installed successfully."
