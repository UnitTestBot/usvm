package org.usvm.ts.pbt.coverage

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.BranchArmCoverage
import org.usvm.ts.pbt.backend.BranchCoverage
import org.usvm.ts.pbt.backend.FunctionCoverage
import org.usvm.ts.pbt.backend.SourceFileCoverage
import org.usvm.ts.pbt.backend.StatementCoverage

/** Decodes statement, function, and branch maps for one retained Istanbul file entry. */
internal class IstanbulSourceFileDecoder(
    file: JsonObject,
    private val path: String,
    reportKey: String,
    private val checkBudget: () -> Unit = {},
) {
    private val coveragePath = "coverage[$reportKey]"
    private val fileJson = IstanbulJsonValue(file, coveragePath)

    fun decode(): SourceFileCoverage = try {
        val statements = decodeStatements()
        val functions = decodeFunctions()
        val branches = decodeBranches()

        SourceFileCoverage(
            path = path,
            statements = statements,
            functions = functions,
            branches = branches,
        )
    } catch (error: CoverageArtifactException) {
        throw error
    } catch (error: IllegalArgumentException) {
        throw CoverageArtifactException.create(
            code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
            message = "Invalid Istanbul coverage for $path: ${error.message}",
            path = coveragePath,
            cause = error,
        )
    }

    private fun decodeStatements(): List<StatementCoverage> {
        val coverageMap = readCoverageMap(
            locationsName = "statementMap",
            hitsName = "s",
        )

        return coverageMap.entries.map { entry ->
            checkBudget()
            val location = coverageMap.location(entry).asRange()
            val hitCount = coverageMap.hitCount(entry).asHitCount()

            StatementCoverage(
                statementId = entry.id,
                location = location,
                hits = hitCount,
            )
        }
    }

    private fun decodeFunctions(): List<FunctionCoverage> {
        val coverageMap = readCoverageMap(
            locationsName = "fnMap",
            hitsName = "f",
        )

        return coverageMap.entries.map { entry ->
            checkBudget()
            decodeFunction(
                entry = entry,
                coverageMap = coverageMap,
            )
        }
    }

    private fun decodeFunction(
        entry: CoverageEntry,
        coverageMap: CoverageMap,
    ): FunctionCoverage {
        val functionJson = coverageMap.location(entry)
        val functionName = functionJson.optionalString(name = "name")
            ?.ifBlank { ANONYMOUS_FUNCTION_NAME }
            ?: ANONYMOUS_FUNCTION_NAME
        val declaration = functionJson.required(name = "decl").asRange()
        val body = functionJson.required(name = "loc").asRange()
        val hitCount = coverageMap.hitCount(entry).asHitCount()

        return FunctionCoverage(
            functionId = entry.id,
            name = functionName,
            declaration = declaration,
            body = body,
            hits = hitCount,
        )
    }

    private fun decodeBranches(): List<BranchCoverage> {
        val coverageMap = readCoverageMap(
            locationsName = "branchMap",
            hitsName = "b",
        )

        return coverageMap.entries.map { entry ->
            checkBudget()
            decodeBranch(
                entry = entry,
                coverageMap = coverageMap,
            )
        }
    }

    private fun decodeBranch(
        entry: CoverageEntry,
        coverageMap: CoverageMap,
    ): BranchCoverage {
        val branchJson = coverageMap.location(entry)
        val branchHitsJson = coverageMap.hitCount(entry)
        val branchArms = decodeBranchArms(branchJson, branchHitsJson)

        val type = branchJson.requiredString(name = "type")
        val location = branchJson.required(name = "loc").asRange()
        val arms = branchArms.locations.indices.map { armIndex ->
            checkBudget()
            BranchArmCoverage(
                location = branchArms.location(armIndex).asRange(),
                hits = branchArms.hitCount(armIndex).asHitCount(),
            )
        }

        return BranchCoverage(
            branchId = entry.id,
            type = type,
            location = location,
            arms = arms,
        )
    }

    private fun decodeBranchArms(
        branchJson: IstanbulJsonValue,
        branchHitsJson: IstanbulJsonValue,
    ): BranchArms {
        val locationsJson = branchJson.required(name = "locations")
        val locations = locationsJson.asArray()
        val hits = branchHitsJson.asArray()

        if (locations.size != hits.size) {
            throw IstanbulJsonValue.invalidReport(
                message = "Branch location and hit counts have different sizes",
                path = branchJson.path,
            )
        }

        return BranchArms(
            locations = locations,
            hits = hits,
            locationsPath = locationsJson.path,
            hitsPath = branchHitsJson.path,
        )
    }

    private fun readCoverageMap(
        locationsName: String,
        hitsName: String,
    ): CoverageMap {
        val locationsJson = fileJson.required(name = locationsName)
        val hitsJson = fileJson.required(name = hitsName)
        val locations = locationsJson.asObject()
        val hits = hitsJson.asObject()

        if (locations.keys != hits.keys) {
            throw IstanbulJsonValue.invalidReport(
                message = "Coverage map keys do not match hit counter keys (${locationsJson.path})",
                path = hitsJson.path,
            )
        }

        val seenIds = mutableSetOf<Int>()
        val entries = locations.keys.map { key ->
            checkBudget()
            val id = parseCoverageId(value = key, locationsPath = locationsJson.path)
            if (!seenIds.add(id)) {
                throw IstanbulJsonValue.invalidReport(
                    message = "Duplicate numeric coverage ID $id",
                    path = "${locationsJson.path}[$key]",
                )
            }

            CoverageEntry(id = id, key = key)
        }.sortedBy { entry -> entry.id }

        return CoverageMap(
            entries = entries,
            locations = locations,
            hits = hits,
            locationsPath = locationsJson.path,
            hitsPath = hitsJson.path,
        )
    }

    private fun parseCoverageId(value: String, locationsPath: String): Int {
        val coverageId = value.toIntOrNull()

        return coverageId?.takeIf { id -> id >= 0 }
            ?: throw IstanbulJsonValue.invalidReport(
                message = "Coverage ID must be a non-negative integer",
                path = "$locationsPath[$value]",
            )
    }

    private data class CoverageEntry(val id: Int, val key: String)

    private data class CoverageMap(
        val entries: List<CoverageEntry>,
        val locations: JsonObject,
        val hits: JsonObject,
        val locationsPath: String,
        val hitsPath: String,
    ) {
        fun location(entry: CoverageEntry): IstanbulJsonValue {
            return IstanbulJsonValue(
                value = locations.getValue(entry.key),
                path = "$locationsPath[${entry.key}]",
            )
        }

        fun hitCount(entry: CoverageEntry): IstanbulJsonValue {
            return IstanbulJsonValue(
                value = hits.getValue(entry.key),
                path = "$hitsPath[${entry.key}]",
            )
        }
    }

    private data class BranchArms(
        val locations: JsonArray,
        val hits: JsonArray,
        val locationsPath: String,
        val hitsPath: String,
    ) {
        fun location(index: Int) = IstanbulJsonValue(
            value = locations[index],
            path = "$locationsPath[$index]",
        )

        fun hitCount(index: Int) = IstanbulJsonValue(
            value = hits[index],
            path = "$hitsPath[$index]",
        )
    }

    private companion object {
        const val ANONYMOUS_FUNCTION_NAME = "<anonymous>"
    }
}
