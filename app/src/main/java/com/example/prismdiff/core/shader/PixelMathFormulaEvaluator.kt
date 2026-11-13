package com.example.prismdiff.core.shader

import com.example.prismdiff.core.model.CustomShaderDefinition
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 100% sandboxed mathematical expression parser & evaluator for the Custom Pixel-Math Shader Lab.
 *
 * Security & Safety Invariant: Imported or user-edited shader formulas are parsed into a strictly
 * typed arithmetic AST — zero reflection, zero code loading, zero arbitrary execution.
 *
 * Available variables:
 * - rA, gA, bA, lA : Slot A normalized channels & luminance [0..1]
 * - rB, gB, bB, lB : Slot B normalized channels & luminance [0..1]
 * - p1, p2, p3     : User-controlled interactive sliders
 *
 * Supported functions: abs, min, max, clamp, sqrt, sin, cos, step, smoothstep
 */
object PixelMathFormulaEvaluator {

    val builtInTemplates: List<CustomShaderDefinition> = listOf(
        CustomShaderDefinition(
            id = "chroma_key_diff",
            name = "Chroma Key Diff",
            description = "Amplifies color divergence while suppressing luminance exposure shifts.",
            expression = "clamp((abs((rA-lA)-(rB-lB)) + abs((gA-lA)-(gB-lB)) + abs((bA-lA)-(bB-lB))) * p1, 0, 1)",
            param1Label = "Chroma Gain",
            param1Default = 6f,
            param2Label = "Floor",
            param2Default = 0.02f,
            param3Label = "Mix",
            param3Default = 1f,
        ),
        CustomShaderDefinition(
            id = "luma_denoise_eval",
            name = "Luma Denoise Evaluator",
            description = "Isolates luminance residual between Stock vs GCam/Denoised outputs.",
            expression = "clamp(abs(lA - lB) * p1 - p2, 0, 1)",
            param1Label = "Boost",
            param1Default = 12f,
            param2Label = "Noise Gate",
            param2Default = 0.04f,
            param3Label = "Mix",
            param3Default = 1f,
        ),
        CustomShaderDefinition(
            id = "highlight_recovery",
            name = "Highlight Recovery Inspector",
            description = "Highlights areas where Slot B retains detail above Slot A highlight threshold.",
            expression = "step(0.82, lA) * clamp((lA - lB) * p1, 0, 1)",
            param1Label = "Recovery Gain",
            param1Default = 8f,
            param2Label = "ClipKnee",
            param2Default = 0.82f,
            param3Label = "Mix",
            param3Default = 1f,
        ),
        CustomShaderDefinition(
            id = "bandpass_diff",
            name = "Midtone Contrast Delta",
            description = "Emphasizes midtone contrast differences weighted by smoothstep envelope.",
            expression = "smoothstep(p2, 0.9, abs(lA - lB) * p1)",
            param1Label = "Sensitivity",
            param1Default = 5f,
            param2Label = "Edge Start",
            param2Default = 0.08f,
            param3Label = "Mix",
            param3Default = 1f,
        ),
    )

    sealed interface AstNode {
        data class Number(val value: Double) : AstNode
        data class Variable(val name: String) : AstNode
        data class UnaryNeg(val child: AstNode) : AstNode
        data class BinaryOp(val op: Char, val left: AstNode, val right: AstNode) : AstNode
        data class FunctionCall(val name: String, val args: List<AstNode>) : AstNode
    }

    data class EvalContext(
        var rA: Double = 0.0,
        var gA: Double = 0.0,
        var bA: Double = 0.0,
        var lA: Double = 0.0,
        var rB: Double = 0.0,
        var gB: Double = 0.0,
        var bB: Double = 0.0,
        var lB: Double = 0.0,
        var p1: Double = 1.0,
        var p2: Double = 0.0,
        var p3: Double = 1.0,
    )

    fun validate(expression: String): Result<AstNode> = runCatching {
        Parser(tokenize(expression)).parseExpression()
    }

