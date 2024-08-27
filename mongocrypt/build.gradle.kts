/*
 * Copyright 2019-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import de.undercouch.gradle.tasks.download.Download
import java.io.ByteArrayOutputStream

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        "classpath"(group = "net.java.dev.jna", name = "jna", version = "5.11.0")
    }
}

plugins {
    // Needed to download libmongocrypt from s3.
    id("de.undercouch.download") version "5.6.0"
}

group = "org.mongodb"
base.archivesBaseName = "mongodb-crypt"
description = "MongoDB client-side crypto support"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    api(project(path = ":bson", configuration = "default"))
    api("net.java.dev.jna:jna:5.11.0")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.2.11")
}

/*
 * Git version information
 */
// Returns a String representing the output of `git describe`
val gitDescribe by lazy {
    val describeStdOut = ByteArrayOutputStream()
    exec {
        commandLine = listOf("git", "describe", "--tags", "--always", "--dirty")
        standardOutput = describeStdOut
    }
    describeStdOut.toString().trim()
}

val isJavaTag by lazy { gitDescribe.startsWith("java") }
val gitVersion by lazy { gitDescribe.subSequence(gitDescribe.toCharArray().indexOfFirst { it.isDigit() }, gitDescribe.length).toString() }

val defaultDownloadRevision = "9a88ac5698e8e3ffcd6580b98c247f0126f26c40" // r.1.11.0

/*
 * Jna copy or download resources
 */
val jnaDownloadsDir = "$buildDir/jnaLibs/downloads/"
val jnaResourcesDir = "$buildDir/jnaLibs/resources/"
val jnaLibPlatform: String = if (com.sun.jna.Platform.RESOURCE_PREFIX.startsWith("darwin")) "darwin" else com.sun.jna.Platform.RESOURCE_PREFIX
val jnaLibsPath: String = System.getProperty("jnaLibsPath", "${jnaResourcesDir}${jnaLibPlatform}")
val jnaResources: String = System.getProperty("jna.library.path", jnaLibsPath)

// Download jnaLibs that match the git to jnaResourcesBuildDir
val downloadRevision: String = System.getProperties().computeIfAbsent("gitRevision") { k -> defaultDownloadRevision }.toString()
val binariesArchiveName = "libmongocrypt-java.tar.gz"

val downloadUrl: String = "https://mciuploads.s3.amazonaws.com/libmongocrypt/java/$downloadRevision/$binariesArchiveName"

val jnaMapping: Map<String, String> = mapOf(
    "rhel-62-64-bit" to "linux-x86-64",
    "rhel72-zseries-test" to "linux-s390x",
    "rhel-71-ppc64el" to "linux-ppc64le",
    "ubuntu1604-arm64" to "linux-aarch64",
    "windows-test" to "win32-x86-64",
    "macos" to "darwin"
)

sourceSets {
    main {
        java {
            resources {
                srcDirs(jnaResourcesDir)
            }
        }
    }
}

tasks.register<Download>("downloadJava") {
    src(downloadUrl)
    dest("${jnaDownloadsDir}/$binariesArchiveName")
    overwrite(true)
}

// The `processResources` task (defined by the `java-library` plug-in) consumes files in the main source set.
// Add a dependency on `unzipJava`. `unzipJava` adds libmongocrypt libraries to the main source set.
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mustRunAfter(tasks.named("unzipJava"))
}

tasks.register<Copy>("unzipJava") {
    outputs.upToDateWhen { false }
    from(tarTree(resources.gzip("${jnaDownloadsDir}/$binariesArchiveName")))
    include(jnaMapping.keys.flatMap {
        listOf("${it}/nocrypto/**/libmongocrypt.so", "${it}/lib/**/libmongocrypt.dylib", "${it}/bin/**/mongocrypt.dll" )
    })
    eachFile {
        path = "${jnaMapping[path.substringBefore("/")]}/${name}"
    }
    into(jnaResourcesDir)
    mustRunAfter("downloadJava")

    doLast {
        println("jna.library.path contents: \n  ${fileTree(jnaResourcesDir).files.joinToString(",\n  ")}")
    }
}

tasks.register("downloadJnaLibs") {
    dependsOn("downloadJava", "unzipJava")
}

tasks.test {
    systemProperty("jna.debug_load", "true")
    systemProperty("jna.library.path", jnaResources)
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }

    doFirst {
        println("jna.library.path contents:")
        println(fileTree(jnaResources)  {
            this.setIncludes(listOf("*.*"))
        }.files.joinToString(",\n  ", "  "))
    }
    dependsOn("downloadJnaLibs", "downloadJava", "unzipJava")
}

tasks.withType<AbstractPublishToMaven> {
    description = """$description
        | System properties:
        | =================
        |
        | jnaLibsPath    : Custom local JNA library path for inclusion into the build (rather than downloading from s3)
        | gitRevision    : Optional Git Revision to download the built resources for from s3.
    """.trimMargin()
}

tasks.jar {
    manifest {
        attributes(
                "-exportcontents" to "com.mongodb.crypt.capi.*;-noimport:=true",
                "Automatic-Module-Name" to "com.mongodb.crypt.capi",
                "Import-Package" to "org.bson.*",
                "Build-Version" to gitVersion,
                "Bundle-Version" to gitVersion,
                "Bundle-Name" to "MongoCrypt",
                "Bundle-SymbolicName" to "com.mongodb.crypt.capi",
                "Private-Package" to ""
        )
    }

    //NOTE this enables depending on the mongocrypt from driver-core
   dependsOn("downloadJnaLibs")
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}