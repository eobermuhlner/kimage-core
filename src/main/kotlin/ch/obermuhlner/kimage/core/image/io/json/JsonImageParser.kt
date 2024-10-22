package ch.obermuhlner.kimage.core.image.io.json

import ch.obermuhlner.kimage.core.image.Channel
import ch.obermuhlner.kimage.core.image.Image
import ch.obermuhlner.kimage.core.image.MatrixImage
import ch.obermuhlner.kimage.core.matrix.DoubleMatrix

class JsonImageParser(private val tokenizer: JsonTokenizer) {

    fun parseImage(): Image {
        var width = 0
        var height = 0
        val data = mutableMapOf<String, List<Double>>()

        expectToken("{")
        while (true) {
            when (val key = tokenizer.nextToken()) {
                "\"width\"" -> {
                    expectToken(":")
                    width = tokenizer.nextToken()!!.toInt()
                }
                "\"height\"" -> {
                    expectToken(":")
                    height = tokenizer.nextToken()!!.toInt()
                }
                "\"data\"" -> {
                    expectToken(":")
                    expectToken("{")
                    while (true) {
                        val colorChannel = tokenizer.nextToken()
                        if (colorChannel == "}") break
                        expectToken(":")
                        val values = parseDoubleArray()
                        data[colorChannel!!.removeSurrounding("\"")] = values
                        if (tokenizer.nextToken() == "}") break
                    }
                }
                "}" -> break
                else -> throw IllegalArgumentException("Unexpected key: $key")
            }
            if (tokenizer.nextToken() == "}") break
        }

        val channels = data.keys.map { Channel.valueOf(it) }
        return MatrixImage(width, height, channels) { channel, _, _ ->
            val values = data[channel.name]!!
            DoubleMatrix.matrixOf(height, width) { index -> values[index]  }
        }
    }

    private fun parseDoubleArray(): List<Double> {
        expectToken("[")
        val list = mutableListOf<Double>()
        while (true) {
            val token = tokenizer.nextToken()
            if (token == "]") break
            list.add(token!!.toDouble())
            if (tokenizer.nextToken() == "]") break
        }
        return list
    }

    private fun expectToken(expected: String) {
        val token = tokenizer.nextToken()
        if (token != expected) {
            throw IllegalArgumentException("Expected token $expected but got $token")
        }
    }
}