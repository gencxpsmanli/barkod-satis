package com.barkodsatis.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ExcelYaziciTest {
    @Test fun gecerliXlsxUretir() {
        val out = ByteArrayOutputStream()
        ExcelYazici.yaz(out, listOf(
            ExcelYazici.Sayfa("Özet", listOf(listOf("Ürün", "Adet"), listOf("Çay <1kg> & şeker", 3), listOf("Su", 2.5))),
            ExcelYazici.Sayfa("Satışlar", listOf(listOf("A"))),
        ))
        val girdiler = HashMap<String, String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; girdiler[e.name] = z.readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue(girdiler.keys.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml", "xl/styles.xml")))
        val s1 = girdiler.getValue("xl/worksheets/sheet1.xml")
        assertTrue(s1.contains("Çay &lt;1kg&gt; &amp; şeker"))
        assertTrue(s1.contains("""<c r="B2"><v>3</v></c>"""))
        assertTrue(s1.contains("""<c r="A1" s="1" t="inlineStr">"""))
    }
}
