package app.fontflip.install

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Gera um AndroidManifest.xml binário (AXML) mínimo, sem aapt.
 * O layout dos chunks foi conferido contra o manifesto compilado do APK original.
 */
internal object AxmlWriter {
    private const val NS = "http://schemas.android.com/apk/res/android"
    private const val NONE = -1

    private const val TYPE_XML = 0x0003
    private const val TYPE_POOL = 0x0001
    private const val TYPE_RESMAP = 0x0180
    private const val TYPE_NS_START = 0x0100
    private const val TYPE_NS_END = 0x0101
    private const val TYPE_ELEM_START = 0x0102
    private const val TYPE_ELEM_END = 0x0103

    private const val VAL_STRING = 0x03
    private const val VAL_INT = 0x10
    private const val VAL_BOOL = 0x12

    // Atributos "android:" precisam vir primeiro no pool, na mesma ordem do resource map.
    private val resAttrs = listOf(
        "label" to 0x01010001,
        "hasCode" to 0x0101000c,
        "minSdkVersion" to 0x0101020c,
        "versionCode" to 0x0101021b,
        "versionName" to 0x0101021c,
        "targetSdkVersion" to 0x01010270,
    )

    private class Attr(val ns: Int, val name: Int, val raw: Int, val type: Int, val data: Int)

    fun manifest(
        pkg: String,
        versionName: String,
        label: String,
        versionCode: Int,
        minSdk: Int,
        targetSdk: Int,
    ): ByteArray {
        val strings = resAttrs.map { it.first } +
            listOf("android", NS, "package", "manifest", "uses-sdk", "application", pkg, versionName, label)
        fun s(v: String) = strings.indexOf(v)
        val android = s("android")
        val uri = s(NS)

        fun strAttr(ns: Int, name: String, value: String) = Attr(ns, s(name), s(value), VAL_STRING, s(value))
        fun intAttr(ns: Int, name: String, value: Int) = Attr(ns, s(name), NONE, VAL_INT, value)
        fun boolAttr(ns: Int, name: String, value: Boolean) =
            Attr(ns, s(name), NONE, VAL_BOOL, if (value) -1 else 0)

        val body = ByteArrayOutputStream()
        body.write(stringPool(strings))
        body.write(resourceMap())
        body.write(namespace(TYPE_NS_START, android, uri))
        body.write(
            startElement(
                s("manifest"),
                listOf(
                    strAttr(NONE, "package", pkg),
                    intAttr(uri, "versionCode", versionCode),
                    strAttr(uri, "versionName", versionName),
                ),
            ),
        )
        body.write(
            startElement(
                s("uses-sdk"),
                listOf(intAttr(uri, "minSdkVersion", minSdk), intAttr(uri, "targetSdkVersion", targetSdk)),
            ),
        )
        body.write(endElement(s("uses-sdk")))
        body.write(
            startElement(
                s("application"),
                listOf(strAttr(uri, "label", label), boolAttr(uri, "hasCode", false)),
            ),
        )
        body.write(endElement(s("application")))
        body.write(endElement(s("manifest")))
        body.write(namespace(TYPE_NS_END, android, uri))

        val payload = body.toByteArray()
        val out = buf(8 + payload.size)
        out.putShort(TYPE_XML.toShort())
        out.putShort(8)
        out.putInt(8 + payload.size)
        out.put(payload)
        return out.array()
    }

    private fun buf(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun stringPool(strings: List<String>): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        strings.forEachIndexed { i, str ->
            offsets[i] = data.size()
            val len = buf(2).putShort(str.length.toShort()).array()
            data.write(len)
            data.write(str.toByteArray(Charsets.UTF_16LE))
            data.write(byteArrayOf(0, 0))
        }
        while (data.size() % 4 != 0) data.write(0)

        val header = 28
        val start = header + 4 * strings.size
        val size = start + data.size()
        val b = buf(size)
        b.putShort(TYPE_POOL.toShort())
        b.putShort(header.toShort())
        b.putInt(size)
        b.putInt(strings.size) // stringCount
        b.putInt(0) // styleCount
        b.putInt(0) // flags (UTF-16)
        b.putInt(start) // stringsStart
        b.putInt(0) // stylesStart
        offsets.forEach { b.putInt(it) }
        b.put(data.toByteArray())
        return b.array()
    }

    private fun resourceMap(): ByteArray {
        val size = 8 + 4 * resAttrs.size
        val b = buf(size)
        b.putShort(TYPE_RESMAP.toShort())
        b.putShort(8)
        b.putInt(size)
        resAttrs.forEach { b.putInt(it.second) }
        return b.array()
    }

    private fun namespace(type: Int, prefix: Int, uri: Int): ByteArray {
        val b = buf(24)
        b.putShort(type.toShort())
        b.putShort(16)
        b.putInt(24)
        b.putInt(1) // linha
        b.putInt(NONE) // comentário
        b.putInt(prefix)
        b.putInt(uri)
        return b.array()
    }

    private fun startElement(name: Int, attrs: List<Attr>): ByteArray {
        val size = 36 + 20 * attrs.size
        val b = buf(size)
        b.putShort(TYPE_ELEM_START.toShort())
        b.putShort(16)
        b.putInt(size)
        b.putInt(1)
        b.putInt(NONE)
        b.putInt(NONE) // ns do elemento
        b.putInt(name)
        b.putShort(20) // attributeStart
        b.putShort(20) // attributeSize
        b.putShort(attrs.size.toShort())
        b.putShort(0) // idIndex
        b.putShort(0) // classIndex
        b.putShort(0) // styleIndex
        attrs.forEach {
            b.putInt(it.ns)
            b.putInt(it.name)
            b.putInt(it.raw)
            b.putShort(8)
            b.put(0)
            b.put(it.type.toByte())
            b.putInt(it.data)
        }
        return b.array()
    }

    private fun endElement(name: Int): ByteArray {
        val b = buf(24)
        b.putShort(TYPE_ELEM_END.toShort())
        b.putShort(16)
        b.putInt(24)
        b.putInt(1)
        b.putInt(NONE)
        b.putInt(NONE)
        b.putInt(name)
        return b.array()
    }
}