    fun evaluateBuffer(
        expression: String,
        pixelsA: IntArray,
        pixelsB: IntArray,
        p1: Float,
        p2: Float,
        p3: Float,
    ): Result<IntArray> = validate(expression).map { ast ->
        val len = min(pixelsA.size, pixelsB.size)
        val out = IntArray(len)
        val ctx = EvalContext(p1 = p1.toDouble(), p2 = p2.toDouble(), p3 = p3.toDouble())
        for (i in 0 until len) {
            val a = pixelsA[i]
            val b = pixelsB[i]
            ctx.rA = ((a ushr 16) and 0xFF) / 255.0
            ctx.gA = ((a ushr 8) and 0xFF) / 255.0
            ctx.bA = (a and 0xFF) / 255.0
            ctx.lA = 0.2126 * ctx.rA + 0.7152 * ctx.gA + 0.0722 * ctx.bA

            ctx.rB = ((b ushr 16) and 0xFF) / 255.0
            ctx.gB = ((b ushr 8) and 0xFF) / 255.0
            ctx.bB = (b and 0xFF) / 255.0
            ctx.lB = 0.2126 * ctx.rB + 0.7152 * ctx.gB + 0.0722 * ctx.bB

            val v = eval(ast, ctx).coerceIn(0.0, 1.0)
            val mix = ctx.p3.coerceIn(0.0, 1.0)
            // Render cyan-magenta scientific heatmap blended with Slot A by p3
            val hr = min(1.0, v * 1.8)
            val hg = max(0.0, v * 1.4 - 0.35)
            val hb = max(0.25 * v, 1.0 - v * 1.4) * v
            val rr = ((ctx.rA * (1.0 - mix) + hr * mix) * 255.0).toInt().coerceIn(0, 255)
            val gg = ((ctx.gA * (1.0 - mix) + hg * mix) * 255.0).toInt().coerceIn(0, 255)
            val bb = ((ctx.bA * (1.0 - mix) + hb * mix) * 255.0).toInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        out
    }

    fun eval(node: AstNode, ctx: EvalContext): Double {
        return when (node) {
            is AstNode.Number -> node.value
            is AstNode.Variable -> when (node.name) {
                "rA" -> ctx.rA; "gA" -> ctx.gA; "bA" -> ctx.bA; "lA" -> ctx.lA
                "rB" -> ctx.rB; "gB" -> ctx.gB; "bB" -> ctx.bB; "lB" -> ctx.lB
                "p1" -> ctx.p1; "p2" -> ctx.p2; "p3" -> ctx.p3
                else -> throw IllegalArgumentException("Unknown variable '${node.name}'")
            }
            is AstNode.UnaryNeg -> -eval(node.child, ctx)
            is AstNode.BinaryOp -> {
                val l = eval(node.left, ctx)
                val r = eval(node.right, ctx)
                when (node.op) {
                    '+' -> l + r
                    '-' -> l - r
                    '*' -> l * r
                    '/' -> if (abs(r) < 1e-9) 0.0 else l / r
                    '^' -> l.pow(r)
                    else -> 0.0
                }
            }
            is AstNode.FunctionCall -> when (node.name) {
                "abs" -> abs(eval(node.args[0], ctx))
                "sqrt" -> sqrt(max(0.0, eval(node.args[0], ctx)))
                "sin" -> sin(eval(node.args[0], ctx))
                "cos" -> cos(eval(node.args[0], ctx))
                "min" -> min(eval(node.args[0], ctx), eval(node.args[1], ctx))
                "max" -> max(eval(node.args[0], ctx), eval(node.args[1], ctx))
                "clamp" -> eval(node.args[0], ctx).coerceIn(eval(node.args[1], ctx), eval(node.args[2], ctx))
                "step" -> if (eval(node.args[1], ctx) >= eval(node.args[0], ctx)) 1.0 else 0.0
                "smoothstep" -> {
                    val e0 = eval(node.args[0], ctx)
                    val e1 = eval(node.args[1], ctx)
                    val x = eval(node.args[2], ctx)
                    val t = if (abs(e1 - e0) < 1e-9) 0.0 else ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
                    t * t * (3.0 - 2.0 * t)
                }
                else -> throw IllegalArgumentException("Unsupported function '${node.name}'")
            }
        }
    }

    private fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                ch.isWhitespace() -> i++
                ch in "+-*/^()," -> {
                    tokens.add(ch.toString())
                    i++
                }
                ch.isDigit() || ch == '.' -> {
                    val start = i
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    tokens.add(input.substring(start, i))
                }
                ch.isLetter() || ch == '_' -> {
                    val start = i
                    while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '_')) i++
                    tokens.add(input.substring(start, i))
                }
                else -> throw IllegalArgumentException("Unexpected character '$ch'")
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<String>) {
        private var pos = 0

        fun parseExpression(): AstNode {
            val node = parseAddSub()
            if (pos < tokens.size) throw IllegalArgumentException("Unexpected token '${tokens[pos]}'")
            return node
        }

        private fun parseAddSub(): AstNode {
            var left = parseMulDiv()
            while (pos < tokens.size && (tokens[pos] == "+" || tokens[pos] == "-")) {
                val op = tokens[pos++][0]
                left = AstNode.BinaryOp(op, left, parseMulDiv())
            }
            return left
        }

        private fun parseMulDiv(): AstNode {
            var left = parsePower()
            while (pos < tokens.size && (tokens[pos] == "*" || tokens[pos] == "/")) {
                val op = tokens[pos++][0]
                left = AstNode.BinaryOp(op, left, parsePower())
            }
            return left
        }

        private fun parsePower(): AstNode {
            var left = parseUnary()
            while (pos < tokens.size && tokens[pos] == "^") {
                pos++
                left = AstNode.BinaryOp('^', left, parseUnary())
            }
            return left
        }

        private fun parseUnary(): AstNode {
            if (pos < tokens.size && tokens[pos] == "-") {
                pos++
                return AstNode.UnaryNeg(parseUnary())
            }
            return parsePrimary()
        }

        private fun parsePrimary(): AstNode {
            if (pos >= tokens.size) throw IllegalArgumentException("Unexpected end of expression")
            val tok = tokens[pos++]
            if (tok == "(") {
                val inner = parseAddSub()
                if (pos >= tokens.size || tokens[pos++] != ")") {
                    throw IllegalArgumentException("Missing closing ')'")
                }
                return inner
            }
            val num = tok.toDoubleOrNull()
            if (num != null) return AstNode.Number(num)

            if (pos < tokens.size && tokens[pos] == "(") {
                pos++
                val args = mutableListOf<AstNode>()
                if (pos < tokens.size && tokens[pos] != ")") {
                    while (true) {
                        args.add(parseAddSub())
                        if (pos < tokens.size && tokens[pos] == ",") {
                            pos++
                        } else break
                    }
                }
                if (pos >= tokens.size || tokens[pos++] != ")") {
                    throw IllegalArgumentException("Expected ')' after arguments to '$tok'")
                }
                validateArity(tok, args.size)
                return AstNode.FunctionCall(tok, args)
            }
            val allowedVars = setOf("rA", "gA", "bA", "lA", "rB", "gB", "bB", "lB", "p1", "p2", "p3")
            if (tok !in allowedVars) throw IllegalArgumentException("Unknown identifier '$tok'")
            return AstNode.Variable(tok)
        }

        private fun validateArity(fn: String, argCount: Int) {
            val expected = when (fn) {
                "abs", "sqrt", "sin", "cos" -> 1
                "min", "max", "step" -> 2
                "clamp", "smoothstep" -> 3
                else -> throw IllegalArgumentException("Unknown function '$fn'")
            }
            if (argCount != expected) {
                throw IllegalArgumentException("Function '$fn' expects $expected arguments, got $argCount")
            }
        }
    }
}
