package xyz.mdhv.formanalyser.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the store documents (privacy policy, Play Console sheet, Play listing) true to the code.
 *
 * The documents once claimed that the app had no internet permission, recorded no video and made no
 * network request. Each case below fails when one of those claims, or the evidence behind a current
 * one, drifts: a permission the manifest grants but the policy table omits, a cloud host the code
 * calls but the documents never name, a stale phrase that comes back, a missing disclosure of the
 * MediaPipe usage-statistics client, an analytics or billing SDK that arrives unannounced, or a
 * policy citation that no longer points at a real file and line.
 *
 * Plain JUnit, no Robolectric. Files are read relative to the module directory (Gradle runs unit
 * tests from `app-android`) or the repository root.
 */
class StoreClaimsTest {
    private fun source(vararg candidates: String): File? =
        candidates.asSequence().map(::File).firstOrNull { it.isFile }

    private fun repoFile(path: String): File? = source("../$path", path)

    private fun repoText(path: String): String {
        val file = repoFile(path)
        assertNotNull("$path was not found from ${File(".").absoluteFile.normalize()}", file)
        return requireNotNull(file).readText()
    }

    private val policy by lazy { repoText(POLICY) }
    private val playSheet by lazy { repoText(PLAY_SHEET) }
    private val listing by lazy { repoText(LISTING) }

