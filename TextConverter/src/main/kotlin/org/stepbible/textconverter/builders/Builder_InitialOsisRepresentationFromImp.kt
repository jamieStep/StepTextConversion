package org.stepbible.textconverter.builders

import org.stepbible.textconverter.osisonly.Osis_Utils
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefBase
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesImp
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
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
 * This present class converts an IMP file to initial OSIS form.
 *
 * <span class='important'>CAUTION:</span>
 * Crosswire suggest there are limitations in the *mod2imp* utility used to
 * create .imp files, as a result of which they say they do not rely upon it.
 * It is not clear whether the issues they describe would affect us or not.
 *
 *
 *
 *
 *
 * ## Preprocessing and filtering
 * IMP processing does not create a Document representation of its data, and
 * therefore does not support XSLT preprocessing.
 *
 * It *does* support filtering by book, so you can limit the number of books
 * processed on a given run (particularly where you are debugging).
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_InitialOsisRepresentationFromImp: Builder(), ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Converting InputImp to initial OSIS"
  override fun commandLineOptions () = null





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Protected                                **/
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
     been forced to accumulate the data to a file and then read it back in
     again.
  */
  /****************************************************************************/


  /****************************************************************************/
  override fun doIt ()
  {
    /**************************************************************************/
    Rpt.report(level = 0, banner())
    val inFile = BuilderUtils.getInputFiles(FileLocations.getInputImpFolderPath(), FileLocations.getFileExtensionForImp(), 1)[0]



    /**************************************************************************/
    val osisFilePath = BuilderUtils.createExternalOsisFolderStructure()
    m_Writer = File(osisFilePath).bufferedWriter()



    /**************************************************************************/
    val lines = filterForBooksOfInterest(File(inFile).bufferedReader().readLines())
    lines.forEach { processLine(it, doOutput = false) } // Just determine which books we have.
    fileHeader()
    lines.forEach { processLine(it, doOutput = true) } // This time do the actual output.
    bookTrailer()
    fileTrailer()
    m_Writer.close()



    /**************************************************************************/
    ExternalOsisDoc = Dom.getDocument(osisFilePath, retainComments = true)
    tidyNodes()
    BuilderUtils.processXslt(ExternalOsisDoc)
    //Dbg.d(ExternalOsisDoc)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun filterForBooksOfInterest (lines: List<String>): List<String>
  {
    /**************************************************************************/
    /* We just accept all lines unless we're limiting the processing of books
       because we're debugging and have limited interests. */

    if (!Dbg.runningOnPartialCollectionOfBooksOnly())
      return lines



    /**************************************************************************/
    val res: MutableList<String> = mutableListOf()
     lines.indices
      .filter { lines[it].startsWith("\$\$\$") && "[" !in lines[it] }
      .filter { Dbg.wantToProcessBook(getBookNumberFromName(matchReferenceLine(lines[it]).groups["bookNameFull"]!!.value)) }
      .forEach { res.add(lines[it]); res.add(lines[it + 1]) }

    return res
  }


  /****************************************************************************/
  /* Useful for debug purposes -- you can breakpoint here to see what is being
     written out. */

  private fun appendText (text: String)
  {
    m_Writer.write(text)
  }


  /****************************************************************************/
  private fun fileHeader ()
  {
    appendText(Osis_Utils.fileHeader(m_BookNumbers.toList()))
  }


  /****************************************************************************/
  private fun fileTrailer () = appendText(Osis_Utils.fileTrailer())


  /****************************************************************************/
  private fun processLine (theLine: String, doOutput: Boolean)
  {
    /**************************************************************************/
    val line = theLine.trim()



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

    val m = matchReferenceLine(line)
    val bookNo = getBookNumberFromName(m.groups["bookNameFull"]!!.value)
    m_BookNumbers.add(bookNo)
    val chapter = m.groups["chapter"]!!.value.toInt()
    val verse = m.groups["verse"]!!.value.toInt()

    if (0 == chapter)
    {
      if (doOutput) bookHeader(bookNo)
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
   private fun bookHeader (bookNo:Int)
   {
     bookTrailer()
     m_ActiveBook = bookNo
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
  private fun getBookNumberFromName (name: String): Int
  {
    var bookNameFull = name
    if (bookNameFull.matches("I+ .+".toRegex())) bookNameFull = bookNameFull.replaceFirst(" ", "")
    if (bookNameFull.startsWith("IV ")) bookNameFull = bookNameFull.replaceFirst(" ", "")
    return BibleBookNamesImp.nameToNumber(bookNameFull)
  }


  /****************************************************************************/
  private fun matchReferenceLine (line: String): MatchResult
  {
    val m = C_BookChapterVerseHeader.matchEntire(line.substring(3)) ?: throw StepExceptionWithStackTraceAbandonRun("Invalid IMP line: $line")
    return m
  }


  /****************************************************************************/
  /* The individual lines are sometimes not ideal ...

     - div:bookGroup doesn't seem to be particular useful for our purposes,
       so I ditch it.

     - div:book positively gets in the way, because I want to generate my
       own version, so I ditch that too.

     - Ditto <chapter>.*/

  private val C_VerseContentRegexes = listOf("<div .+?type=.book.*?>\\s*".toRegex(), // This covers both book and bookGroup.
                                             "<chapter .+?>".toRegex())

  private fun modifyVerseContent (theLine: String): String
  {
    var line = theLine
    C_VerseContentRegexes.forEach { line = line.replace(it, "") }
    return BuilderUtils.processRegexes(line, ConfigData.getPreprocessingRegexes())
  }


  /****************************************************************************/
  /* Some nodes appear to be incomplete in some files.  In particular, <note>
     nodes often seem to lack certain essential attributes. */

  private fun tidyNodes ()
  {
    tidyNotes()
  }


  /****************************************************************************/
  /* In various IMP files I've come across <note> nodes which lack the 'n'
     and / or 'osisId' and / or 'osisRef' attributes.  Some of them also lack a
     'type', and here I assume that the type should be 'explanation'. */

  private fun tidyNotes ()
  {
    var counter = 0
    var verseRef = Ref.rd(0, 0, 0, 0)

    val allNodes = ExternalOsisDoc.getAllNodesBelow()
    allNodes.forEach {
      val nodeName = Dom.getNodeName(it)
      when (nodeName)
      {
        Osis_FileProtocol.tagName_note() ->
        {
          if ("n" !in it)
            it["n"] = "+"

          if ("osisID" !in it)
            it["osisID"] = "$verseRef!fref_${++counter}"

          if ("osisRef" !in it)
            it["osisRef"] = "$verseRef"

          if ("type" !in it) // If 'type' is lacking, assume 'explanation'.
            it["type"] = "explanation"
        }



        Osis_FileProtocol.tagName_verse() ->
        {
          if (Osis_FileProtocol.attrName_verseSid() in it)
            verseRef = Osis_FileProtocol.readRef(it[Osis_FileProtocol.attrName_verseSid()]!!)
        }
      }
    }
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
  private lateinit var m_Writer: BufferedWriter
  private var m_VerseRef = ""
}
