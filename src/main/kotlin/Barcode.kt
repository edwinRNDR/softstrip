// based on https://github.com/FozzTexx/Distripitor/blob/master/Barcode.m

@ExperimentalUnsignedTypes
class BarcodeFields {
    var data: UByte = 0.toUByte()
    var expansion1: UShort = 0.toUShort()
    var length: UShort = 0.toUShort()
    var checksum: UByte = 0.toUByte()
    var stripID = ByteArray(6)
    var sequence: UByte = 0.toUByte()
    var type: UByte = 0.toUByte()
    var expansion: UShort = 0.toUShort()
    var opSys: UByte = 0.toUByte()
    var numFiles: UByte = 0.toUByte()
}

class BarcodeFileEntry(val cauzinType: Byte, val fileType: Byte, val length: ByteArray, val name: ByteArray)

val StripStandard = 0
val StripSpecialKey = 1

val OSGeneric = 0
val OSCOLOS = 1
val OSAppleDOS33 = 0x10
val OSAppleProDOS = 0x11
val OSAppleCPM2 = 0x12
val OSMSDOS = 0x14
val OSMacintosh = 0x15
val OSReserved = 0x20

val MAXSTRIPWIDTH_MM = 16.8
val MAXSTRIPLENGTH_MM = 241.0
val MINBITWIDTH_MM = 0.15
val MINBITHEIGHT_MM = 0.20

class Barcode(val data: ByteArray, val density: Int = 4) {
    val stripWidth = MAXSTRIPLENGTH_MM
    val pixelWidth = 14 + density * 8
    val byteWidth = (pixelWidth + 7) / 8
    val rows = (data.size * 2) / density
    var bitmap = ByteArray(rows * byteWidth)
    val bitHeight = (stripWidth / pixelWidth) * (MINBITHEIGHT_MM / MINBITWIDTH_MM)

    var pxlen = 0
    var pxrow = 0
    var pxcol = 0

    init {
        val pxw = stripWidth / pixelWidth
    }

    val pxbuf = ByteArray(pixelWidth)

    fun insertPixelsAt(row: Int) {
        val len0 = (pxlen + 1) * byteWidth
        if (len0 > bitmap.size) {
            val newBitmap = ByteArray(bitmap.size + byteWidth * 20)
            bitmap.copyInto(newBitmap)
            bitmap = newBitmap
        }

        val offset = row * byteWidth
        val len1 = (pxlen - row) * byteWidth
        for (i in 0 until len1) {
            bitmap[offset + byteWidth + i] = bitmap[offset + i]
        }
        pxlen++

        for (i in 0 until pixelWidth step 8) {
            var byte = 0
            for (j in 0 until 8) {
                byte = byte shl 1
                if (i + j < pixelWidth) {
                    require(pxbuf[i + j] < 2)
                    byte = byte or (1 - pxbuf[i + j]).toInt()
                }
            }
            bitmap[offset + i / 8] = byte.toByte()
        }

    }

    fun appendPixels() {
        insertPixelsAt(pxrow)
    }

    fun appendData() {
        pxbuf[0] = 0
        pxbuf[1] = 0
        pxbuf[2] = 1
        pxbuf[3] = (pxrow and 1).toByte()
        pxbuf[4] = (1 - pxbuf[3]).toByte()
        pxbuf[pixelWidth - 5] = 1
        pxbuf[pixelWidth - 4] = 1
        pxbuf[pixelWidth - 3] = 0
        pxbuf[pixelWidth - 2] = 0
        pxbuf[pixelWidth - 1] = pxbuf[4]

        var parity = 0
        for (parpos in 0 until density * 2) {
            parity += pxbuf[parpos * 4 + 7]
        }
        pxbuf[pixelWidth - 7] = (parity and 1).toByte()
        pxbuf[pixelWidth - 6] = (1 - pxbuf[pixelWidth - 7]).toByte()

        for (parpos in 0 until density * 2) {
            parity += pxbuf[parpos * 4 + 9]
        }

        pxbuf[5] = (parity and 1).toByte()
        pxbuf[6] = (1 - pxbuf[5]).toByte()
        appendPixels()
    }

    fun appendByte(inbyte: Int) {
        var byte = inbyte
        for (clen in 0 until 8) {
            pxbuf[pxcol * 2 + 7] = (byte and 1).toByte()
            pxbuf[pxcol * 2 + 8] = (1 - pxbuf[pxcol * 2 + 7]).toByte()
            byte = byte shr 1
            pxcol++
            if (pxcol == density * 4) {
                appendData()
                pxrow++
                pxcol = 0
            }
        }
    }

    fun prependHeader() {
        for (i in 0 until pixelWidth) {
            pxbuf[i] = 1
        }
        for (i in 0 until 2) {
            pxbuf[i] = 0
            pxbuf[pixelWidth - 4 + i] = 0
        }
        for (i in 0 until 6) {
            pxbuf[4 + i] = 0
            pxbuf[pixelWidth - 12 + i] = 0
        }
        for (i in 0 until density - 4) {
            pxbuf[12 + i * 4] = pxbuf[13 + i * 4]
            pxbuf[pixelWidth - 16 - i * 4] = 0
            pxbuf[pixelWidth - 15 - i * 4] = 0
        }
        val len0 = 4 //ceil(2.0 / bitHeight).toInt()
        for (i in 0 until len0) {
            appendPixels()
            pxrow++
        }

        for (i in 0 until density * 4 step 8) {
            var c = 0x80
            for (clen in 0 until 8) {
                pxbuf[(i + clen) * 2 + 7] = (c and 1).toByte()
                pxbuf[(i + clen) * 2 + 8] = (1 - pxbuf[(i + clen) * 2 + 7]).toByte()
                c = c shr 1
            }
        }

        val len1 = 10 //ceil(4.0 / bitHeight).toInt()
        for (i in 0 until len1) {
            appendData()
            pxrow++
        }
    }
}