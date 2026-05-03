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

Write-Host "All MSOSA jars installed successfully." -ForegroundColor Green
