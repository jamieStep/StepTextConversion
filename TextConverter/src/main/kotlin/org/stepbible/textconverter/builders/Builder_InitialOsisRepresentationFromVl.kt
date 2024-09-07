package org.stepbible.textconverter.builders

import org.stepbible.textconverter.osisonly.Osis_Utils
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.applicationspecificutils.*
import java.io.BufferedWriter
import java.io.File


/******************************************************************************/
/**
 * Converts verse-per-line format (VL) to internal OSIS form.
 *
 * Verse-per-line format is a simple way of representing texts, with one verse
 * per line, relatively simple additional features, and no cross-verse boundary
 * markup.  We presently have only a couple of texts which use it, but I guess
 * it may be useful to have a general-purpose converter to turn it directly
 * into OSIS.
 *
 * The processing relies upon the following configuration parameters:
 *
 * - stepVlCommentMarker: Defines the comment marker used in the VL (if any).
 *   Blank lines and lines starting with this marker are ignored.  May be left
 *   undefined if there are no comment lines.
 *
 * - stepVlLineFormat -- eg (?<bookAbbrev>.*?)\.(?<chapter>\d+)\.(?<verse>\d+)\t(?<text>.*)
 *   A regular expression which makes it possible to extract the various parts
 *   of each line.  You must define the named fields listed above.
 *
 * - You may also need to define #VernacularBookDetails -- for example
 *   #VernacularBookDetails GEN: Abbr: Gn.  (#VernacularBookDetails is used
 *   throughout the system to relate long / short / abbreviated vernacular
 *   names to the corresponding USX abbreviation.  You need one entry for each
 *   book which appears in the VL data (or which may be created as a result of
 *   reversification).  This may be omitted if the names which appear in the
 *   VL are in fact standard USX abbreviations.)
 *
 *
 * <span class='important'>IMPORTANT</span>: VerseLine is not a standard.  Each
 * instance of VerseLine which I've seen is different from every other, in terms
 * both of syntax (eg how the verse references are represented) and in terms of
 * what additional features it supports (footnotes, Strong's, etc).  The
 * processing here supports the few examples I've seen, but there's no guarantee
 * it will cope with the next one which comes along.  You therefore need to be
 * reconciled to the possible need to do additional coding work each time.  At
 * the same time, we don't really want the converter to grow and grow merely to
 * accommodate new texts, so you may possibly need to consider some way of
 * offloading this additional processing to something outside the converter --
 * a pre-processor of some kind.
 *
 *
 *
 *
 *
 * ## Preprocessing and filtering
 * VL processing does not create a Document representation of its data, and
 * therefore does not support XSLT preprocessing.
 *
 * It *does* support filtering by book, so you can limit the number of books
 * processed on a given run (particularly where you are debugging).
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_InitialOsisRepresentationFromVl: Builder()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Converting InputVl to initial OSIS"
  override fun commandLineOptions () = null





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun doIt ()
  {
    Dbg.withReportProgressMain(Builder_InitialOsisRepresentationFromUsx.banner(), ::doIt1)
  }

  
  /****************************************************************************/
  private fun doIt1 ()
  {
    /**************************************************************************/
    val inFile = BuilderUtils.getInputFiles(FileLocations.getInputVlFolderPath(),FileLocations.getFileExtensionForVl(), 1)[0]



    /**************************************************************************/
    val osisFilePath = BuilderUtils.createExternalOsisFolderStructure()
    m_Writer = File(osisFilePath).bufferedWriter()



    /**************************************************************************/
    val commentMarker = ConfigData["stepVlCommentMarker"] ?: "\u0001" // If no comment marker is defined, use a dummy value which we'll never actually encounter.
    val linePattern = ConfigData["stepVlLineFormat"]!!.toRegex()
    m_BookDescriptors = ConfigData.getBookDescriptors().associate { it.vernacularAbbreviation to it.ubsAbbreviation }



    /**************************************************************************/
    /* Accumulate parsed versions of all lines in all input files. */

    val allParsedLines = mutableListOf<ParsedLine>()
    fun gatherContent (filePath: String)
    {
      File(filePath).useLines { lines ->
        allParsedLines.addAll(
          lines
            .map { Builder_Master.processRegexes(it.trim(), ConfigData.getPreprocessingRegexes()) } // Apply regex to each line.
            .filter { it.isNotEmpty() && !it.startsWith(commentMarker) } // Ignore blank lines and comments.
            .map { ParsedLine(linePattern.matchEntire(it)!!, it) } // Convert to ParsedLine structure.
            .filter { Dbg.wantToProcessBook(it.m_UbsBookNo) } // Drop data for books we don't want to bother processing on this run.
        )
      }
    }

    gatherContent(inFile)
    val groupedLines = allParsedLines.groupBy { it.m_UbsBookNo }



    /**************************************************************************/
    ConfigData.makeBibleDescriptionAsItAppearsOnBibleList(groupedLines.keys.toList()) // The argument gives us the list of book numbers.
    writeln(Osis_Utils.fileHeader(groupedLines.keys.map { it }))
    groupedLines.keys.forEach { processBook(groupedLines[it]!!) }
    writeln(Osis_Utils.fileTrailer())
    m_Writer.close()



    /**************************************************************************/
    ExternalOsisDoc = Dom.getDocument(osisFilePath, retainComments = true)
    BuilderUtils.processXslt(ExternalOsisDoc)
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

   /****************************************************************************/
   private fun processBook (parsedLines: List<ParsedLine>)
   {
     val bookNo = BibleBookNamesUsx.abbreviatedNameToNumber(parsedLines[0].m_UbsBookAbbreviation)
     val bookNameOsis = BibleBookNamesOsis.numberToAbbreviatedName(bookNo)
     Dbg.reportProgress("Creating $bookNameOsis.")
     bookHeader(bookNameOsis)
     parsedLines.groupBy { Integer.parseInt(it.m_ChapterNo) }. forEach { processChapter(bookNameOsis, it.value) }
     bookTrailer()
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
    content = content.replace("¶", makeParaMarker()).trim()



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
    val sid = "$bookName.${parsedLine.m_ChapterNo}.${parsedLine.m_VerseNo}"
    val prefixPara = if (0 == paraPos) makeParaMarker() else ""
    //Dbg.d("      $prefixPara<verse sid=\"$sid\"/>$content<verse eid=\"$sid\"/>")
    writeln("  $prefixPara<verse osisID='$sid' sID='$sid'/>$content<verse eID='$sid'/>")
  }


  /****************************************************************************/
  private fun bookHeader (bookName: String)
  {
    writeln(""); writeln(""); writeln(""); writeln(""); writeln("")
    writeln("<!-- ================================================================================ -->")
    writeln("<div canonical='false' osisID='$bookName' type='book'>")
  }


  /****************************************************************************/
  private fun bookTrailer ()
  {
    writeln("</div>")
  }


  /****************************************************************************/
  private fun chapterHeader (bookName: String, chapterNo: String)
  {
    val id = "$bookName.$chapterNo"
    writeln("\n\n\n<chapter osisID='$id' sID='$id'>")
  }


  /****************************************************************************/
  private fun chapterTrailer ()
  {
    writeln("</chapter>")
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
    val id = "$owningChapter.$owningVerse"
    return "<note type='explanation' osisRef='$id' osisID='$id!${Globals.getUniqueExternal()}' n='▼'>$content</note>"
  }


  /****************************************************************************/
  /* Weird markup for a plain vanilla para -- forced on us by a distressing bug
     in STEPBible whereby paras occasionally go missing. */

  private fun makeParaMarker (): String
  {
    return "<hi type='bold'/><p/>"
  }


  /****************************************************************************/
  private fun writeln (text: String?)
  {
    if (null != text)
    {
      m_Writer.write(text)
      m_Writer.write("\n")
    }
  }


  /****************************************************************************/
  private class ParsedLine (mc: MatchResult, rawLine: String)
  {
    val m_UbsBookAbbreviation: String
    val m_VernacularBookAbbreviation = mc.groups["bookAbbrev"]!!.value
    val m_UbsBookNo: Int
    val m_ChapterNo = mc.groups["chapter"]!!.value
    val m_VerseNo = mc.groups["verse"]!!.value
    val m_Text = mc.groups["text"]!!.value
    val m_RawLine = rawLine

    init {
      m_UbsBookAbbreviation = m_BookDescriptors[m_VernacularBookAbbreviation] ?: m_VernacularBookAbbreviation
      m_UbsBookNo = BibleBookNamesUsx.abbreviatedNameToNumber(m_UbsBookAbbreviation)
    }
  }


  /****************************************************************************/
  private val C_Regex_AdditionalInformation = Regex("\\{(.*?)}")
  private val C_Regex_Catchword = Regex("(?i)Ͼ\\*catchword\\|\\s*(.*?)\\*Ͽ")
  private val C_Regex_FootnotePlaceHolder = Regex("<(N\\d+)>")
  private val C_Regex_QuoteInFootnote = Regex("(?i)Ͼ/quote\\|\\s*(.*?)/Ͽ")
  private lateinit var m_BookDescriptors: Map<String, String>
  private lateinit var m_Writer: BufferedWriter
}
