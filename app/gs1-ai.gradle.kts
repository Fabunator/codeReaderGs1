// ---------------------------------------------------------------------------
// GS1-AI-Tabelle: externe Quellen laden und Gs1AiDictionary.kt erzeugen.
//
//   gradlew updateGs1Ai                beide Quellen laden und die Kotlin-Datei neu erzeugen
//   gradlew downloadGs1Sources         nur die Quellen nach tools/gs1/ laden
//   gradlew generateGs1AiDictionary    nur die Kotlin-Datei aus tools/gs1/ erzeugen (offline)
//
// Quellen:
//   Formate, Laengen, Pruefregeln – GS1 Barcode Syntax Dictionary (GitHub, GS1 AISBL)
//   lange Beschreibungen          – Feld "description" der JSON-LD von ref.gs1.org
// Warum zwei Quellen: siehe Projekt-Dokument gs1-ai-tabelle.md. Kurz: das Dictionary
// ist genauer, die JSON-LD hat die ausfuehrlichen Bezeichnungen.
//
// Der normale Build laedt nichts. Nach updateGs1Ai zeigt `git diff tools/gs1`, was sich
// bei GS1 geaendert hat; danach Tests laufen lassen und einchecken.
// ---------------------------------------------------------------------------

import groovy.json.JsonSlurper
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

/** Laedt beide Quellen, prueft sie und legt sie unter tools/gs1/ ab. */
abstract class DownloadGs1Sources : DefaultTask() {

    @get:Input
    abstract val dictionaryUrl: Property<String>

    @get:Input
    abstract val jsonLdUrl: Property<String>

    @get:OutputFile
    abstract val dictionaryFile: RegularFileProperty

    @get:OutputFile
    abstract val descriptionsFile: RegularFileProperty

    init {
        // Ohne Netzabfrage laesst sich nicht wissen, ob es etwas Neues gibt
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun download() {
        val client = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault()) // beruecksichtigt systemProp.https.proxyHost
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build()

        // --- Syntax Dictionary: unveraendert uebernehmen ---
        val dictionary = fetch(client, dictionaryUrl.get(), "text/plain")
        val entries = dictionary.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        check(entries.size > 150 && entries.any { it.startsWith("01 ") } && entries.any { it.startsWith("3100-3105") }) {
            "Syntax Dictionary sieht unvollstaendig aus (${entries.size} Eintraege) – nichts ueberschrieben"
        }
        val dictionaryAis = expand(entries.map { it.trim().split(Regex("\\s+"))[0] })

        // --- JSON-LD: nur die Beschreibungen, Bereiche zusammengefasst ---
        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parseText(fetch(client, jsonLdUrl.get(), "application/ld+json")) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val items = root["applicationIdentifiers"] as? List<Map<String, Any?>>
            ?: error("JSON-LD ohne 'applicationIdentifiers' – nichts ueberschrieben")
        val meta = items.firstOrNull { it["applicationIdentifier"] == null }.orEmpty()
        val version = meta["owl:versionInfo"]?.toString() ?: "?"
        val modified = (meta["dc:lastModified"] as? Map<*, *>)?.get("@value")?.toString() ?: "?"

        val rows = items.mapNotNull { item ->
            val ai = item["applicationIdentifier"]?.toString() ?: return@mapNotNull null
            ai to item["description"]?.toString().orEmpty().replace(Regex("\\s+"), " ").trim()
        }
        check(rows.size > 500) { "JSON-LD enthaelt nur ${rows.size} AIs – nichts ueberschrieben" }
        rows.filter { it.second.isEmpty() }.forEach { logger.warn("GS1: keine Beschreibung fuer AI ${it.first}") }

        val jsonAis = rows.map { it.first }.toSet()
        (dictionaryAis - jsonAis).takeIf { it.isNotEmpty() }?.let { logger.warn("GS1: nur im Syntax Dictionary: ${it.sorted()}") }
        (jsonAis - dictionaryAis).takeIf { it.isNotEmpty() }?.let { logger.warn("GS1: nur in der JSON-LD: ${it.sorted()}") }

        val ranges = mutableListOf<Triple<String, String, String>>() // erste AI, letzte AI, Text
        for ((ai, text) in rows) {
            val last = ranges.lastOrNull()
            if (last != null && last.third == text && ai.length == last.second.length && ai.toInt() == last.second.toInt() + 1) {
                ranges[ranges.lastIndex] = last.copy(second = ai)
            } else {
                ranges += Triple(ai, ai, text)
            }
        }
        val descriptions = buildString {
            append("# Lange Beschreibungen der GS1 Application Identifier\n")
            append("# Quelle: ${jsonLdUrl.get()} (Version $version, geaendert $modified)\n")
            append("# Erzeugt von der Gradle-Task downloadGs1Sources – nicht von Hand bearbeiten.\n")
            for ((first, last, text) in ranges) {
                append(if (first == last) first else "$first-$last").append('\t').append(text).append('\n')
            }
        }

        // Erst schreiben, wenn beide Quellen in Ordnung sind
        report("Syntax Dictionary", replace(dictionaryFile.get().asFile, dictionary))
        report("Beschreibungen (JSON-LD $version, $modified)", replace(descriptionsFile.get().asFile, descriptions))
        logger.lifecycle("GS1: ${dictionaryAis.size} AIs im Syntax Dictionary, ${jsonAis.size} in der JSON-LD")
    }

