import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.dialogs.openFileDialog
import org.openrndr.dialogs.saveFileDialog
import org.openrndr.events.Event
import org.openrndr.panel.controlManager
import org.openrndr.panel.elements.*
import org.openrndr.panel.style.*
import org.openrndr.shape.CompositionDrawer
import org.openrndr.shape.Rectangle
import org.openrndr.svg.saveToFile

fun main() = application {
    configure {
        width = 768
        height = 700
        windowResizable = true
        title = "SOFTSTRIP"
    }

    program {
        window.presentationMode = PresentationMode.MANUAL
        val state = object {
            var offsetY = 0.0

            var density = 2
                set(value) {
                    field = value
                    generateBarcode()
                }

            var inputData = "Hello this is the softstrip.".toByteArray(charset = Charsets.US_ASCII)
                set(value) {
                    field = value
                    generateBarcode()
                }

            var addHeader = true
                set(value) {
                    field = value
                    generateBarcode()
                }

            var barcode = Barcode(inputData, density = density * 2)

            init {
                generateBarcode()
            }

            fun generateBarcode() {
                barcode = Barcode(inputData, density = density * 2)
                if (addHeader) {
                    barcode.prependHeader()
                }
                for (i in inputData) {
                    barcode.appendByte(i.toInt())
                }
            }
        }

        window.drop.listen {
            val f = it.files.first()
            try {
                state.inputData = f.readText().toByteArray(charset = Charsets.US_ASCII)
                state.offsetY = 0.0
            } catch(e :Throwable) {
                // TODO handle exceptions properly
                e.printStackTrace()
            }
        }

        val cm = controlManager {
            styleSheet(has class_ "container") {
                display = Display.FLEX
                width = 100.percent
                height = 100.percent
                background = Color.RGBa(ColorRGBa.BLUE)
                flexGrow = FlexGrow.Ratio(1.0)
                flexDirection = FlexDirection.Column
            }
            styleSheet(has class_ "inner-container") {
                display = Display.FLEX
                width = 100.percent
                background = Color.RGBa(ColorRGBa.RED)
                flexGrow = FlexGrow.Ratio(1.0)
                child(has type "canvas") {
                    width = 100.percent
                    height = 100.percent
                }
            }
            styleSheet(has class_ "toolbar") {
                display = Display.FLEX
                width = 100.percent
                height = 40.px
                background = Color.RGBa(ColorRGBa.GRAY)
                flexGrow = FlexGrow.Ratio(0.0)
                flexDirection = FlexDirection.Row
                child(has class_ "spacer") {
                    flexGrow = FlexGrow.Ratio(1.0)
                }
                child(has type "slider") {
                    width = 100.px
                    flexGrow = FlexGrow.Ratio(0.0)
                }
                child(has type "button") {
                    flexGrow = FlexGrow.Ratio(0.0)
                }
                child(has type "toggle") {
                    flexGrow = FlexGrow.Ratio(0.0)
                }
            }

            layout {
                div("container") {
                    div("inner-container") {
                        canvas {
                            val rowHeight = 8.0
                            listOf(this.keyboard.pressed, this.keyboard.repeated).listen {
                                when (it.key) {
                                    KEY_HOME -> state.offsetY = 0.0
                                    KEY_ARROW_DOWN -> state.offsetY -= rowHeight
                                    KEY_ARROW_UP -> state.offsetY += rowHeight
                                    KEY_PAGE_DOWN -> state.offsetY -= (height - 40.0 - rowHeight)
                                    KEY_PAGE_UP -> state.offsetY += (height - 40.0 - rowHeight)
                                }
                                requestRedraw()
                            }
                            this.mouse.scrolled.listen {
                                state.offsetY += it.rotation.y * rowHeight
                                it.cancelPropagation()
                                requestRedraw()
                            }
                            this.mouse.pressed.listen {
                                it.cancelPropagation()
                            }
                            this.mouse.dragged.listen {
                                state.offsetY += it.dragDisplacement.y
                                it.cancelPropagation()
                                requestRedraw()
                            }
                            userDraw = { drawer ->
                                val barcode = state.barcode
                                drawer.clear(ColorRGBa.PINK)
                                drawer.translate((width - barcode.byteWidth * 8 * 8) / 2.0, rowHeight + state.offsetY)
                                drawer.stroke = null

                                val whiteRectangles = mutableListOf<Rectangle>()
                                val blackRectangles = mutableListOf<Rectangle>()

                                for (y in 0 until barcode.pxrow) {
                                    for (x in 0 until barcode.byteWidth) {
                                        val offset = y * barcode.byteWidth + x
                                        val v = barcode.bitmap[offset].toInt()
                                        for (i in 0 until 8) {
                                            if ((v shr i) and 1 != 0) {
                                                blackRectangles.add(
                                                    Rectangle(
                                                        (x * 8 + (7 - i)) * 8.0,
                                                        y * rowHeight,
                                                        8.0,
                                                        rowHeight
                                                    )
                                                )
                                            } else {
                                                drawer.fill = ColorRGBa.WHITE
                                                whiteRectangles.add(
                                                    Rectangle(
                                                        (x * 8 + (7 - i)) * 8.0,
                                                        y * rowHeight,
                                                        8.0,
                                                        rowHeight
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                drawer.fill = ColorRGBa.WHITE
                                drawer.rectangles(whiteRectangles)
                                drawer.fill = ColorRGBa.BLACK
                                drawer.rectangles(blackRectangles)
                            }
                        }
                    }
                    div("toolbar") {
                        button {
                            label = "Import data"
                            clicked {
                                openFileDialog {
                                    try {
                                        state.inputData = it.readText().toByteArray(charset = Charsets.US_ASCII)
                                        state.offsetY = 0.0
                                    } catch(e : Throwable) {
                                        // TODO handle exceptions properly
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                        div("spacer") {}
                        toggle {
                            label = "header"
                            bind(state::addHeader)
                        }
                        slider {
                            label = "density"
                            precision = 0
                            range = Range(1.0, 4.0)
                            bind(state::density)
                        }
                        div("spacer") {}
                        button {
                            label = "Export SVG"
                            clicked {
                                val cd = CompositionDrawer()
                                cd.apply {
                                    val barcode = state.barcode
                                    for (y in 0 until barcode.pxrow) {
                                        for (x in 0 until barcode.byteWidth) {
                                            val offset = y * barcode.byteWidth + x
                                            val v = barcode.bitmap[offset].toInt()
                                            for (i in 0 until 8) {
                                                if ((v shr i) and 1 != 0) {
                                                    fill = ColorRGBa.BLACK
                                                    rectangle((x * 8 + (7 - i)) * 8.0, y * 8.0, 8.0, 8.0)
                                                } else {
                                                    fill = ColorRGBa.WHITE
                                                    rectangle((x * 8 + (7 - i)) * 8.0, y * 8.0, 8.0, 8.0)
                                                }
                                            }
                                        }
                                    }
                                }
                                val compo = cd.composition
                                saveFileDialog(supportedExtensions = listOf("svg")) {
                                    compo.saveToFile(it)
                                }
                            }
                        }
                    }
                }
            }
        }
        extend(cm)
    }
}

fun <T> List<Event<T>>.listen(listener: (T) -> Unit) {
    for (i in this) {
        i.listen(listener)
    }
}