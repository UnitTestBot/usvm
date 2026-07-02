package org.usvm.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.encodeToString
import org.usvm.mcp.json.ClassDto
import org.usvm.mcp.json.ListMethodsResultDto
import org.usvm.mcp.json.McpJson
import org.usvm.mcp.json.MethodDto
import org.usvm.mcp.json.ParamDto
import org.usvm.mcp.runTool
import org.usvm.mcp.textResult

fun Server.registerListMethodsTool(ctx: UsvmToolContext) {
    addTool(
        name = "list_methods",
        description = """
            Lists all classes and methods of a TypeScript file that are visible to the USVM
            symbolic machine. Use it first to discover exact 'class'/'method' argument values
            for the other tools. Top-level functions are listed under a synthetic default class.
        """.trimIndent(),
        inputSchema = toolSchema(required = listOf("file")) {
            fileProp()
        },
    ) { request ->
        runTool {
            val file = request.arguments.requireStringArg("file")
            val scene = ctx.scenes.getScene(file)
            val classes = scene.projectClasses.map { cls ->
                ClassDto(
                    name = cls.name,
                    methods = cls.methods.map { method ->
                        MethodDto(
                            name = method.name,
                            parameters = method.parameters.map { param ->
                                ParamDto(name = param.name, type = param.type.toString())
                            },
                            returnType = method.returnType.toString(),
                            stmtCount = runCatching { method.cfg.stmts.size }.getOrDefault(0),
                        )
                    },
                )
            }
            textResult(McpJson.encodeToString(ListMethodsResultDto(file = file, classes = classes)))
        }
    }
}