    private fun fetch(client: HttpClient, url: String, accept: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", accept)
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()} fuer $url" }
        return response.body()
    }

    /** "3100-3105" → 3100 … 3105 */
    private fun expand(codes: List<String>): Set<String> = codes.flatMap { code ->
        val parts = code.split('-')
        val first = parts[0]
        (first.toInt()..parts.getOrElse(1) { first }.toInt()).map { it.toString().padStart(first.length, '0') }
    }.toSet()

    /** Schreibt nur bei Aenderung, ueber eine Temp-Datei; true = geaendert. */
    private fun replace(target: File, content: String): Boolean {
        if (target.isFile && target.readText(Charsets.UTF_8) == content) return false
        target.parentFile.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content, Charsets.UTF_8)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return true
    }

    private fun report(what: String, changed: Boolean) =
        logger.lifecycle("GS1: $what ${if (changed) "AKTUALISIERT" else "unveraendert"}")
}

/** Erzeugt Gs1AiDictionary.kt aus den beiden Dateien unter tools/gs1/ – ohne Netz. */
abstract class GenerateGs1AiDictionary : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val dictionaryFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val descriptionsFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val source = dictionaryFile.get().asFile.readText(Charsets.UTF_8)
        val entries = source.lines().map { it.trimEnd() }.filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        val release = source.lines().firstOrNull { it.startsWith("# Release:") }?.substringAfter(':')?.trim() ?: "unbekannt"

        val descriptionLines = descriptionsFile.get().asFile.readText(Charsets.UTF_8).lines()
        val descriptions = descriptionLines.map { it.trimEnd() }.filter { it.isNotBlank() && !it.startsWith("#") }
        val descriptionSource = descriptionLines.firstOrNull { it.startsWith("# Quelle:") }?.substringAfter(':')?.trim()
            ?: "https://ref.gs1.org/ai/GS1_Application_Identifiers.jsonld"

        val body = entries.joinToString("\n")
        val descBody = descriptions.joinToString("\n")
        val quotes = "\"\"\""
        // Kotlin-Rohstrings kennen kein Escaping
        for (text in listOf(body, descBody)) {
            check('$' !in text && quotes !in text) { "Sonderzeichen in den GS1-Daten – Generator anpassen" }
        }
        // String-Konstanten im Klassenformat: max. 65535 Byte
        check(body.toByteArray(Charsets.UTF_8).size < 60000 && descBody.toByteArray(Charsets.UTF_8).size < 60000) {
            "GS1-Daten zu gross fuer eine String-Konstante"
        }

        val kotlin = """
            |package com.example.codescannergs1
            |
            |// ---------------------------------------------------------------------------
            |// GENERIERT von der Gradle-Task generateGs1AiDictionary (app/gs1-ai.gradle.kts)
            |// – nicht von Hand bearbeiten. Quellen aktualisieren: gradlew updateGs1Ai
            |//
            |// Quelle: GS1 Barcode Syntax Dictionary (Release: $release)
            |//         https://github.com/gs1/gs1-syntax-dictionary – Datensatz hinter https://ref.gs1.org/ai/
            |// Copyright (c) 2020-2021 BWIPP project, (c) 2020-2021 Zint Project, (c) 2021-2025 GS1 AISBL
            |// Licensed under the Apache License, Version 2.0
            |//
            |// Zeilenformat:  AIs  [Flags]  Spezifikation  [Attribute...]  [# Titel]
            |//   AIs     einzelne AI oder Bereich, z. B. "3100-3105"
            |//   Flags   "*" = vordefinierte Laenge, kein FNC1 danach; "?" = Digital-Link-Attribut
            |//   Spez.   Komponenten Typ[,Linter...]: N = numerisch, X = CSET 82, Y = CSET 39,
            |//           Z = base64url; "N6" feste, "X..20" variable Laenge, "[...]" optional
            |// Ausgewertet in GS1Parser.
            |// ---------------------------------------------------------------------------
            |
            |internal const val GS1_SYNTAX_DICTIONARY = $quotes
            |@@BODY@@
            |$quotes
            |
            |// ---------------------------------------------------------------------------
            |// Lange Beschreibungen, Feld "description" aus
            |// $descriptionSource
            |// (GS1 AISBL, Apache 2.0). Zeilenformat: AI oder Bereich <TAB> Beschreibung.
            |// Wortlaut unveraendert.
            |// ---------------------------------------------------------------------------
            |
            |internal const val GS1_AI_DESCRIPTIONS = $quotes
            |@@DESC@@
            |$quotes
            |""".trimMargin()
            .replace("@@BODY@@", body)
            .replace("@@DESC@@", descBody)

        val target = outputFile.get().asFile
        // Zeilenenden der vorhandenen Datei beibehalten, damit kein Voll-Diff entsteht
        val crlf = if (target.isFile) target.readText(Charsets.UTF_8).contains("\r\n") else System.lineSeparator() == "\r\n"
        val content = if (crlf) kotlin.replace("\n", "\r\n") else kotlin
        if (target.isFile && target.readText(Charsets.UTF_8) == content) {
            logger.lifecycle("GS1: ${target.name} unveraendert (${entries.size} Eintraege)")
            return
        }
        target.writeText(content, Charsets.UTF_8)
        logger.lifecycle("GS1: ${target.name} neu erzeugt (${entries.size} Eintraege, ${descriptions.size} Beschreibungen)")
    }
}

