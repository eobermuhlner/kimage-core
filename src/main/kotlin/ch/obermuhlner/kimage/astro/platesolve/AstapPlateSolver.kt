package ch.obermuhlner.kimage.astro.platesolve

import ch.obermuhlner.kimage.astro.annotate.WCSParser
import ch.obermuhlner.kimage.core.image.Image
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.pathString

class AstapPlateSolver(
    private val executable: String = "astap_cli"
) : PlateSolver {

    override fun solve(image: Image, inputFile: File, searchRa: Double?, searchDec: Double?, searchRadius: Double?): Map<String, String>? {
        val wcsFilename = Paths.get("astro-process", "reference.wcs").pathString
        
        val args = mutableListOf(executable, "-f", inputFile.path, "-o", wcsFilename)
        if (searchRa != null && searchDec != null) {
            args.add("-ra")
            args.add(searchRa.toString())
            args.add("-speed")
            args.add("slow") // slow search if we have coordinates but it fails? Or just provide hints.
            // ASTAP has many flags, but we keep it simple for now mirroring existing logic
        }

        val output = executeCommand(*args.toTypedArray())
        println(output)

        val wcsFile = File(wcsFilename)
        return if (wcsFile.exists()) {
            WCSParser.parse(wcsFile)
        } else {
            null
        }
    }

    private fun executeCommand(vararg arguments: String): String {
        val process = ProcessBuilder(*arguments)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            println("WARNING: Command '${arguments[0]}' exited with code $exitCode")
        }

        return output
    }
}
