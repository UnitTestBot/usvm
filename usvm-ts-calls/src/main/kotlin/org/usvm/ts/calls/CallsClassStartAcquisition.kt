package org.usvm.ts.calls

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/** Installation alone never establishes completeness; use the separately frozen acquisition receipt. */
internal fun CallsClassStartCase.verifyStartupAcquisition(manifestDirectory: Path) {
    val receiptName = acquisitionReceipt ?: return
    val relative = Path.of(receiptName)
    require(!relative.isAbsolute && !relative.startsWith("..")) { "Unsafe acquisition receipt path" }
    val directory = manifestDirectory.toRealPath()
    val receipt = directory.resolve(relative).toRealPath()
    require(receipt.startsWith(directory)) { "Acquisition receipt escapes its manifest root" }
    val bytes = Files.readAllBytes(receipt)
    require(callsArtifactSha256(bytes) == acquisitionSha256) { "Frozen acquisition receipt changed" }
    val value = CallsExperimentJson.json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
    require(value["projectRevision"]?.jsonPrimitive?.content == projectRevision) { "Acquisition revision differs" }
    val receiptRoot = value.getValue("sourceRoot").jsonPrimitive.content
    require(Path.of(receiptRoot).toRealPath() == Path.of(sourceRoot).toRealPath()) { "Acquisition root differs" }
    val declared = value.getValue("classes").jsonArray.map { it.jsonObject }
    val matchesOriginal = declared.any { owner ->
        owner["identity"]?.jsonPrimitive?.content == identity &&
            owner["sourceFile"]?.jsonPrimitive?.content == sourceFile &&
            owner["className"]?.jsonPrimitive?.content == className
    }
    require(matchesOriginal) { "Acquisition receipt does not contain the frozen original class" }
    val originalHash = value.getValue("originalClassSha256").jsonObject[sourceFile]?.jsonPrimitive?.content
    require(originalHash == inputSha256.getValue(sourceFile)) { "Original target source bytes differ" }
    val snapshot = value.getValue("snapshot").jsonObject.getValue("inputSha256").jsonObject
    val frozenInputsMatch = inputSha256.all { (name, digest) -> snapshot[name]?.jsonPrimitive?.content == digest }
    require(frozenInputsMatch) { "Startup input is outside its frozen acquired snapshot" }
    val receiptComplete = value["acquisitionComplete"]?.jsonPrimitive?.booleanOrNull == true
    require(!acquisitionComplete || receiptComplete) { "Installation without graph proof cannot qualify" }
}
