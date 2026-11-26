package com.barkodsatis.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Kütüphanesiz, basit .xlsx yazıcı. Her sayfa bir satır listesidir; hücre Number ise sayı,
 * diğerleri metin olarak yazılır. İlk satır kalın başlık olarak biçimlenir.
 */
object ExcelYazici {

    data class Sayfa(val ad: String, val satirlar: List<List<Any?>>)

    fun yaz(cikis: OutputStream, sayfalar: List<Sayfa>) {
        ZipOutputStream(cikis).use { zip ->
            fun dosya(ad: String, icerik: String) {
                zip.putNextEntry(ZipEntry(ad)); zip.write(icerik.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            val n = sayfalar.size
            dosya("[Content_Types].xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                append("""<Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                append("</Types>")
            })
            dosya("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            dosya("xl/workbook.xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sayfalar.forEachIndexed { i, s -> append("""<sheet name="${xml(s.ad.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
                append("</sheets></workbook>")
            })
            dosya("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
                append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                append("</Relationships>")
            })
            // Stil 0: normal, 1: kalın
            dosya("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs></styleSheet>""")
            sayfalar.forEachIndexed { i, s -> dosya("xl/worksheets/sheet${i + 1}.xml", sayfaXml(s)) }
        }
    }

    private fun sutunHarf(i: Int): String {
        var n = i + 1; val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun sayfaXml(s: Sayfa) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        s.satirlar.forEachIndexed { r, satir ->
            append("""<row r="${r + 1}">""")
            satir.forEachIndexed { c, deger ->
                val ref = sutunHarf(c) + (r + 1)
                val stil = if (r == 0) """ s="1"""" else ""
                when (deger) {
                    null -> {}
                    is Number -> append("""<c r="$ref"$stil><v>$deger</v></c>""")
                    else -> append("""<c r="$ref"$stil t="inlineStr"><is><t xml:space="preserve">${xml(deger.toString())}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it == '\r' || it >= ' ' }
}
