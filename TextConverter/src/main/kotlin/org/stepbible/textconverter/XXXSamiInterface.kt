package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.*
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.stepexception.StepException
import java.io.File
import java.io.PrintWriter




/******************************************************************************/
/**
 * Generates a JSON file describing the verse structure of the text.
 *
 * At present this is, I think, somewhat experimental, so as well as describing
 * what we are doing here, let me give a few caveats.
 *
 * Originally the purpose of reversification was to generate something fully
 * aligned with NRSVA, so that STEP's added value features would work correctly.
 * However, to achieve that, we may have to restructure the text quite
 * significantly, and this results in a text which looks wrong to readers who
 * are familiar with the 'proper' version of the text and will usually also be
 * ruled out by the licence conditions imposed upon copyright texts.
 *
 * In an effort to circumvent this, we are changing the processing to be applied
 * by reversification where verses are being moved: we move them to their new
 * location, but we also leave a copy in the original location to give the
 * impression that the text has not been altered significantly.
 *
 * I still struggle with this (although we need to bear in mind that if _we_
 * don't apply reversification, osis2mod is quite likely to do so anyway, which
 * is no better).  This _may_ work where, for instance, we are moving large
 * blocks of verses out of Daniel and into deuterocanonical books, because you
 * never see the original verses (which we will leave in Daniel) at the same
 * time as you see the moved copies.  But there are still plenty of cases where
 * this processing cannot be applied (for example, with the Ten Commandments,
 * whose order differs in some texts: we can't both move the verses and leave
 * copies in situ, because the user will now see two copies of everything,
 * which will be confusing).  And in some texts, the psalms are renumbered,
 * so that Psalm 10 in the text actually corresponds to Psalm 11 in NRVSA,
 * and it seems to me there is no way of handling this at all -- if we
 * _don't_ duplicate the verses, then each psalm apparently contains the
 * wrong content; but if we _do_ duplicate them, then each ends up containing
 * the whole of _two_ psalms -- the one from the original text, and the one
 * which appears there as the result of moving things around.
 *
 * At the time of writing it is assumed that we can minimise these impacts
 * by having our own custom version of osis2mod (and I think also JSword) which
 * can adapt to different versification schemes on the fly, and which also
 * leaves verses in the order in which they are supplied (the official version
 * reorders verses which are passed to it in the wrong order).
 *
 * I remain very dubious that this is going to work: it is a hugely complicated
 * approach, will produce texts which we cannot share with third parties even
 * where copyright conditions permit, and I think will neither fully support
 * STEP's added value features nor produce a text which will be close enough
 * to the original to be acceptable either to users or to copyright holders.
 *
 * Be all that as it may, though, in order for these bespoke osis2mod and
 * JSword programs to work, we need a JSON file which describes the
 * actual versification structure present in the reversified text, and it is
 * the purpose of this present class to create it.
 *
 * @author ARA "Jamie" Jamieson
 */

object XXXSamiInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  const val C_UsingSamisInterface = true


  /****************************************************************************/
  /**
  * Outputs all relevant information to the JSON files.
  */

  fun process ()
  {
    if (!C_UsingSamisInterface) return
    BibleStructureTextUnderConstruction.populate(BibleBookAndFileMapperEnhancedUsx, wantWordCount = false, reportIssues = true) // Make sure we have up-to-date structural information.
    populateBibleStructure()
    populateReversificationMappings()
    outputJson(StandardFileLocations.getVersificationStructureForBespokeOsis2ModFilePath())
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Bible structure                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private class BibleStructure
  {
     var v11nName = ""

     val otBooks: MutableList<BookDetails> = mutableListOf()
     val ntBooks: MutableList<BookDetails> = mutableListOf()

     val jswordMappings: MutableList<Pair<RefKey, RefKey>> = mutableListOf()


     fun output (writer: PrintWriter)
     {
       print(writer, "{\n")
       print(writer, "  'v11nName': '$v11nName',\n")

       outputBookDetails(writer,"otBooks", otBooks)
       outputBookDetails(writer,"ntBooks", ntBooks)

       print(writer, "  'vm': [")
       outputMaxVerses(writer, otBooks)
       outputMaxVerses(writer, ntBooks)
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
      val mappings = m_BibleStructure.jswordMappings.map { "${Ref.rd(it.first).toStringUsx()}=${Ref.rd(it.second).toStringUsx()}" }
      print(writer, mappings.joinToString(",\n    "))
      print(writer, "    ]\n")
    }


    fun outputMaxVerses (writer: PrintWriter, content: List<BookDetails>)
    {
      val s = content.subList(0, content.size - 1).joinToString(",\n\n") { it.maxVersesToJson() }
      print(writer, "$s\n")
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
  /* Note that OT and DC books all get grouped into otbooks for output purposes.
     I don't necessarily understand why, but it _is_ what's required. */

  private fun populateBibleStructure ()
  {
    m_BibleStructure.v11nName = ConfigData.get("stepModuleName")!!
    populateBibleStructure(m_BibleStructure.otBooks, BibleAnatomy.getBookNumberForStartOfOt(), BibleAnatomy.getBookNumberForEndOfOt())
    populateBibleStructure(m_BibleStructure.otBooks, BibleAnatomy.getBookNumberForStartOfDc(), BibleAnatomy.getBookNumberForEndOfDc()) // otbooks _is_ intended here -- see head of method comments.
    populateBibleStructure(m_BibleStructure.ntBooks, BibleAnatomy.getBookNumberForStartOfNt(), BibleAnatomy.getBookNumberForEndOfNt())

    m_BibleStructure.otBooks.add(BookDetails()) // Terminate with empty entry.
    m_BibleStructure.ntBooks.add(BookDetails()) // Terminate with empty entry.
  }


  /****************************************************************************/
  private fun populateBibleStructure (headers: MutableList<BookDetails>, bookLow: Int, bookHigh: Int)
  {
    for (bookNo in bookLow .. bookHigh)
    {
      if (!BibleStructureTextUnderConstruction.hasBook(bookNo)) continue
      val header = BookDetails()
      headers.add(header)

      val ubsAbbreviation = BibleBookNamesUsx.numberToAbbreviatedName(bookNo)
      header.name = m_CrosswireBookDetails[ubsAbbreviation]!!.fullName
      header.osis = BibleBookNamesOsis.numberToAbbreviatedName(bookNo)
      header.prefAbbrev = m_CrosswireBookDetails[ubsAbbreviation]!!.abbreviation
      header.chapMax = BibleStructureTextUnderConstruction.getLastChapterNo(bookNo)
      for (chapterNo in 1 .. header.chapMax) header.vm.add(BibleStructureTextUnderConstruction.getLastVerseNo(bookNo, chapterNo))
    }
  }


  /****************************************************************************/
  private fun populateReversificationMappings ()
  {
    val renumbers = ReversificationData.getReferenceMappings()
    renumbers.forEach { m_BibleStructure.jswordMappings.add(Pair(it.key, it.value)) }

    val psalmTitles = ReversificationData.getAllAcceptedRows().filter { 0 != it.processingFlags.and(ReversificationData.C_StandardIsPsalmTitle) }
    psalmTitles.forEach { m_BibleStructure.jswordMappings.add(Pair(it.sourceRefAsRefKey, Ref.setV(it.standardRefAsRefKey, 0))) }

    m_BibleStructure.jswordMappings.sortBy { it.first }
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
        throw StepException(e)
    }
  }


  /****************************************************************************/
  private fun print (writer: PrintWriter, s: String)
  {
    writer.print(s.replace("'", "\""))
  }



  /****************************************************************************/
  private data class CrosswireBookDetails (val fullName: String, val abbreviation: String)


  /****************************************************************************/
  private val m_BibleStructure = BibleStructure()
  private val m_CrosswireBookDetails: MutableMap<String, CrosswireBookDetails?>  = mutableMapOf()


  /****************************************************************************/
  /* Sadly Crosswire use neither OSIS nor UBS book names and abbreviations, so
     I need a mapping from UBS to Crosswire.  Note that the two collections are
     not identical: UBS supports a number of DC books which Crosswire does not
     and Crosswire supports some which UBS does not (unless they're
     masquerading under different names and I haven't recognised the fact.) */

  init
  {
    m_CrosswireBookDetails["Gen"] = CrosswireBookDetails("Genesis", "Gen")
    m_CrosswireBookDetails["Exo"] = CrosswireBookDetails("Exodus", "Exo")
    m_CrosswireBookDetails["Lev"] = CrosswireBookDetails("Leviticus", "Lev")
    m_CrosswireBookDetails["Num"] = CrosswireBookDetails("Numbers", "Num")
    m_CrosswireBookDetails["Deu"] = CrosswireBookDetails("Deuteronomy", "Deu")
    m_CrosswireBookDetails["Jos"] = CrosswireBookDetails("Joshua", "Jos")
    m_CrosswireBookDetails["Jdg"] = CrosswireBookDetails("Judges", "Judg")
    m_CrosswireBookDetails["Rut"] = CrosswireBookDetails("Ruth", "Rut")
    m_CrosswireBookDetails["1Sa"] = CrosswireBookDetails("1 Samuel", "1Sa")
    m_CrosswireBookDetails["2Sa"] = CrosswireBookDetails("2 Samuel", "2Sa")
    m_CrosswireBookDetails["1Ki"] = CrosswireBookDetails("1 Kings", "1Ki")
    m_CrosswireBookDetails["2Ki"] = CrosswireBookDetails("2 Kings", "2Ki")
    m_CrosswireBookDetails["1Ch"] = CrosswireBookDetails("1 Chronicles", "1Ch")
    m_CrosswireBookDetails["2Ch"] = CrosswireBookDetails("2 Chronicles", "2Ch")
    m_CrosswireBookDetails["Ezr"] = CrosswireBookDetails("Ezra", "Ezr")
    m_CrosswireBookDetails["Neh"] = CrosswireBookDetails("Nehemiah", "Neh")
    m_CrosswireBookDetails["Est"] = CrosswireBookDetails("Esther", "Est")
    m_CrosswireBookDetails["Job"] = CrosswireBookDetails("Job", "Job")
    m_CrosswireBookDetails["Psa"] = CrosswireBookDetails("Psalms", "Psa")
    m_CrosswireBookDetails["Pro"] = CrosswireBookDetails("Proverbs", "Pro")
    m_CrosswireBookDetails["Ecc"] = CrosswireBookDetails("Ecclesiastes", "Ecc")
    m_CrosswireBookDetails["Sng"] = CrosswireBookDetails("Song of Solomon", "Song")
    m_CrosswireBookDetails["Isa"] = CrosswireBookDetails("Isaiah", "Isa")
    m_CrosswireBookDetails["Jer"] = CrosswireBookDetails("Jeremiah", "Jer")
    m_CrosswireBookDetails["Lam"] = CrosswireBookDetails("Lamentations", "Lam")
    m_CrosswireBookDetails["Ezk"] = CrosswireBookDetails("Ezekiel", "Eze")
    m_CrosswireBookDetails["Dan"] = CrosswireBookDetails("Daniel", "Dan")
    m_CrosswireBookDetails["Hos"] = CrosswireBookDetails("Hosea", "Hos")
    m_CrosswireBookDetails["Jol"] = CrosswireBookDetails("Joel", "Joe")
    m_CrosswireBookDetails["Amo"] = CrosswireBookDetails("Amos", "Amo")
    m_CrosswireBookDetails["Oba"] = CrosswireBookDetails("Obadiah", "Obd")
    m_CrosswireBookDetails["Jon"] = CrosswireBookDetails("Jonah", "Jon")
    m_CrosswireBookDetails["Mic"] = CrosswireBookDetails("Micah", "Mic")
    m_CrosswireBookDetails["Nam"] = CrosswireBookDetails("Nahum", "Nah")
    m_CrosswireBookDetails["Hab"] = CrosswireBookDetails("Habakkuk", "Hab")
    m_CrosswireBookDetails["Zep"] = CrosswireBookDetails("Zephaniah", "Zep")
    m_CrosswireBookDetails["Hag"] = CrosswireBookDetails("Haggai", "Hag")
    m_CrosswireBookDetails["Zec"] = CrosswireBookDetails("Zechariah", "Zec")
    m_CrosswireBookDetails["Mal"] = CrosswireBookDetails("Malachi", "Mal")
    m_CrosswireBookDetails["Mat"] = CrosswireBookDetails("Matthew", "Mat")
    m_CrosswireBookDetails["Mrk"] = CrosswireBookDetails("Mark", "Mar")
    m_CrosswireBookDetails["Luk"] = CrosswireBookDetails("Luke", "Luk")
    m_CrosswireBookDetails["Jhn"] = CrosswireBookDetails("John", "Joh")
    m_CrosswireBookDetails["Act"] = CrosswireBookDetails("Acts", "Act")
    m_CrosswireBookDetails["Rom"] = CrosswireBookDetails("Romans", "Rom")
    m_CrosswireBookDetails["1Co"] = CrosswireBookDetails("1 Corinthians", "1Cor")
    m_CrosswireBookDetails["2Co"] = CrosswireBookDetails("2 Corinthians", "2Cor")
    m_CrosswireBookDetails["Gal"] = CrosswireBookDetails("Galatians", "Gal")
    m_CrosswireBookDetails["Eph"] = CrosswireBookDetails("Ephesians", "Eph")
    m_CrosswireBookDetails["Php"] = CrosswireBookDetails("Philippians", "Phili")
    m_CrosswireBookDetails["Col"] = CrosswireBookDetails("Colossians", "Col")
    m_CrosswireBookDetails["1Th"] = CrosswireBookDetails("1 Thessalonians", "1Th")
    m_CrosswireBookDetails["2Th"] = CrosswireBookDetails("2 Thessalonians", "2Th")
    m_CrosswireBookDetails["1Ti"] = CrosswireBookDetails("1 Timothy", "1Ti")
    m_CrosswireBookDetails["2Ti"] = CrosswireBookDetails("2 Timothy", "2Ti")
    m_CrosswireBookDetails["Tit"] = CrosswireBookDetails("Titus", "Tit")
    m_CrosswireBookDetails["Phm"] = CrosswireBookDetails("Philemon", "Phile")
    m_CrosswireBookDetails["Heb"] = CrosswireBookDetails("Hebrews", "Heb")
    m_CrosswireBookDetails["Jas"] = CrosswireBookDetails("James", "Jam")
    m_CrosswireBookDetails["1Pe"] = CrosswireBookDetails("1 Peter", "1Pe")
    m_CrosswireBookDetails["2Pe"] = CrosswireBookDetails("2 Peter", "2Pe")
    m_CrosswireBookDetails["1Jn"] = CrosswireBookDetails("1 John", "1Jo")
    m_CrosswireBookDetails["2Jn"] = CrosswireBookDetails("2 John", "2Jo")
    m_CrosswireBookDetails["3Jn"] = CrosswireBookDetails("3 John", "3Jo")
    m_CrosswireBookDetails["Jud"] = CrosswireBookDetails("Jude", "Jude")
    m_CrosswireBookDetails["Rev"] = CrosswireBookDetails("Revelation of John", "Rev")
    m_CrosswireBookDetails["Tob"] = CrosswireBookDetails("Tobit", "Tob")
    m_CrosswireBookDetails["Jdt"] = CrosswireBookDetails("Judith", "Jdt")
    m_CrosswireBookDetails["Esg"] = CrosswireBookDetails("Additions to Esther", "Add Est")
    m_CrosswireBookDetails["Wis"] = CrosswireBookDetails("Wisdom of Solomon", "Wis")
    m_CrosswireBookDetails["Sir"] = CrosswireBookDetails("Sirach", "Sir")
    m_CrosswireBookDetails["Bar"] = CrosswireBookDetails("Baruch", "Bar")
    m_CrosswireBookDetails["Lje"] = CrosswireBookDetails("Epistle of Jeremiah", "Ep Jer")
    m_CrosswireBookDetails["S3y"] = CrosswireBookDetails("Prayer of Azariah", "Pr Azar")
    m_CrosswireBookDetails["Sus"] = CrosswireBookDetails("Susanna", "Sus")
    m_CrosswireBookDetails["Bel"] = CrosswireBookDetails("Bel and the Dragon", "Bel")
    m_CrosswireBookDetails["1Ma"] = CrosswireBookDetails("1 Maccabees", "1 Macc")
    m_CrosswireBookDetails["2Ma"] = CrosswireBookDetails("2 Maccabees", "2 Macc")
    m_CrosswireBookDetails["3Ma"] = CrosswireBookDetails("3 Maccabees", "3 Macc")
    m_CrosswireBookDetails["4Ma"] = CrosswireBookDetails("4 Maccabees", "4 Macc")
    m_CrosswireBookDetails["Man"] = CrosswireBookDetails("Prayer of Manasseh", "Pr Man")
    m_CrosswireBookDetails["1Es"] = CrosswireBookDetails("1 Esdras", "1 Esd")
    m_CrosswireBookDetails["2Es"] = CrosswireBookDetails("2 Esdras", "2 Esd")
    m_CrosswireBookDetails["Ps2"] = CrosswireBookDetails("Psalm 151", "Ps151")
    m_CrosswireBookDetails["Pss"] = CrosswireBookDetails("Psalms of Solomon", "Ps Sol")
    m_CrosswireBookDetails["Lao"] = CrosswireBookDetails("Epistle to the Laodiceans", "Ep Lao")
    m_CrosswireBookDetails["Jsa"] = null
    m_CrosswireBookDetails["Jdb"] = null
    m_CrosswireBookDetails["Tbs"] = null
    m_CrosswireBookDetails["Sst"] = null
    m_CrosswireBookDetails["Dnt"] = null
    m_CrosswireBookDetails["Blt"] = null
    m_CrosswireBookDetails["Eza"] = null
    m_CrosswireBookDetails["5Ez"] = null
    m_CrosswireBookDetails["6Ez"] = null
  }
}