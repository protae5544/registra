import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.Failure
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import javax.inject.Inject

plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// ---- CI diagnostics: print Gradle task failures as GitHub annotations (safe to remove) ----
// Lets us read the real error through the GitHub API without downloading raw logs.

abstract class CiFailureReporter : BuildService<BuildServiceParameters.None>, OperationCompletionListener {
    private fun walk(f: Failure, sb: StringBuilder, depth: Int) {
        if (depth > 6) return
        sb.append("  ".repeat(depth)).append(f.message ?: "(no message)").append("\n")
        f.causes.forEach { walk(it, sb, depth + 1) }
    }

    private fun reportFiles(sb: StringBuilder) {
        try {
            val base = java.io.File("app/build")
            if (!base.exists()) return
            base.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                if (file.name == "missing_rules.txt") {
                    sb.append("\n[missing_rules.txt]\n").append(file.readText().take(2500)).append("\n")
                } else if (file.name.startsWith("lint-results") && file.extension == "xml") {
                    val text = file.readText()
                    val re = Regex("<issue\\s+id=\"([^\"]+)\"\\s+severity=\"(Error|Fatal)\"\\s+message=\"([^\"]*)\"", RegexOption.DOT_MATCHES_ALL)
                    var n = 0
                    re.findAll(text).forEach { m ->
                        if (n++ >= 15) return@forEach
                        val tail = text.substring(m.range.last, minOf(text.length, m.range.last + 600))
                        val loc = Regex("file=\"([^\"]+)\"\\s+line=\"(\\d+)\"").find(tail)
                        sb.append("[lint ").append(file.name).append("] ").append(m.groupValues[1]).append(": ")
                            .append(m.groupValues[3].take(220))
                            .append(if (loc != null) " @ " + loc.groupValues[1].substringAfter("/app/") + ":" + loc.groupValues[2] else "")
                            .append("\n")
                    }
                }
            }
        } catch (t: Throwable) {
            sb.append("(report scan failed: ").append(t.toString()).append(")\n")
        }
    }

    override fun onFinish(event: FinishEvent) {
        try {
            if (event !is TaskFinishEvent) return
            val r = event.result
            if (r !is TaskFailureResult) return
            val sb = StringBuilder()
            sb.append("Task ").append(event.descriptor.taskPath).append(" FAILED\n")
            r.failures.forEach { walk(it, sb, 0) }
            reportFiles(sb)
            val msg = sb.toString().take(7000)
                .replace("%", "%25").replace("\r", "").replace("\n", "%0A")
            println("::error title=Gradle failure::" + msg)
        } catch (t: Throwable) {
            println("::warning title=CiFailureReporter broke::" + t.toString())
        }
    }
}

interface CiInjectedOps {
    @get:Inject
    val registry: BuildEventsListenerRegistry
}

if (System.getenv("GITHUB_ACTIONS") == "true") {
    val ops = project.objects.newInstance(CiInjectedOps::class.java)
    val reporter = gradle.sharedServices.registerIfAbsent("ciFailureReporter", CiFailureReporter::class.java) {}
    ops.registry.onTaskCompletion(reporter)
}
