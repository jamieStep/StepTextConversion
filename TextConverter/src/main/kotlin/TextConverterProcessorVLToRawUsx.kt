/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
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

object TextConverterProcessorVLToEnhancedUsx : TextConverterProcessorBase()
{
  /****************************************************************************/
  override fun banner (): String
  {
     return "Converting VL to enhanced USX"
  }


  /****************************************************************************/
  override fun pre (): Boolean
  {
    createFolders(listOf(StandardFileLocations.getRawUsxFolderPath()))
    deleteFiles(listOf(Pair(StandardFileLocations.getRawUsxFolderPath(), "*.usx")))
    return true
  }


  /****************************************************************************/
  /* Run this so long as we have a vl.txt file.  Rename that file if you're
     happy with the raw USX generated on a previous run. */

  override fun runMe (): Boolean
  {
    return StepFileUtils.fileExists(StandardFileLocations.getVLFilePath())
  }



  /****************************************************************************/
  /**
   * @{inheritDoc}
   */

  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
    CommandLineProcessor.addCommandLineOption("debugLevel", 1, "Debug level -- 0 => no debug, larger numbers => increasing amounts of debug.", null, "0", false)
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
  private fun doIt ()
  {
    /**************************************************************************/
    /* This was originally written to handle rows like 01_Gen.001.001\t.  If we
       already have that, then we can take things as they are. */

    fun leaveAsIs (line: String) : String { return line }



    /**************************************************************************/
    /* SRGNT has 42001002^ where ^ is s space.  Convert to the original form.
       Actually, needs a little more work than that, because the VL as supplied
       numbres the books from 40, not 41.*/

    var bookNoIncrement = 0
    fun convertFromSRGNTForm (line: String): String
    {
      val id = line.split(" ")[0]
      val bookNo = id.substring(0, 2).toInt() + bookNoIncrement
      val newId = String.format("%02d", bookNo) + "_" + BibleBookNamesUsx.numberToAbbreviatedName(bookNo) + "." + id.substring(2, 5) + "." + id.substring(5)
      return newId + "\t" + line.substring(line.indexOf(" ") + 1)
    }



    /**************************************************************************/
    var preprocessor: ((String) -> String)? = null
    fun getPreprocessor (line: String): (String) -> String
    {
      if (null == preprocessor)
      {
        if (line.matches("\\d+\\s.*".toRegex()))
        {
          preprocessor = ::convertFromSRGNTForm
          if (line.startsWith("40")) bookNoIncrement = 1
        }
        else
          preprocessor = ::leaveAsIs
      }

      return preprocessor!!
    }



    /**************************************************************************/
    File(StandardFileLocations.getVLFilePath()).useLines {
      it.filter { it.isNotEmpty() && !it.trim().startsWith("#") }
        .map { getPreprocessor(it).invoke(it) }
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
    Out.println("<usx version=\"2.0\">")
    Out.println("  <para style=\"ide\">UTF-8</para>")
    Out.println("  <book code=\"$bookName\"/>")
  }


  /****************************************************************************/
  private fun bookTrailer ()
  {
    Out.println("</usx>")
  }


  /****************************************************************************/
  private fun chapterHeader (bookName: String, chapterNo: String)
  {
    Out.println("\n\n\n  <chapter sid=\"$bookName $chapterNo\"/>")
  }


  /****************************************************************************/
  private fun chapterTrailer ()
  {
    //Out.println("<!-- Node:  </chapter>-->")
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

    File(Paths.get(StandardFileLocations.getRawUsxFolderPath(), "$bookName.usx").toString()).printWriter().use { out ->
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
    substitutions.filter { it.value.contains("Ͼ/quote") }. forEach {
      substitutions[it.key] = substitutions[it.key]!!.replace(C_Regex_QuoteInFootnote) { "'" + it.groups[1]!!.value + "'" }
    }

    substitutions.filter { it.value.contains("Ͼ*catchWord") }. forEach {
      substitutions[it.key] = substitutions[it.key]!!.replace(C_Regex_Catchword) { "'" + it.groups[1]!!.value + "'" }
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
    Out.println("    $prefixPara<verse sid=\"$sid\"/>$content<verse eid=\"$sid\"/>")
  }


  /****************************************************************************/
  private fun processFootnoteRepresentation (raw: String): String
  {
    var res = raw.trim()
    return res
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

