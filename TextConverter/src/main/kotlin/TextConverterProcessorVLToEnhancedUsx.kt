/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils
import java.io.File
import java.io.PrintWriter
import java.nio.file.Paths
import java.time.LocalDate


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

object TextConverterProcessorVLToEnhancedUsx : TextConverterProcessorBase
{
  /****************************************************************************/
  /* Some general comments ...

     VL (verse-per-Line) is a standard only in rather vague terms.  All flavours
     have a single verse per line, but they differ in whether or not they
     support comments, whether they may have blank lines, what additional
     features they support (eg footnotes), etc, etc.

     This means that in effect you may well need significantly different
     processing support for each flavour.  I'm hoping not, but given that
     latterly we have only had one or two VL-based texts to handle, it's not
     necessarily clear how things will pan out.

     At present, I am assuming that all texts can be processed by this present
     class, and will require little tailoring.  That, however, may be a forlorn
     hope: if, in future, we have to deal with many entirely different flavours,
     it may no longer be reasonable to do this processing within the general
     converter -- we may need a separate processor for each.

     Note that the remit of the present class is to convert directly to enhanced
     USX: I assume that the first phase of USX processing (which is concerned
     largely with initial validation and with massaging it into a form more
     amenable to later processing) is unnecessary, because I can organise for
     the output generated here already to look like enhanced USX.
   */

  /****************************************************************************/
  override fun banner (): String
  {
     return "Converting VL to enhanced USX"
  }


  /****************************************************************************/
  override fun pre (): Boolean
  {
    createFolders(listOf(StandardFileLocations.getRawInputFolderPath()))
    deleteFiles(listOf(Pair(StandardFileLocations.getRawInputFolderPath(), StandardFileLocations.getRawUsxFilePattern(null))))
    return true
  }


  /****************************************************************************/
  override fun runMe (): Boolean
  {
    return true
  }



  /****************************************************************************/
  /**
   * @{inheritDoc}
   */

  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
  }


  /****************************************************************************/
  override fun process (): Boolean
  {
    doIt()
    return true
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private interface LineReformatter { fun reformatLine (line: String): String }

  private object LineReformatter_Vanilla: LineReformatter { override fun reformatLine (line: String): String { return line } }


  /****************************************************************************/
  private object LineReformatter_SRGNT: LineReformatter
  {
    /**************************************************************************/
    /* SRGNT has 42001002^ where ^ is a space, and also numbers its books
       differently.  Corrects the book numbers and converts the string to
       canonical form. */

    private fun reformatReference (line: String): String
    {
       val id = line.split(" ")[0]
      val bookNo = id.substring(0, 2).toInt() + 1 // SRGNT numbers NT books from 40; USX numbers them from 41.
      val newId = String.format("%02d", bookNo) + "_" + BibleBookNamesUsx.numberToAbbreviatedName(bookNo) + "." + id.substring(2, 5) + "." + id.substring(5)
      return newId + "\t" + line.substring(line.indexOf(" ") + 1)
    }


    /**************************************************************************/
    override fun reformatLine (line: String): String
    {
      val res = reformatReference(line)
      return res.replace("˚", "°&#8201;") // SRGNT has an odd character in quite a lot of places.  Not sure what it's supposed to signify, but I've been asked to insert a small space before it.
    }
  }


  /****************************************************************************/
  private fun doIt ()
  {
    /**************************************************************************/
    val reformatter = if ("grc_srgnt" in StandardFileLocations.getRawInputFolderPath().lowercase()) LineReformatter_SRGNT else LineReformatter_Vanilla



     /**************************************************************************/
    File(StandardFileLocations.getVLFilePath()).useLines { line ->
      line.filter { it.isNotEmpty() && !it.trim().startsWith("#") }
        .map { reformatter.reformatLine(it) }
        .groupBy { it.substring(0, 2) }
        .forEach { processBook(it.value) }
    }
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
  private fun makeFootnote (substitutions: MutableMap<String, String>, key: String, owningChapter: String, owningVerse: Int): String
  {
    val content = substitutions[key]
    substitutions.remove(key)
    return "<note caller=\"▼\" style=\"f\"><char closed=\"false\" style=\"fr\">$owningChapter:$owningVerse</char><char closed=\"false\" style=\"ft\">$content</char></note>"
  }


  /****************************************************************************/
  private fun processBook (lines: List<String>)
  {
   val bookName = lines[0].substring(3, 6).uppercase()

    File(Paths.get(StandardFileLocations.getEnhancedUsxFolderPath(), "$bookName.usx").toString()).printWriter().use { out ->
      Out.m_Out = out
      bookHeader(bookName)
      lines.groupBy { it.substring(7, 10) }. forEach { processChapter(bookName, it.key.trimStart('0'), it.value) }
      bookTrailer()
    }
  }


  /****************************************************************************/
  private fun processChapter (bookName: String, chapterNo: String, lines: List<String>)
  {
    chapterHeader(bookName, chapterNo)
    lines.forEach { processVerse(bookName, chapterNo, it) }
    chapterTrailer()
  }


  /****************************************************************************/
  private fun processVerse (bookName: String, chapterNo: String, line: String)
  {
    /**************************************************************************/
    val idBits = line.split("\t")[0].split(".")
    val verseNo = idBits[2].toInt()
    val subverseNo = if (idBits.size > 3) MiscellaneousUtils.convertNumberToRepeatingString (idBits[3].toInt(), 'a', 'z') else ""



    /**************************************************************************/
    var content = line.split("\t")[1].trim()
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
      content = content.replace(C_Regex_FootnotePlaceHolder) { makeFootnote(substitutions, it.groups[1]!!.value, chapterNo, verseNo) }
    }

    content = content.replace("&", "&amp;")



    /**************************************************************************/
    if (substitutions.isNotEmpty())
    {
      Logger.error("VL failed to handle mappings: $line")
      return
    }


    /**************************************************************************/
    val sid = "$bookName $chapterNo:$verseNo$subverseNo"
    val prefixPara = if (0 == paraPos) "<para style='p'/>" else ""
    Out.println("      $prefixPara<verse sid=\"$sid\"/>$content<verse eid=\"$sid\"/>")
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
}

