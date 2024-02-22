package org.stepbible.textconverter.osisinputonly

import org.stepbible.textconverter.utils.BibleStructure
import org.stepbible.textconverter.support.bibledetails.*
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.utils.InternalOsisDataCollection
import org.stepbible.textconverter.utils.ReversificationData
import java.io.File
import java.io.PrintWriter




/******************************************************************************/
/**
 * Handles the interface to osis2mod.
 *
 * Until the present, we have always used the Crosswire variant of osis2mod,
 * and therefore this present class has not been necessary.
 *
 * It has become apparent, though, that the Crosswire variant gets in the way of
 * some of the things we want to do.  In particular, it doesn't sit too well
 * with reversification, and also it forces verses to be in 'correct' order
 * despite the fact that in a few texts the translators may deliberately have
 * put them in the wrong order.  To address this, at the time of writing we have
 * our own variant of osis2mod.  Whether this will turn out to be a permanent
 * feature is not certain at present; but so long as we opt to use it, it does
 * have implications for the processing at large, which needs to know which
 * variant we are using, and there are certain things we need to do to support
 * notably creating a JSON file containing information about the structure of
 * the text.
 *
 * Note that this doesn't _drive_ the selection: it merely responds to settings
 * established in TestController, qv.
 *
 * Once we establish whether in future we're going to go with our version of
 * osis2mod or Crosswire's, a lot of this can go (and we won't want to be
 * driven by TestController).  Indeed if we revert to using Crosswire's all of
 * it can go, provided we reinstate any code dependent upon checking which
 * version we are using.  If we use our own, then a fair bit of code here will
 * need to remain, because our version requires a JSON file which is generated
 * here.
 *
 * @author ARA "Jamie" Jamieson
 */