val gs1Dir = rootProject.layout.projectDirectory.dir("tools/gs1")

val downloadGs1Sources = tasks.register<DownloadGs1Sources>("downloadGs1Sources") {
    group = "gs1"
    description = "Laedt GS1 Syntax Dictionary und JSON-LD-Beschreibungen nach tools/gs1/"
    // Per -Pgs1.dictionaryUrl=… / -Pgs1.jsonLdUrl=… ueberschreibbar, etwa fuer einen Mirror
    dictionaryUrl.set(providers.gradleProperty("gs1.dictionaryUrl")
        .orElse("https://raw.githubusercontent.com/gs1/gs1-syntax-dictionary/main/gs1-syntax-dictionary.txt"))
    jsonLdUrl.set(providers.gradleProperty("gs1.jsonLdUrl")
        .orElse("https://ref.gs1.org/ai/GS1_Application_Identifiers.jsonld"))
    dictionaryFile.set(gs1Dir.file("gs1-syntax-dictionary.txt"))
    descriptionsFile.set(gs1Dir.file("gs1-ai-descriptions.tsv"))
}

val generateGs1AiDictionary = tasks.register<GenerateGs1AiDictionary>("generateGs1AiDictionary") {
    group = "gs1"
    description = "Erzeugt Gs1AiDictionary.kt aus tools/gs1/ (offline)"
    dictionaryFile.set(gs1Dir.file("gs1-syntax-dictionary.txt"))
    descriptionsFile.set(gs1Dir.file("gs1-ai-descriptions.tsv"))
    outputFile.set(layout.projectDirectory.file("src/main/java/com/example/codescannergs1/Gs1AiDictionary.kt"))
    mustRunAfter(downloadGs1Sources)
}

tasks.register("updateGs1Ai") {
    group = "gs1"
    description = "Laedt die aktuellen GS1-Quellen und erzeugt Gs1AiDictionary.kt neu"
    dependsOn(downloadGs1Sources, generateGs1AiDictionary)
}
