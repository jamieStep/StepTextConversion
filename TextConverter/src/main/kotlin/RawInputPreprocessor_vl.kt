/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.stepexception.StepException
import java.io.File
import java.io.PrintWriter
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.collections.HashMap


/******************************************************************************/
/**
 * Converts verse-per-line format (VL) to enhanced USX.
 *
 * Verse-per-line format is a simple way of representing texts, with one verse
 * per line, relatively simple additional markup, and no cross-verse boundary
 * markup.  We presently have only a couple of texts which use it, but I guess
 * it may be useful to have a general-purpose converter to turn it directly
 * into enhanced USX.
 *
 * @author ARA "Jamie" Jamieson
 */

class RawInputPreprocessor_vl: RawInputPreprocessor()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Checks the inputs, and then compares the timestamp on the input file with
     the timestamp recorded in the name of a file in the RawUsx folder. */

  override fun runMe (inputFolderPath: String): Boolean
  {
    val inputFiles = StepFileUtils.getMatchingFilesFromFolder(inputFolderPath, ".*".toRegex()).map { it.toString() }
    if (inputFiles.isEmpty()) throw StepException("Raw input folder is empty.")
    if (inputFiles.size > 1) throw StepException("More than one VL input file")
    return !sameTimestamp(inputFiles[0], StandardFileLocations.getRawUsxFolderPath(),"usx")
  }



  /****************************************************************************/
  override fun process (inputFolderPath: String)
  {
    doIt(StepFileUtils.getMatchingFilesFromFolder(inputFolderPath, ".*".toRegex()).map { it.toString() } [0])
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doIt (inputFilePath: String)
  {
    /**************************************************************************/
    val commentMarker = ConfigData["stepVlCommentMarker"] ?: "\u0001" // If no comment marker is defined, use a dummy value which we'll never actually encounter.
    val linePattern = ConfigData["stepVlLineFormat"]!!.toRegex()
    m_BookDescriptors = ConfigData.getBookDescriptors().associate { it.vernacularAbbreviation to it.ubsAbbreviation }
    m_InputFileDateStamp = getFormattedDateStamp(inputFilePath)



    /**************************************************************************/
    File(inputFilePath).useLines { lines ->
      lines.map { it.trim() }
           .filter { it.isNotEmpty() && !it.trim().startsWith(commentMarker) }
           .map { ParsedLine(linePattern.matchEntire(it)!!, it,this) }
           .groupBy { it.m_UbsBookNo }
           .forEach { processBook(it.value) }
    }
  }


   /****************************************************************************/
   private fun processBook (parsedLines: List<ParsedLine>)
   {
     val bookName = parsedLines[0].m_UbsBookAbbreviation

     File(Paths.get(StandardFileLocations.getRawUsxFolderPath(), makeUsxBookFileName(bookName, m_InputFileDateStamp, "usx")).toString()).printWriter().use { out ->
       Out.m_Out = out
       bookHeader(bookName)
       parsedLines.groupBy { Integer.parseInt(it.m_ChapterNo) }. forEach { processChapter(bookName, it.value) }
       bookTrailer()
     }
   }


  /****************************************************************************/
  private fun processChapter (bookName: String, parsedLines: List<ParsedLine>)
  {
    chapterHeader(bookName, parsedLines[0].m_ChapterNo)
    parsedLines.forEach { processVerse(bookName, it) }
    chapterTrailer()
  }


  /****************************************************************************/
  private fun processVerse (bookName: String, parsedLine: ParsedLine)
  {
    /**************************************************************************/
    var content = applyConfiguredTextMappings(parsedLine.m_Text)
    val paraPos = content.indexOf("¶")
    if (0 == paraPos) content = content.substring(1)
    content = content.replace("¶", "<para style='p'/>").trim()



    /**************************************************************************/
    val substitutions: MutableMap<String, String> = HashMap()
    C_Regex_AdditionalInformation.findAll(content).forEach {
      val parts = it.groups[1]!!.value.split("#|")
      substitutions[parts[0].trim()] = parts[1].trim()
    }



    /**************************************************************************/
    substitutions.filter { it.value.contains("Ͼ/quote") }. forEach { subst ->
      substitutions[subst.key] = substitutions[subst.key]!!.replace(C_Regex_QuoteInFootnote) { "'" + it.groups[1]!!.value + "'" }
    }

    substitutions.filter { it.value.contains("Ͼ*catchWord") }. forEach { subst ->
      substitutions[subst.key] = substitutions[subst.key]!!.replace(C_Regex_Catchword) { "'" + it.groups[1]!!.value + "'" }
    }
    //Dbg.d(substitutions.values)



    /**************************************************************************/
    val ix = content.indexOf("{")
    if (ix >= 0)
    {
      content = content.substring(0, ix).trim()
      content = content.replace(C_Regex_FootnotePlaceHolder) { makeFootnote(substitutions, it.groups[1]!!.value, parsedLine.m_ChapterNo, parsedLine.m_VerseNo) }
    }

    content = content.replace("&", "&amp;")



    /**************************************************************************/
    if (substitutions.isNotEmpty())
    {
      Logger.error("VL failed to handle mappings: ${parsedLine.m_RawLine}")
      return
    }


    /**************************************************************************/
    val sid = "$bookName $parsedLine.m_ChapterNo:$parsedLine.m_VerseNo"
    val prefixPara = if (0 == paraPos) "<para style='p'/>" else ""
    Out.println("      $prefixPara<verse sid=\"$sid\"/>$content<verse eid=\"$sid\"/>")
  }


  /****************************************************************************/
  private fun bookHeader (bookName: String)
  {
    val dt = LocalDate.now()
    Out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    Out.println("<!-- SourceFormat: VL.  Converted to USX by the STEP project: $dt -->")
    Out.println("<_X_usx version=\"2.0\">")
    Out.println("  <para style=\"ide\">UTF-8</para>")
    Out.println("  <_X_book code=\"$bookName\">")
  }


  /****************************************************************************/
  private fun bookTrailer ()
  {
    Out.println("  </_X_book>")
    Out.println("</_X_usx>")
  }


  /****************************************************************************/
  private fun chapterHeader (bookName: String, chapterNo: String)
  {
    Out.println("\n\n\n    <_X_chapter sid=\"$bookName $chapterNo\">")
  }


  /****************************************************************************/
  private fun chapterTrailer ()
  {
    Out.println("    </_X_chapter>")
  }


  /****************************************************************************/
  /* See if there are any text mappings which need to be applied to the text.
     Mappings should be a ||-separated list of individual mappings, each of
     the form x -> y.

     These individual elements are trimmed.  If you need to have a leading or
     trailing space, give it as {space}. */

  private fun applyConfiguredTextMappings (s: String): String
  {
    var mappings = ConfigData["stepVlTextMappings"] ?: return s
    mappings = mappings.trim()
    if (mappings.isEmpty()) return s

    var ss = s

    mappings.split("||").forEach {mapping ->
      var bits = mapping.split("->")
      bits = bits.map { it.trim().replace("(?i)\\{space}".toRegex(), " ") }
      ss = ss.replace(bits[0], bits[1])
    }

    return ss
  }


  /****************************************************************************/
  private fun makeFootnote (substitutions: MutableMap<String, String>, key: String, owningChapter: String, owningVerse: String): String
  {
    val content = substitutions[key]
    substitutions.remove(key)
    return "<note caller=\"▼\" style=\"f\"><char closed=\"false\" style=\"fr\">$owningChapter:$owningVerse</char><char closed=\"false\" style=\"ft\">$content</char></note>"
  }


  /****************************************************************************/
  private class ParsedLine (mc: MatchResult, rawLine: String, preprocessor: RawInputPreprocessor_vl)
  {
    val m_UbsBookAbbreviation: String
    val m_VernacularBookAbbreviation: String
    val m_UbsBookNo: Int
    val m_ChapterNo: String
    val m_VerseNo: String
    val m_Text: String
    val m_RawLine: String

    init {
      m_RawLine = rawLine
      m_ChapterNo = mc.groups["chapterNo"]!!.value
      m_VerseNo = mc.groups["verseNo"]!!.value
      m_Text = mc.groups["text"]!!.value
      m_VernacularBookAbbreviation = mc.groups["bookAbbrev"]!!.value
      m_UbsBookAbbreviation = preprocessor.m_BookDescriptors[m_VernacularBookAbbreviation] ?: m_VernacularBookAbbreviation
      m_UbsBookNo = BibleBookNamesUsx.abbreviatedNameToNumber(m_UbsBookAbbreviation)
    }
  }


  /****************************************************************************/
  private fun processFootnoteRepresentation (raw: String): String
  {
    return raw.trim()
  }

  
  /****************************************************************************/
  /* If you use PrintWriter.println, at least on Windows, all of the lines are
     terminated by CR, which looks messy.  This avoids that. */
     
  object Out
  {
    fun println (text: String) { m_Out.print(text); m_Out.print("\n") }
    lateinit var m_Out: PrintWriter
  }


  /****************************************************************************/
  private val C_Regex_AdditionalInformation = Regex("\\{(.*?)}")
  private val C_Regex_Catchword = Regex("(?i)Ͼ\\*catchword\\|\\s*(.*?)\\*Ͽ")
  private val C_Regex_FootnotePlaceHolder = Regex("<(N\\d+)>")
  private val C_Regex_QuoteInFootnote = Regex("(?i)Ͼ/quote\\|\\s*(.*?)/Ͽ")
  private lateinit var m_BookDescriptors: Map<String, String>
  private lateinit var m_InputFileDateStamp: String
}

