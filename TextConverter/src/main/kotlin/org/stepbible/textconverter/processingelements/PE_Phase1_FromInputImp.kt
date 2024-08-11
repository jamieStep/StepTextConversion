/******************************************************************************/
package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.osisinputonly.Osis_Utils
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.utils.*
import java.io.BufferedWriter
import java.io.File


/******************************************************************************/
/**
 * Converts IMP format (.imp) to internal OSIS form.
 *
 * .imp is a simplified Crosswire textual representation of the content of a
 * module. It is basically a verse-per-line representation, along with some
 * additional markers to reflect book, chapter and verse divisions.  Within a
 * verse, the content occupies a single line, and comprises a collection of
 * OSIS tags.
 *
 * This present class converts an IMP file to OSIS form.
 * @author ARA "Jamie" Jamieson
 */

object PE_Phase1_FromInputImp: PE
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /*
     Implementation note
     ===================

     I was intending to accumulate the generated OSIS in either a StringBuffer
     or a mutable list.  However, both of these seemed to give up just short of
     the end on very large files, which perhaps suggests that they were not set
     up to hold as much data as these large files contained.  I have therefore
     been forced to write the data out to accumulate the data to a file and then
     read it back in again.
  */
  /****************************************************************************/


  /****************************************************************************/
  override fun banner () = "Converting InputImp to InternalOsis"
  override fun getCommandLineOptions(commandLineProcessor: CommandLineProcessor) {}
  override fun pre () { }


  /****************************************************************************/
  override fun process ()
  {
    val inFiles = StepFileUtils.getMatchingFilesFromFolder(FileLocations.getInputImpFolderPath(), ".*\\.${FileLocations.getFileExtensionForImp()}".toRegex()).map { it.toString() }
    if (1 != inFiles.size) throw StepException("Expecting precisely one IMP file, but ${inFiles.size} files available.")

    m_OsisFilePath = FileLocations.makeInputOsisFilePath()
    StepFileUtils.deleteFileOrFolder(FileLocations.getInputOsisFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getInputOsisFolderPath())
    m_OsisFilePath = FileLocations.makeInputOsisFilePath()
    m_OutputFile = File(m_OsisFilePath).bufferedWriter()

    File(inFiles[0]).bufferedReader().lines().forEach { processLine(it, doOutput = false) } // Just determine which books we have.
    fileHeader()
    File(inFiles[0]).bufferedReader().lines().forEach { processLine(it, doOutput = true) } // This time do the actual output.
    bookTrailer()
    fileTrailer()

    m_OutputFile.close()

    Phase1TextOutput = File(m_OsisFilePath).readText()
    //Dbg.outputText(Phase1TextOutput)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Useful for debug purposes -- you can breakpoint here to see what is being
     written out. */

  private fun appendText (text: String)
  {
    m_OutputFile.write(text)
  }


  /****************************************************************************/
  private fun fileHeader ()
  {
    ConfigData.makeBibleDescriptionAsItAppearsOnBibleList(m_BookNumbers.toList())
    appendText(Osis_Utils.fileHeader(m_BookNumbers.toList()))
  }


  /****************************************************************************/
  private fun fileTrailer () = appendText(Osis_Utils.fileTrailer())


  /****************************************************************************/
  private fun processLine (theLine: String, doOutput: Boolean)
  {
    /**************************************************************************/
    var line = theLine.trim()



    /**************************************************************************/
    /* Nothing worth appending. */

    if (line.isEmpty()) return



    /**************************************************************************/
    /* Verse content. */

    if (!line.startsWith("\$\$\$"))
    {
      if (doOutput) appendText(modifyVerseContent(line))
      return
    }



    /**************************************************************************/
    /* Special directives. */

    if ("[" in line) // This seems to occur only in IMP headers, and in the examples I've seen conveys no useful information.
      return



    /**************************************************************************/
    /* Book, chapter, or verse header.  Note that I don't know what happens
       with subverses -- I have no documentation, and am therefore reliant
       upon examples; and I have yet to see an example of a subverse. */

    line = line.substring(3)
    val m = C_BookChapterVerseHeader.matchEntire(line) ?: throw StepException("Invalid IMP line: $line")
    val bookNameFull = m.groups["bookNameFull"]!!.value
    m_BookNumbers.add(BibleBookNamesOsis.nameToNumber(bookNameFull))
    val chapter = m.groups["chapter"]!!.value.toInt()
    val verse = m.groups["verse"]!!.value.toInt()

    if (0 == chapter)
    {
      if (doOutput) bookHeader(bookNameFull)
      return
    }

    else if (0 == verse)
    {
      if (doOutput) chapterHeader(chapter)
      return
    }

    else
      if (doOutput) verseHeader(verse)
  }


   /****************************************************************************/
   private fun bookHeader (bookNameFull: String)
   {
     bookTrailer()
     m_ActiveBook = BibleBookNamesOsis.nameToNumber(bookNameFull)
     val abbreviatedName = Ref.rd(m_ActiveBook, 1, RefBase.C_DummyElement).toStringOsis("b")
     appendText("\n\n\n\n\n\n\n\n\n\n")
     appendText("<!-- ========================================================================================= -->\n")
     appendText("<!-- ========================================================================================= -->\n")
     appendText("<!-- ========================================================================================= -->\n")
     appendText("<!-- ========================================================================================= -->\n")
     appendText("<!-- ========================================================================================= -->\n")
     appendText("<!-- Book $abbreviatedName ================================================================================ -->\n")
     appendText("<div canonical='false' osisID='$abbreviatedName' type='book'>")
   }


   /****************************************************************************/
   private fun bookTrailer ()
   {
     if (-1 == m_ActiveBook) return
     chapterTrailer()
     appendText("</div>")
     m_ActiveBook = -1
   }


  /****************************************************************************/
  private fun chapterHeader (chapterNo: Int)
  {
    chapterTrailer()
    m_ActiveChapter = chapterNo
    val ref = makeOsisChapterRef()
    appendText("\n\n\n")
    appendText("<!-- Chapter $ref ========================================================================== -->\n")
    appendText("<chapter osisID='$ref' sID='$ref'>")
  }


  /****************************************************************************/
  private fun chapterTrailer ()
  {
    if (-1 == m_ActiveChapter) return
    verseTrailer()
    appendText("</chapter>")
    m_ActiveChapter = -1
  }


  /****************************************************************************/
  /* I'm not too sure about this.  I have seen a text which contains just
     <note> markers.  This is not really all that helpful, because it gives
     none of the additional information required in OSIS, like whether this
     is an explanatory note or a cross-reference.  In the one text where I have
     seen this problem, all the notes were, in fact, explanatory. */

  private fun modifyVerseContent (theLine: String): String
  {
    var line = theLine
    var n = 0

    while ("<note>" in line)
    {
      val replacement =  "<note n='+' osisID='$m_VerseRef!fref_${++n}' osisRef='$m_VerseRef' type='explanation'>"
      line = line.replaceFirst("<note>", replacement)
    }

    return line
  }


  /****************************************************************************/
  private fun verseHeader (verseNo: Int)
  {
    verseTrailer()
    m_ActiveVerse = verseNo
    appendText("\n")
    val ref = makeOsisVerseRef()
    appendText("<verse osisID='$ref' sID='$ref'/>")
    m_VerseRef = ref
  }


  /****************************************************************************/
  private fun verseTrailer ()
  {
    if (-1 == m_ActiveVerse) return
    val ref = makeOsisVerseRef()
    appendText("<verse eID='$ref'/>")
    m_ActiveVerse = -1
  }


  /****************************************************************************/
  private fun makeOsisChapterRef () = Ref.rd(m_ActiveBook, m_ActiveChapter, RefBase.C_DummyElement).toStringOsis("bc")
  private fun makeOsisVerseRef   () = Ref.rd(m_ActiveBook, m_ActiveChapter, m_ActiveVerse         ).toStringOsis()


  /****************************************************************************/
  private val C_BookChapterVerseHeader = Regex("(?<bookNameFull>(.+?)) (?<chapter>\\d+):(?<verse>\\d+)")
  private var m_ActiveBook = -1
  private var m_ActiveChapter = -1
  private var m_ActiveVerse = -1
  private val m_BookNumbers: MutableSet<Int> = mutableSetOf()
  private lateinit var m_OsisFilePath: String
  private lateinit var m_OutputFile: BufferedWriter
  private var m_VerseRef = ""
}

