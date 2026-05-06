# Run from uaf-neo4j-plugin\ to register MSOSA SDK jars in your local Maven repo.
# Usage: .\install-msosa-jars.ps1

$ErrorActionPreference = "Stop"
$jarsDir = Join-Path $PSScriptRoot "msosa-api"
$version  = "2022x-hf2"
$group    = "com.nomagic.magicdraw"

mvn install:install-file "-Dfile=$jarsDir\md.jar" "-DgroupId=$group" "-DartifactId=md" "-Dversion=$version" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\md_api.jar" "-DgroupId=$group" "-DartifactId=md-api" "-Dversion=$version" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\com.nomagic.magicdraw.uml2-2022.2.0-105-acd52bbc.jar" "-DgroupId=$group" "-DartifactId=uml2" "-Dversion=$version" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\com.nomagic.magicdraw.foundation-2022.2.0-105-acd52bbc.jar" "-DgroupId=$group" "-DartifactId=magicdraw-foundation" "-Dversion=$version" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\com.nomagic.magicdraw.core.diagram-2022.2.0-105-acd52bbc.jar" "-DgroupId=$group" "-DartifactId=magicdraw-core-diagram" "-Dversion=$version" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\com.dassault_systemes.modeler.foundation-2022.2.0-105-acd52bbc.jar" "-DgroupId=com.dassault_systemes" "-DartifactId=modeler-foundation" "-Dversion=$version" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\cmof-1.4.jar" "-DgroupId=com.nomagic" "-DartifactId=cmof" "-Dversion=1.4" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\com.nomagic.magicdraw.modeling-2022.2.0-105-acd52bbc.jar" "-DgroupId=$group" "-DartifactId=magicdraw-modeling" "-Dversion=$version" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\javax.jmi-1.0.jar" "-DgroupId=javax.jmi" "-DartifactId=jmi" "-Dversion=1.0" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\org.eclipse.emf.ecore-2.33.0.jar" "-DgroupId=org.eclipse.emf" "-DartifactId=ecore" "-Dversion=2.33.0" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\com.nomagic.utils-2022.2.0-105-acd52bbc.jar" "-DgroupId=com.nomagic" "-DartifactId=nomagic-utils" "-Dversion=$version" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\org.eclipse.emf.common-2.28.0.jar" "-DgroupId=org.eclipse.emf" "-DartifactId=emf-common" "-Dversion=2.28.0" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\common-2022.2.0-105-acd52bbc.jar" "-DgroupId=$group" "-DartifactId=magicdraw-common" "-Dversion=$version" "-Dpackaging=jar" "-DgeneratePom=true"
mvn install:install-file "-Dfile=$jarsDir\jide-action-3.7.13.jar" "-DgroupId=com.jidesoft" "-DartifactId=jide-action" "-Dversion=3.7.13" "-Dpackaging=jar" "-DgeneratePom=true"

Write-Host "All MSOSA jars installed successfully." -ForegroundColor Green