abstract class Osis_Osis2modInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  companion object
  {
    fun instance (): Osis_Osis2modInterface
    {
      if (null == m_Instance)
        m_Instance = if ("step" == ConfigData["stepOsis2modType"]!!) Osis2ModInterfaceStep else Osis2ModInterfaceCrosswire
      return m_Instance!!
    }

    private var m_Instance: Osis_Osis2modInterface? = null
  }


  /****************************************************************************/
  /**
  * Outputs all relevant information to the JSON files where we are using
  * the STEP variant of osis2mod.
  *
  * @param filePath Place where data is stored.
  */

  abstract fun createSupportingDataIfRequired (filePath: String)


  /****************************************************************************/
  /**
  * Records the osis2mod variant we are going to use, and sets up any other
  * aspects of the environment dependent upon that.
  */

  abstract fun initialise ()


  /****************************************************************************/
  /**
  * Returns an indication of whether the version of osis2mod being used
  * supports subverses.
  *
  * @return True if osis2mod supports subverses.
  */

  abstract fun supportsSubverses (): Boolean
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                                Crosswire                                 **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
object Osis2ModInterfaceCrosswire: Osis_Osis2modInterface()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Outputs any support information required by osis2mod.
  */

  override fun createSupportingDataIfRequired (filePath: String)
  {
  }


  /****************************************************************************/
  /**
  * Records the osis2mod variant we are going to use, and sets up any other
  * aspects of the environment dependent upon that.
  */

  override fun initialise ()
  {
  }


  /****************************************************************************/
  /**
  * Returns an indication of whether the version of osis2mod being used
  * supports subverses.
  *
  * @return True if osis2mod supports subverses.
  */

  override fun supportsSubverses () = false
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                                   Step                                   **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
object Osis2ModInterfaceStep: Osis_Osis2modInterface()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Outputs any support information required by osis2mod.
  */

  override fun createSupportingDataIfRequired (filePath: String)
  {
    ConfigData["stepVersificationScheme"] = "v11n_" + ConfigData["stepModuleName"]!!
    populateBibleStructure(InternalOsisDataCollection.getBibleStructure()) // We _must_ be dealing with OsisTemp by now.
    m_BibleStructure.jswordMappings = ReversificationData.getReversificationMappings()
    outputJson(filePath)
  }


  /****************************************************************************/
  /**
  * Records the osis2mod variant we are going to use, and sets up any other
  * aspects of the environment dependent upon that.
  */

  override fun initialise ()
  {
    initialiseBookDetails()
  }


  /****************************************************************************/
  /**
  * Returns an indication of whether the version of osis2mod being used
  * supports subverses.
  *
  * @return True if osis2mod supports subverses.
  */

  override fun supportsSubverses (): Boolean
  {
    return true
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

     var jswordMappings: List<Pair<RefKey, RefKey>> = listOf()


     fun output (writer: PrintWriter)
     {
       print(writer, "{\n")
       print(writer, "  'v11nName': '$v11nName',\n")

       outputBookDetails(writer,"otbooks", otBooks)
       outputBookDetails(writer,"ntbooks", ntBooks)

       print(writer, "  'vm': [")
       outputMaxVerses(writer, otBooks, true)
       outputMaxVerses(writer, ntBooks, false)
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
      val mappings = m_BibleStructure.jswordMappings.map { "${Ref.rd(it.first).toStringOsis()}=${Ref.rd(it.second).toStringOsis()}" }
      print(writer, "    \"" + mappings.joinToString("\",\n    \""))
      print(writer, "\"\n    ]\n")
    }


    fun outputMaxVerses (writer: PrintWriter, content: List<BookDetails>, moreToCome: Boolean)
    {
      val s = content.subList(0, content.size - 1).filter { it.chapMax > 0} .joinToString(",\n\n") { it.maxVersesToJson() }
      print(writer, s)
      if (moreToCome && s.isNotEmpty()) print(writer, ",\n")
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
      return """    // $name ($chapMax chapters).
    ${vm.joinToString(", ")}"""
    }
  }


  /****************************************************************************/
  /* If creating the JSON file needed by our own version of ossi2mod, we need
     Crosswire book abbreviations, which unfortunately differ from USX and
     OSIS.  (Crosswire also differs from other schemes in terms of the DC books
     it supports -- there's a lot of overlap, but it's not an exact match.) */

  private fun initialiseBookDetails ()
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


  /****************************************************************************/
  /* Note that OT and DC books all get grouped into otBooks for output purposes.
     I don't necessarily understand why, but it _is_ what's required. */

  private fun populateBibleStructure (bibleStructureUnderConstruction: BibleStructure)
  {
    m_BibleStructure.v11nName = ConfigData["stepVersificationScheme"]!!

    populateBibleStructure(bibleStructureUnderConstruction, m_BibleStructure.otBooks, BibleAnatomy.getBookNumberForStartOfOt(), BibleAnatomy.getBookNumberForEndOfOt())
    populateBibleStructure(bibleStructureUnderConstruction, m_BibleStructure.otBooks, BibleAnatomy.getBookNumberForStartOfDc(), BibleAnatomy.getBookNumberForEndOfDc(), true) // otbooks _is_ intended here -- see head of method comments.
    populateBibleStructure(bibleStructureUnderConstruction, m_BibleStructure.ntBooks, BibleAnatomy.getBookNumberForStartOfNt(), BibleAnatomy.getBookNumberForEndOfNt())

    m_BibleStructure.otBooks.add(BookDetails()) // Terminate with empty entry.
    m_BibleStructure.ntBooks.add(BookDetails()) // Terminate with empty entry.
  }


  /****************************************************************************/
  private fun populateBibleStructure (bibleStructureUnderConstruction: BibleStructure, headers: MutableList<BookDetails>, bookLow: Int, bookHigh: Int, skipMissingBooks: Boolean = false)
  {
    for (bookNo in bookLow .. bookHigh)
    {
      val missingBook = !bibleStructureUnderConstruction.bookExists(bookNo)
      if (skipMissingBooks && missingBook) continue

      val header = BookDetails()
      headers.add(header)

      val ubsAbbreviation = BibleBookNamesUsx.numberToAbbreviatedName(bookNo)
      header.name = m_CrosswireBookDetails[ubsAbbreviation]!!.fullName
      header.osis = BibleBookNamesOsis.numberToAbbreviatedName(bookNo)
      header.prefAbbrev = m_CrosswireBookDetails[ubsAbbreviation]!!.abbreviation
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
  private val m_BibleStructure = MyBibleStructure()
  private val m_CrosswireBookDetails: MutableMap<String, CrosswireBookDetails?>  = mutableMapOf()
}