    @Test
    fun manifestPermissionsAppearInPolicyTable() {
        val manifest =
            repoText(MANIFEST).replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        val elements =
            Regex("<uses-permission\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL)
                .findAll(manifest)
                .map { it.groupValues[1] }
                .toList()
        fun nameOf(attributes: String) =
            requireNotNull(Regex("android:name=\"([^\"]+)\"").find(attributes)) {
                "uses-permission without a name: $attributes"
            }.groupValues[1]
        val removed = elements.filter { it.contains("tools:node=\"remove\"") }.map(::nameOf).toSet()
        val declared = elements.filterNot { it.contains("tools:node=\"remove\"") }.map(::nameOf).toSet()

        // The merged manifest is what ships; the release verifier holds its allow-list.
        val allowList =
            requireNotNull(
                    Regex("ALLOWED_PERMISSIONS\\s*=\\s*\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
                        .find(repoText(CONTRACT))
                ) {
                    "ALLOWED_PERMISSIONS not found in verify_manifest_contract.py"
                }
                .groupValues[1]
        val merged =
            Regex("\"(android\\.permission\\.[A-Z_]+)\"")
                .findAll(allowList)
                .map { it.groupValues[1] }
                .toSet()

        assertTrue("no uses-permission found in the manifest", declared.isNotEmpty())
        assertTrue("no android.permission found in ALLOWED_PERMISSIONS", merged.isNotEmpty())
        for ((label, document) in listOf("privacy policy" to policy, "Play sheet" to playSheet)) {
            val rows = document.lines().filter { it.trimStart().startsWith("|") }
            for (permission in declared + merged) {
                val short = permission.removePrefix("android.permission.")
                assertTrue(
                    "the $label permissions table has no row for `$short`",
                    rows.any { it.contains("`$short`") },
                )
            }
            for (permission in removed) {
                val short = permission.removePrefix("android.permission.")
                assertFalse(
                    "the $label permissions table lists `$short`, which the manifest removes",
                    rows.any { it.contains("`$short`") },
                )
            }
        }
    }

    @Test
    fun everyCloudHostNamedInPolicyAndPlaySheet() {
        val providers =
            listOf("../$PROVIDERS_DIR", PROVIDERS_DIR).map(::File).firstOrNull { it.isDirectory }
        assertNotNull("provider sources not found: $PROVIDERS_DIR", providers)
        val hosts =
            requireNotNull(providers)
                .listFiles { file -> file.isFile && file.name.endsWith(".kt") }
                .orEmpty()
                .flatMap { file ->
                    Regex("https://([a-z0-9.-]+)").findAll(file.readText()).map {
                        it.groupValues[1].trimEnd('.', '-')
                    }
                }
                .toSet()
        assertTrue("expected at least the four cloud providers, found $hosts", hosts.size >= 4)
        for ((label, document) in listOf("privacy policy" to policy, "Play sheet" to playSheet)) {
            for (host in hosts) {
                assertTrue("the $label does not name https://$host", document.contains("https://$host"))
            }
        }
    }

    @Test
    fun staleClaimsAbsent() {
        val stalePhrases =
            listOf(
                "no internet permission",
                "cannot make a network request",
                "No video is recorded",
                "open source",
                "open-source",
                "xyz.mdhv.formanalyser",
                "only permission",
                "downloadPoseModel",
                "free and open",
                "free, open",
            )
        val documents =
            listOf("privacy policy" to policy, "Play sheet" to playSheet, "Play listing" to listing)
        for ((label, document) in documents) {
            val lower = document.lowercase()
            for (phrase in stalePhrases) {
                assertFalse("the $label still says '$phrase'", lower.contains(phrase.lowercase()))
            }
            assertFalse(
                "the $label still uses the word FOSS",
                Regex("\\bFOSS\\b", RegexOption.IGNORE_CASE).containsMatchIn(document),
            )
        }
        // Settings -> About makes the same promise to the user, and once said "zero network calls".
        val about = repoText(ABOUT_SCREEN).lowercase()
        for (phrase in listOf("zero network calls", "nothing to opt out of")) {
            assertFalse("the About screen still says '$phrase'", about.contains(phrase))
        }
    }

    @Test
    fun listingUsesConfiguredApplicationId() {
        val applicationId =
            requireNotNull(Regex("applicationId\\s*=\\s*\"([^\"]+)\"").find(repoText(BUILD))) {
                    "applicationId not found in app-android/build.gradle.kts"
                }
                .groupValues[1]
        assertTrue("the privacy policy does not name $applicationId", policy.contains(applicationId))
        assertTrue("the Play sheet does not name $applicationId", playSheet.contains(applicationId))
    }

    @Test
    fun telemetryDisclosurePresent() {
        assertTrue(
            "the privacy policy must name the endpoint of MediaPipe's usage-statistics client",
            policy.contains("firebaselogging.googleapis.com"),
        )
        assertTrue(
            "the privacy policy needs its Third-party components section",
            policy.contains("## Third-party components"),
        )
        assertTrue(
            "the Play sheet must name the endpoint of MediaPipe's usage-statistics client",
            playSheet.contains("firebaselogging.googleapis.com"),
        )
    }

    @Test
    fun noForbiddenSdks() {
        val forbidden = Regex("firebase|crashlytics|play-services|billing|admob", RegexOption.IGNORE_CASE)
        val declaration = Regex("^\\s*(implementation|api|runtimeOnly|compileOnly)\\(")
        val declared = repoText(BUILD).lines().filter { declaration.containsMatchIn(it) }
        assertTrue("expected the dependency block, found ${declared.size} declarations", declared.size >= 10)
        for (line in declared) {
            assertFalse("forbidden SDK declared: ${line.trim()}", forbidden.containsMatchIn(line))
        }
        // `exclude(...)` lines are not declarations, so excluding a transitive artifact stays legal.

        // What actually ships: the locked release runtime graph, transitive artifacts included.
        val locked = repoText(LOCKFILE).lines().filter { it.contains("releaseRuntimeClasspath") }
        assertTrue("expected the locked release runtime graph, found ${locked.size} lines", locked.size >= 50)
        for (line in locked) {
            assertFalse(
                "forbidden artifact in the release runtime graph: ${line.substringBefore('=')}",
                forbidden.containsMatchIn(line.substringBefore('=')),
            )
        }
    }

    @Test
    fun everyCitedSourceLineExists() {
        val citations =
            Regex("<!--\\s*(C\\d+[a-z]?):(.*?)-->", RegexOption.DOT_MATCHES_ALL).findAll(policy).toList()
        assertTrue("the privacy policy cites only ${citations.size} sources", citations.size >= 60)
        val reference =
            Regex("([A-Za-z0-9_./-]+):(\\d+(?:-\\d+)?(?:,\\d+(?:-\\d+)?)*)")
        val problems = ArrayList<String>()
        for (citation in citations) {
            val id = citation.groupValues[1]
            val references = reference.findAll(citation.groupValues[2]).toList()
            if (references.isEmpty()) problems += "$id cites no file:line"
            for (match in references) {
                val path = match.groupValues[1]
                val file = repoFile(path)
                if (file == null) {
                    problems += "$id cites $path, which does not exist"
                    continue
                }
                val lineCount = file.readLines().size
                for (part in match.groupValues[2].split(',')) {
                    val bounds = part.split('-').map(String::toInt)
                    val from = bounds.first()
                    val to = bounds.last()
                    if (from < 1 || from > to || to > lineCount) {
                        problems += "$id cites $path:$part, but the file has $lineCount lines"
                    }
                }
            }
        }
        assertTrue("citations that do not resolve:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val POLICY = "docs/store/privacy-policy.md"
        const val PLAY_SHEET = "docs/store/play-console.md"
        const val LISTING = "fastlane/metadata/android/en-US/full_description.txt"
        const val MANIFEST = "app-android/src/main/AndroidManifest.xml"
        const val ABOUT_SCREEN = "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ui/SettingsScreens.kt"
        const val CONTRACT = "tools/release/verify_manifest_contract.py"
        const val BUILD = "app-android/build.gradle.kts"
        const val LOCKFILE = "app-android/gradle.lockfile"
        const val PROVIDERS_DIR = "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/ai/providers"
    }
}
