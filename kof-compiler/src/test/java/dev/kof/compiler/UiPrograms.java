package dev.kof.compiler;

/**
 * Programas Kof do E2E de {@code kof.ui} ({@code UiE2ETest}), hoisted de inline
 * para constantes. Vive fora da classe de teste (Fase 3 da arquitetura de
 * testes, {@code D-TEST-ARCHITECTURE-GO}) e do harness para que ambos fiquem
 * abaixo do limite de 500 linhas; os testes e o nome da classe seguem no
 * {@code UiE2ETest}.
 */
abstract class UiPrograms {

    static final String SRC_FIELDSET_IFRAME_MEDIA_HR_LINK_ON_ALL_TARGETS = """
            main() {
                var campo = Input("")
                var fs = Fieldset(listOf(campo))
                fs.setDisabled(true)

                var iframe = Iframe("https://kof.dev")
                iframe.setSrc("https://kof.dev/docs")

                var video = Video("movie.mp4")
                video.setControls(true)
                video.play()
                video.pause()

                var audio = Audio("song.mp3")
                audio.setControls(true)
                audio.play()
                audio.pause()

                var hr = Hr()
                hr.setClass("divisor")

                println("ok")
            }
            """;

    static final String SRC_CANVAS_CREATION = """
            main() {
                var c = Canvas(400, 300)
                c.setFill(Palette.blue)
                c.setStroke(Palette.red)
                c.setLineWidth(2)
                c.beginPath()
                c.moveTo(200, 150)
                c.arc(200, 150, 100, 0.0, 3.14159)
                c.closePath()
                c.fill()
                c.stroke()
                c.clearRect(0, 0, 400, 300)
                c.remove()
            }
            """;

    static final String SRC_UI003_REMAINING_LINKS_ON_ALL_TARGETS = """
            main() {
                var fs = Fieldset(listOf(Label("dentro")), "credenciais")
                var fr = Iframe("https://example.org")
                var v = Video("clip.mp4")
                var a = Audio("som.mp3")
                var h = Hr()
                fs.remove()
                fr.remove()
                v.remove()
                a.remove()
                h.remove()
                println("ok")
            }
            """;

    static final String SRC_LAYOUT_CONTAINERS = """
            main() {
                var l1 = Label("a")
                var l2 = Label("b")
                var col = Column(listOf(l1, l2))
                var row = Row(listOf(l1, l2))
                var style = Style(Palette.black, Palette.white, 16, 8)
                var view = View(style)
                view.bind(col)
                view.bind(row)
                var w = Window("Layout")
                w.bind(view)
                w.show()
            }
            """;

    static final String SRC_WINDOW_BEHAVIOR = """
            main() {
                var w1 = Window("Primeira")
                var w2 = Window("Segunda")
                var l1 = Label("a")
                var l2 = Label("b")
                w1.bind(l1)
                w2.bind(l2)
                w1.size(640, 480)
                w1.show()
                w2.show()
                w1.close()
            }
            """;

    static final String SRC_CANVAS_UI009_LINKS_ON_ALL_TARGETS = """
            main() {
                var c = Canvas(400, 300)
                c.save()
                c.setGlobalAlpha(0.5)
                c.transform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
                c.fillText("oi", 10, 20)
                println("w=" + (c.measureText("oi") >= 0.0))
                var img = Image("x.png")
                c.drawImage(img, 5, 5)
                c.restore()
                println("ok")
            }
            """;

    static final String SRC_THEMES = """
            main() {
                var dark = Theme.dark()
                println(dark.isDark())
                println(dark.background().toCss())
                println(dark.text().toCss())
                var light = Theme.light()
                println(light.isDark())
                println(light.background().toCss())
                println(light.text().toCss())
            }
            """;

    static final String SRC_UI006_EVENT_ACCESSORS_LINK_ON_ALL_TARGETS = """
            main() {
                var campo = Input("")
                campo.on("keydown", (e: Event) -> { println(e.key()) })
                campo.on("input", (e: Event) -> { println(e.value()) })
                campo.on("click", (e: Event) -> { println(e.x()) })
                campo.on("click", (e: Event) -> { println(e.target()) })
                campo.on("focus", (e: Event) -> { println(e.relatedTarget()) })
                println("ok")
            }
            """;

    static final String SRC_INPUT_ATTRS_LINKS_ON_ALL_TARGETS = """
            main() {
                var i = Input("oi")
                i.setName("usuario")
                i.setReadonly(true)
                var t = Textarea("x")
                t.setName("bio")
                t.setReadonly(true)
                println("ok")
            }
            """;

    static final String SRC_WIDGET_VISUAL_PRIMITIVES_LINK_ON_ALL_TARGETS = """
            main() {
                var card = Column(listOf(Label("x")))
                card.setBorder(Color(255, 0, 0), 2)
                card.setShadow(Color(0, 0, 0), 4, 12)
                card.setGradient(Color(255, 0, 0), Color(0, 0, 255), 90)
                card.setFlexBasis(300)
                card.setMaxWidth(600)
                println("ok")
            }
            """;

    static final String SRC_BUTTON_OPERATIONS = """
            main() {
                var b = Button("Salvar")
                println(b.text)
                b.text = "Salvando..."
                println(b.text)
                b.remove()
                var c = Button("Ok", () -> println("acabou"))
                println(c.text)
            }
            """;
}
