package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.*
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefBase
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefKey
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.stepbible.textconverter.protocolagnosticutils.reversification.PA_ReversificationHandler
import org.stepbible.textconverter.protocolagnosticutils.reversification.SourceRef
import org.stepbible.textconverter.protocolagnosticutils.reversification.StandardRef
import java.io.File
import java.io.PrintWriter




/******************************************************************************/
/**
 * Creates the JSON needed to support our version of osis2mod when using
 * runtime reversification.
 *
 * @author ARA "Jamie" Jamieson
 */

/******************************************************************************/
object Osis_Osis2modRunTimeReversificationJsonHandler: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process ()
  {
    populateBibleStructure(InternalOsisDataCollection.getBibleStructure())
    m_BibleStructure.jswordMappings = PA_ReversificationHandler.getRuntimeReversificationMappings()
    outputJson(FileLocations.getOsis2ModSupportFilePath())
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Bible structure                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private class MyBibleStructure
  {
     var v11nName = ""

     val otBooks: MutableList<BookDetails> = mutableListOf() // Includes DC.
     val ntBooks: MutableList<BookDetails> = mutableListOf()

     var jswordMappings: List<Pair<SourceRef<String>, StandardRef<String>>> = listOf()


     fun output (writer: PrintWriter)
     {
       print(writer, "{\n")
       print(writer, "  'v11nName': '$v11nName',\n")

       outputBookDetails(writer,"otbooks", otBooks)
       outputBookDetails(writer,"ntbooks", ntBooks)

       print(writer, "  'vm': [")
       outputMaxVerses(writer, otBooks.subList(0, otBooks.size - 1) union ntBooks.subList(0, ntBooks.size - 1))
       print(writer, "    ],\n")

       outputMappings(writer)

       print(writer, "}\n")
    }


    fun outputBookDetails (writer: PrintWriter, name: String, content: List<BookDetails>)
    {
      print(writer, "  '$name': [\n")
      print(writer, content.joinToString(",\n") { it.bookDetailsToJson() })
      print(writer, "\n")
      print(writer, "  ],\n")
    }


    fun outputMappings (writer: PrintWriter)
    {
      print(writer, "  'jsword_mappings': [\n")
      val mappings = m_BibleStructure.jswordMappings.map { "${it.first.value}=${it.second.value}" }
      print(writer, "    \"" + mappings.joinToString("\",\n    \""))
      print(writer, "\"\n    ]\n")
    }


    fun outputMaxVerses (writer: PrintWriter, content: Set<BookDetails>)
    {
      val s = content.filter { it.chapMax > 0} .joinToString(",\n\n") { it.maxVersesToJson() }
      print(writer, s)
      print(writer, "\n")
    }
  }


  /****************************************************************************/
  private class BookDetails
  {
    var name = ""
    var osis = ""
    var prefAbbrev = ""
    var chapMax = 0
    val vm: MutableList<Int> = mutableListOf()

    fun bookDetailsToJson (): String
    {
      return """    {
      'name': '$name',
      'osis': '$osis',
      'prefAbbrev': '$prefAbbrev',
      'chapmax': $chapMax
    }"""
    }

    fun maxVersesToJson (): String
    {
      return """    // $name
    ${vm.joinToString(", ")}"""
    }
  }


  /****************************************************************************/
  /* Note that the requirements are that OT and DC books all get grouped into
     otBooks for output purposes. */

  private fun populateBibleStructure (bibleStructureUnderConstruction: BibleStructure)
  {
    m_BibleStructure.v11nName = ConfigData["stepVersificationScheme"] ?: ConfigData["calcModuleName"]!!
    populateBibleStructure1(bibleStructureUnderConstruction)
    m_BibleStructure.otBooks.add(BookDetails()) // Terminate with empty entry.
    m_BibleStructure.ntBooks.add(BookDetails()) // Terminate with empty entry.
  }


  /****************************************************************************/
  /* I make a few assumptions here ...

     - We may have an OT-only text, or an NT-only text, but we'll never have a
       DC-only text.

     - OT and DC books may be interleaved, or OT may be followed by DC.

     - NT and DC books are not interleaved, and nor are there any DC books after
       the NT.  Actually this latter constraint isn't one of my own making --
       the JSON file requires that OT and DC books all appear together under the
       'otbooks' heading. */
       
  private fun populateBibleStructure1 (bibleStructureUnderConstruction: BibleStructure)
  {
    val booksInOrder = BookOrdering.getOrder().filter { bibleStructureUnderConstruction.bookExists(it) }
    val firstOtBookIx = booksInOrder.indexOfFirst { BibleAnatomy.isOt(it) }
    val firstNtBookIx = booksInOrder.indexOfFirst { BibleAnatomy.isNt(it) }

    if (firstOtBookIx >= 0)
      populateBibleStructure(bibleStructureUnderConstruction, m_BibleStructure.otBooks, booksInOrder.subList(firstOtBookIx, if (-1 == firstNtBookIx) booksInOrder.size else firstNtBookIx))

    if (firstNtBookIx >= 0)
      populateBibleStructure(bibleStructureUnderConstruction, m_BibleStructure.ntBooks, booksInOrder.subList(firstNtBookIx, booksInOrder.size))
  }


  /****************************************************************************/
  private fun populateBibleStructure (bibleStructureUnderConstruction: BibleStructure, headers: MutableList<BookDetails>, bookNumbers: List<Int>)
  {
    for (bookNo in bookNumbers)
    {
      if (!Dbg.wantToProcessBook(bookNo)) continue

      val header = BookDetails()
      headers.add(header)

      header.name = BibleBookNamesOsis2modJsonFile.numberToShortName(bookNo)
      header.osis = BibleBookNamesOsis.numberToAbbreviatedName(bookNo)
      header.prefAbbrev = BibleBookNamesOsis2modJsonFile.numberToAbbreviatedName(bookNo)
//      header.name = m_CrosswireBookDetails[ubsAbbreviation]!!.fullName
//      header.osis = BibleBookNamesOsis.numberToAbbreviatedName(bookNo)
//      header.prefAbbrev = m_CrosswireBookDetails[ubsAbbreviation]!!.abbreviation
      header.chapMax = bibleStructureUnderConstruction.getLastChapterNo(bookNo)
      for (chapterNo in 1 .. header.chapMax) header.vm.add(bibleStructureUnderConstruction.getLastVerseNo(bookNo, chapterNo))
    }
  }


  /****************************************************************************/
  /* Can go if the versions without _OLD in their names are working. */

  private fun populateBibleStructure1_OLD (bibleStructureUnderConstruction: BibleStructure)
  {
    populateBibleStructure_OLD(bibleStructureUnderConstruction, m_BibleStructure.otBooks, BibleAnatomy.getBookNumberForStartOfOt(), BibleAnatomy.getBookNumberForEndOfOt())
    populateBibleStructure_OLD(bibleStructureUnderConstruction, m_BibleStructure.otBooks, BibleAnatomy.getBookNumberForStartOfDc(), BibleAnatomy.getBookNumberForEndOfDc(), true) // otbooks _is_ intended here -- see head of method comments.
    populateBibleStructure_OLD(bibleStructureUnderConstruction, m_BibleStructure.ntBooks, BibleAnatomy.getBookNumberForStartOfNt(), BibleAnatomy.getBookNumberForEndOfNt())
  }


  /****************************************************************************/
  /* Can go if the versions without _OLD in their names are working. */

  private fun populateBibleStructure_OLD (bibleStructureUnderConstruction: BibleStructure, headers: MutableList<BookDetails>, bookLow: Int, bookHigh: Int, skipMissingBooks: Boolean = true)
  {
    for (bookNo in bookLow .. bookHigh)
    {
      val missingBook = !bibleStructureUnderConstruction.bookExists(bookNo)
      if (skipMissingBooks && missingBook) continue
      if (!Dbg.wantToProcessBook(bookNo)) continue

      val header = BookDetails()
      headers.add(header)

      header.name = BibleBookNamesOsis2modJsonFile.numberToShortName(bookNo)
      header.osis = BibleBookNamesOsis.numberToAbbreviatedName(bookNo)
      header.prefAbbrev = BibleBookNamesOsis2modJsonFile.numberToAbbreviatedName(bookNo)

//      val ubsAbbreviation = BibleBookNamesUsx.numberToAbbreviatedName(bookNo)
//      header.name = m_CrosswireBookDetails[ubsAbbreviation]!!.fullName
//      header.osis = BibleBookNamesOsis.numberToAbbreviatedName(bookNo)
//      header.prefAbbrev = m_CrosswireBookDetails[ubsAbbreviation]!!.abbreviation

      header.chapMax = if (missingBook) 0 else bibleStructureUnderConstruction.getLastChapterNo(bookNo)
      for (chapterNo in 1 .. header.chapMax) header.vm.add(bibleStructureUnderConstruction.getLastVerseNo(bookNo, chapterNo))
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Common utilities                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun outputJson (filePath:String)
  {
    File(filePath).parentFile.mkdirs()

    try
    {
      PrintWriter(filePath).use { m_BibleStructure.output(it) }
    }
    catch (e:Exception)
    {
        throw StepExceptionWithStackTraceAbandonRun(e)
    }
  }


  /****************************************************************************/
  private fun print (writer: PrintWriter, s: String)
  {
    writer.print(s.replace("'", "\""))
  }


  /****************************************************************************/
  private val m_BibleStructure = MyBibleStructure()
}
