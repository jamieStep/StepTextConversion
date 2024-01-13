package org.stepbible.textconverter

import com.google.gson.GsonBuilder
import org.stepbible.textconverter.support.bibledetails.TextStructureUsxForUseWhenAnalysingInput
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.w3c.dom.Document
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.getExtendedNodeName
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefCollection
import org.stepbible.textconverter.support.ref.RefRange
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Node
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet




/******************************************************************************/
/**
 * Holds summary information about the raw text.
 *
 * This class is somewhat speculative in nature -- it is used to store
 * information about the raw text (which tags it uses, etc) and also about the
 * structure of the Bible in its raw form (which chapters and verses it has,
 * and so on).
 *
 * I do relatively little with this information at present -- it is stored in
 * the TextFeatures folder in extended JSON format in case anyone is interested
 * in it, and I also copy parts of the raw text information to the Sword
 * configuration file as comments.
 *
 * *IMPORTANT:* Note that information relating to the Bible structure and
 * text features relates to the _input_ USX text (or, where running from VL,
 * to the USX generated immediately from this).  It does not take into account
 * any subsequent processing.
 *
 * The data is stored in two separate files.  One contains details of the raw
 * text and the Bible structure it represents; and one contains details of
 * this particular run.  The former is meaningful only on a text where we have
 * VL or USX available as an input.  And the latter may be only partially
 * meaningful on runs which start from OSIS.
 *
 * @author ARA "Jamie" Jamieson
 */

object UsxA_GenerateTextFeatures
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
    StepFileUtils.createFolderStructure(FileLocations.getMasterMiscellaneousFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getMasterTextFeaturesRootFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getMasterTextFeaturesFolderPath())

    if ("tbd" == (ConfigData["stepVersificationScheme"] ?: "tbd"))
      ConfigData["stepVersificationScheme"] = "v11n_" + ConfigData["stepModuleName"]

    if (haveBibleTextAvailable())
      outputBibleStructureToJson()
    else
      outputBibleStructureDummy()

    outputRunDetailsToJson()
  }


  /****************************************************************************/
  /**
  * Adds to the text features details of a reversification issues.
  *
  * @param details Details of issue.
  */

  fun addReversificationIssue (details: String)
  {
    if (null == m_RunFeatures.ReversificationDataIssues) m_RunFeatures.ReversificationDataIssues = ArrayList()
    m_RunFeatures.ReversificationDataIssues!!.add(details)
  }


  /****************************************************************************/
  /**
  * Records the key and value of a piece of translatable text which has
  * actually been used during processing.  The idea is that this can make it
  * easier to identify things for which we might want translations.  To this
  * end I record only texts which are in English.
  *
  * @param key Key for text: V_emptyContentFootnote_verseEmptyInThisTranslation
  *   or whatever.
  *
  * @param text Corresponding text.  Unfortunately I have no easy way of
  *   overtly identifying whether the text is in English or not, so there is
  *   no automated way of determining whether we need a translation.
  */

  fun addTranslatableText (key: String, text: String)
  {
    if (ConfigData.isEnglishTranslatableText(key) && "eng" != ConfigData["stepLanguageCode3Char"])
      m_RunFeatures.FootnoteTextWhichWasOutputInEnglishWhenPerhapsATranslationWouldBeBetter[key] = text
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Bible structure                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private class BibleTextStructure
  {
     var ModuleName: String? = null

     var HasOt = false
     var HasNt = false
     var HasDc = false
     var HasFullOt = false
     var HasFullNt = false
     var BooksOt: List<String>? = null
     var BooksNt: List<String>? = null
     var BooksDc: List<String>? = null

    var HasCrossReferences = false
    var HasFootnotes = false



    /**************************************************************************/
    var HasElisions = false
    val AllElisionLocations: MutableList<String> = ArrayList() // All elisions.



    /**************************************************************************/
    var HasSubverses = false
    val SubverseLocations: MutableList<String> = ArrayList()



    /**************************************************************************/
    var HasElidedSubverses = false
    val SubverseElisionLocations: MutableList<String> = ArrayList() // Elisions which start and end with a subverse.



    /**************************************************************************/
    var HasMismatchedVerseSubverseElisions = false
    val MismatchedVerseSubverseElisionLocations: MutableList<String> = ArrayList() // Elisions which start with a verse and end with a subverse or vice versa.



    /**************************************************************************/
    var HasSpeakers = false
    val SpeakerLocations: MutableList<String> = ArrayList()



    /**************************************************************************/
    var HasTables = false
    val TableLocations: MutableList<String> = ArrayList()



    /**************************************************************************/
    var TagNames: List<String>? = null


    /**************************************************************************/
    var HasOutOfOrderVerses = false
    var OutOfOrderVerses: List<String>? = null



    /**************************************************************************/
    var ABOUT_THE_FOLLOWING_VERSIFICATION_DATA= ""
    
    var DCPresence = ""
    
    var HasBooksInTextWhichOsis2modDoesNotSupport = false
    var BooksInTextWhichOsis2modDoesNotSupport: List<String>? = null

    var HasBooksInOsis2modWhichTextDoesNotProvide = false
    var BooksInOsis2modWhichTextDoesNotProvide: List<String>? = null

    var HasVersesInTextWhichOsis2modDoesNotSupport = false
    var VersesInTextWhichOsis2modDoesNotSupport: List<String>? = null

    var HasVersesInOsis2modWhichTextDoesNotProvide = false
    var VersesInOsis2modWhichTextDoesNotProvide: List<String>? = null
  }


  /****************************************************************************/
  /* Used where we can't actually generate a Bible structure file. */

  private fun outputBibleStructureDummy ()
  {
    File(FileLocations.getMasterBibleTextFeaturesFilePath())
    .writeText("""
      //******************************************************************************
      //
      // Bible structure information is not presently available for texts where USX
      // and VL are not available.
      //
      //******************************************************************************
    """.trimIndent())
  }


  /****************************************************************************/
  private fun outputBibleStructureToJson ()
  {
    populateBibleStructure()

    val header = """
                 //******************************************************************************
                 //
                 // ${ConfigData["stepModuleName"]!!}
                 //
                 //
                 // Content is as follows:
                 //
                 // * ModuleName: What it says on the tin.
                 //
                 // * HasCrossReferences is true if the text contains ref tags or char:xt tags.
                 //   I do not (presently) record here any details of cross-references which
                 //   don't work for any reason.
                 //
                 // * HasFootnotes is true if the text contains note:f, note:ef or note:fe.
                 //
                 // * HasElisions / AllElisionLocations: All ElisionLocations lists the locations
                 //   of all elisions.  HasElisions is true if AllElisionLocations is non-empty.
                 //
                 // * HasSubverses / SubverseLocations: SubverseLocations indicates all of the
                 //   verses which contain subverses.  HasSubverses is true if the list is non-
                 //   empty.
                 //
                 // * HasElidedSubverses / SubverseElisionLocations: SubverseElisionLocations
                 //   gives the locations of all places where the start and end of an elision are
                 //   both subverses, and HasElidedSubverses is true if the list is non-empty.
                 //   Note that at present, I don't check that the start and end locations are in
                 //   the same verse (or not for purposes of reporting here: the code at large may
                 //   well do so).  SubverseElisionLocations will be a possibly empty subset of
                 //   AllElisionLocations.
                 //
                 // * HasMismatchedVerseSubverseElisions/MismatchedVerseSubverseElisionLocations:
                 //   MismatchedVerseSubverseElisionLocations lists all places where an elision
                 //   starts with a verse and ends with a subverse or vice versa, and
                 //   HasMismatchedVerseSubverseElisions is true if this collection is non-empty.
                 //   MismatchedVerseSubverseElisionLocations will be a possibly empty subset of
                 //   AllElisionLocations.
                 //
                 // * HasSpeakers / SpeakerLocations: SpeakerLocations lists the locations of all
                 //   speaker markup.  HasSpeakers will be true if SpeakerLocations is non-empty.
                 //
                 // * HasTables / TableLocations: TableLocations lists the locations of all tables.
                 //   HasTables will be true if TableLocations is non-empty.
                 //
                 // * TagNames: a full list of tag names.
                 //
                 // * OutOfOrderVerses: If any verses in the text are out of order, this gives a
                 //   list of the _first_ verse in each chapter which is out of order in relation
                 //   to the verse which appears in the text physically immediately before it.
                 //
                 //******************************************************************************
                 
                 """.trimIndent()

    outputJson(FileLocations.getMasterBibleTextFeaturesFilePath(), header, m_BibleTextStructure)
  }


  /****************************************************************************/
  private fun populateBibleStructure ()
  {
    m_BibleTextStructure.ModuleName = ConfigData["stepModuleName"]!!
    m_BibleTextStructure.BooksOt = TextStructureUsxForUseWhenAnalysingInput.getBooksOt()
    m_BibleTextStructure.BooksNt = TextStructureUsxForUseWhenAnalysingInput.getBooksNt()
    m_BibleTextStructure.BooksDc = TextStructureUsxForUseWhenAnalysingInput.getBooksDc()
    m_BibleTextStructure.HasOt = TextStructureUsxForUseWhenAnalysingInput.hasOt()
    m_BibleTextStructure.HasNt = TextStructureUsxForUseWhenAnalysingInput.hasNt()
    m_BibleTextStructure.HasDc = TextStructureUsxForUseWhenAnalysingInput.hasDc()
    m_BibleTextStructure.HasFullOt = TextStructureUsxForUseWhenAnalysingInput.hasFullOt()
    m_BibleTextStructure.HasFullNt = TextStructureUsxForUseWhenAnalysingInput.hasFullNt()

    TextStructureUsxForUseWhenAnalysingInput.iterateOverAllFiles(::populateBibleFeatures)
    m_BibleTextStructure.TagNames = m_NodeNames.sorted()
 }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Text features                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* For reporting general information. */

  private class RunFeatures
  {
    /**************************************************************************/
    var ModuleName = ""
    var RunStartedFrom = ""
    var VersificationScheme = ""



    /**************************************************************************/
    var ReversificationType: String = ""
    var HasReversificationDataIssues = false
    var ReversificationDataIssues: MutableList<String>? = null
    var ReversificationMappings: List<String>? = null
    var AcceptedReversificationRows: List<String>? = null


    /**************************************************************************/
    val FootnoteTextWhichWasOutputInEnglishWhenPerhapsATranslationWouldBeBetter = TreeMap<String, String>()
  }


  /****************************************************************************/
  private fun outputRunDetailsToJson ()
  {
    populateRunFeatures()

    val header = """
                 //******************************************************************************
                 //
                 // ${ConfigData["stepModuleName"]!!}
                 //
                 // The information in this file is intended mainly for debug purposes, and to
                 // make it possible to locate texts with particular characteristics.
                 //
                 // The information in this file is related purely to the nature of the run
                 // which gave rise to this module.  There is a separate JSON file, co-located
                 // with this file, which describes the characteristics of the input text file
                 // where this was either VL or USX.
                 //
                 // Where starting a run from OSIS, reversification-related information and
                 // translatable text information in this file is not meaningful.  
                 //
                 // Content is as follows:
                 //
                 // * ModuleName: What it says on the tin.
                 //
                 // * RunStartedFrom: The type of input from which the run started (VL, USX, OSIS).
                 //
                 // * ReversificationType: None, RunTime or ConversionTime.
                 //
                 // * VersificationScheme: What it says on the tin.
                 //
                 // * HasReversificationDataIssues / ReversificationDataIssues:
                 //   ReversificationDataIssues lists any issues found while processing the
                 //   reversification.  (In particular, it lists reversification rows whose
                 //   applicability could not be determined because they used rules which
                 //   attempted to apply length tests to elided verses.) HasReversificationDataIssues
                 //   is true if any data issues have been recorded.
                 //
                 // * ReversificationMappings: Where reversification has been applied, this lists
                 //   old-to-new reference mappings where old and new are different.
                 //
                 // * AcceptedReversificationRows: Lists all of the applicable reversification
                 //   rows.
                 //
                 // * TranslatableText: A collection of key/value pairs listing all of the
                 //   translatable footnote texts used by this Bible.  It is intended to give
                 //   a handle on the list of texts which might require translation.
                 //   Unfortunately I have no easy way of indicating whether a particular
                 //   item is in English or not, so this list will have to be scanned manually.
                 //
                 //******************************************************************************
                 
                 """.trimIndent()

    outputJson(FileLocations.getMasterRunFeaturesFilePath(), header, m_RunFeatures)
  }


  /****************************************************************************/
  private fun populateRunFeatures ()
  {
    val isOsisRun = "OSIS" == ConfigData["stepProcessingOriginalData"]
    m_RunFeatures.ModuleName = ConfigData["stepModuleName"]!!
    m_RunFeatures.RunStartedFrom = ConfigData["stepProcessingOriginalData"]!!
    m_RunFeatures.VersificationScheme = ConfigData["stepVersificationScheme"]!!
    m_RunFeatures.ReversificationType = if (isOsisRun) "ReversificationType and related parameters are not meaningful when starting from OSIS" else ConfigData["stepReversificationType"]!!



    /**************************************************************************/
    val referenceMappings = ReversificationData.getReferenceMappings()
    if (referenceMappings.isNotEmpty())
      m_RunFeatures.ReversificationMappings = referenceMappings.map { Ref.rd(it.key).toString() + " -> " + Ref.rd(it.value).toString() }



    /**************************************************************************/
    val acceptedRows = ReversificationData.getAllAcceptedRows()
    if (acceptedRows.isNotEmpty())
      m_RunFeatures.AcceptedReversificationRows = acceptedRows.map { it.toString() }

    m_RunFeatures.HasReversificationDataIssues = null != m_RunFeatures.ReversificationDataIssues
  }


  /****************************************************************************/
  private fun populateBibleFeatures (bookName: String, filePath: String, document: Document)
  {
    /**************************************************************************/
    Dom.collectNodesInTree(document).forEach { populateTextFeaturesFromNode(it) }



    /**************************************************************************/
    m_BibleTextStructure.OutOfOrderVerses = TextStructureUsxForUseWhenAnalysingInput.getBibleStructure().getOutOfOrderVerses().map { Ref.rd(it).toString() }
    m_BibleTextStructure.HasOutOfOrderVerses = m_BibleTextStructure.OutOfOrderVerses!!.isNotEmpty()
 
 
    
    /**************************************************************************/
    if ("conversiontime" == ConfigData["stepReversificationType"]!!)
    {
     m_BibleTextStructure.ABOUT_THE_FOLLOWING_VERSIFICATION_DATA= "Conversion-time reversification is being applied.  As a result, the text will be forced to be NRSVA compliant, and therefore the following data is not meaningful."
     return
    }



    /**************************************************************************/
    if ("runtime" == ConfigData["stepReversificationType"]!!)
    {
     m_BibleTextStructure.ABOUT_THE_FOLLOWING_VERSIFICATION_DATA= "We are using a bespoke versification scheme.  The text will necessarily comply with that, and therefore the following data is not meaningful."
     return
    }



    /**************************************************************************/
    m_BibleTextStructure.ABOUT_THE_FOLLOWING_VERSIFICATION_DATA= "Because reversification is not being applied, the text needs to conform to the selected osis2mod versification scheme.  The following details highlight any issues."

    val analysis = VersificationSchemesEvaluator_InputUsxOrUsxA.evaluateSingleSchemeDetailed(ConfigData["stepReversificationType"]!!)

    m_BibleTextStructure.DCPresence = analysis.DCPresence

    m_BibleTextStructure.BooksInTextWhichOsis2modDoesNotSupport = analysis.booksMissingInOsis2modScheme.map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
    m_BibleTextStructure.HasBooksInTextWhichOsis2modDoesNotSupport = m_BibleTextStructure.BooksInTextWhichOsis2modDoesNotSupport!!.isNotEmpty()

    m_BibleTextStructure.BooksInOsis2modWhichTextDoesNotProvide = analysis.booksInExcessInOsis2modScheme.map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
    m_BibleTextStructure.HasBooksInOsis2modWhichTextDoesNotProvide = m_BibleTextStructure.BooksInTextWhichOsis2modDoesNotSupport!!.isNotEmpty()

    m_BibleTextStructure.VersesInTextWhichOsis2modDoesNotSupport = analysis.versesMissingInOsis2modScheme.map { Ref.rd(it).toString() }
    m_BibleTextStructure.HasVersesInTextWhichOsis2modDoesNotSupport = m_BibleTextStructure.BooksInTextWhichOsis2modDoesNotSupport!!.isNotEmpty()

    m_BibleTextStructure.VersesInOsis2modWhichTextDoesNotProvide = analysis.versesInExcessInOsis2modScheme.map { Ref.rd(it).toString() }
    m_BibleTextStructure.HasVersesInOsis2modWhichTextDoesNotProvide = m_BibleTextStructure.VersesInTextWhichOsis2modDoesNotSupport!!.isNotEmpty()
  }


  /****************************************************************************/
  private fun populateTextFeaturesFromNode (node: Node)
  {
    /**************************************************************************/
    /* Keep track of overall structure, allowing for USX 2 / USX 3
       differences. */

    when (Dom.getNodeName(node))
    {
      "book" -> m_CurrentBook = Dom.getAttribute(node, "code")!!

      "chapter" ->
      {
        Dom.deleteAttribute(node, "style")

        if (!Dom.hasAttribute(node, "eid"))
        {
          m_CurrentChapterSid = if (Dom.hasAttribute(node, "sid"))
            Dom.getAttribute(node,"sid")!!
          else
            m_CurrentBook + " " + Dom.getAttribute(node, "number")
        }
      }

      "verse" ->
      {
        Dom.deleteAttribute(node, "style")

        if (!Dom.hasAttribute(node, "eid"))
        {
          m_CurrentVerseSid = if (Dom.hasAttribute(node, "sid"))
            Dom.getAttribute(node,"sid")!!
          else
            m_CurrentChapterSid + ":" + Dom.getAttribute(node, "number")
        }
      }
    }


    /**************************************************************************/
    val nodeName = getExtendedNodeName(node)
    m_NodeNames.add(nodeName)
    when (nodeName)
    {
      "note:f", "note:ef", "note:fe" -> m_BibleTextStructure.HasFootnotes = true

      "para:sp" ->
      {
        m_BibleTextStructure.SpeakerLocations.add(m_CurrentVerseSid)
        m_BibleTextStructure.HasSpeakers = true
      }

      "ref", "char:xt" -> m_BibleTextStructure.HasCrossReferences = true

      "table" ->
      {
        m_BibleTextStructure.TableLocations.add(m_CurrentVerseSid)
        m_BibleTextStructure.HasTables = true
      }

      "verse" ->
      {
        val rc = RefCollection.rd(m_CurrentVerseSid)
        if (rc.getLowAsRef().hasS() || rc.getHighAsRef().hasS())
        {
          m_BibleTextStructure.SubverseLocations.add(m_CurrentVerseSid)
          m_BibleTextStructure.HasSubverses = true
        }

        if (m_CurrentVerseSid.contains("-"))
        {
          if (m_CurrentVerseSid !in m_BibleTextStructure.AllElisionLocations)
            m_BibleTextStructure.AllElisionLocations.add(m_CurrentVerseSid)
          m_BibleTextStructure.HasElisions = true

          val range = RefRange.rdUsx(m_CurrentVerseSid)
          if (range.getLowAsRef().hasS() || range.getHighAsRef().hasS())
          {
            m_BibleTextStructure.SubverseElisionLocations.add(m_CurrentVerseSid)
            m_BibleTextStructure.HasElidedSubverses = true
            if (!range.getLowAsRef().hasS() || range.getHighAsRef().hasS())
            {
              m_BibleTextStructure.MismatchedVerseSubverseElisionLocations.add(m_CurrentVerseSid)
              m_BibleTextStructure.HasMismatchedVerseSubverseElisions = true
            }
          }
        }
      }
    }
  }

  private var m_CurrentBook = ""
  private var m_CurrentChapterSid = ""
  private var m_CurrentVerseSid = ""
  private val m_NodeNames: MutableSet<String> = HashSet()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Common utilities                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* In essence, we are concerned here purely with checking that we have
     input USX available.  It isn't an error if we don't -- it may be that for
     this text we have only USX.  But we need to know, because it limits what
     information we can supply.

     There is one additional piece of functionality, though -- if we have VL
     but no USX derived from it, then we arrange to create the USX.

     There is no need to record details of which folder should be used for
     input: BibleBookAndFileMapperInputUsx will take care of that. */

  private fun haveBibleTextAvailable (): Boolean
  {
    if (StepFileUtils.fileOrFolderExists(FileLocations.getInputUsxFolderPath()) && !StepFileUtils.folderIsEmpty(FileLocations.getInputUsxFolderPath()))
      return true

    if (StepFileUtils.fileOrFolderExists(FileLocations.getInputVlFolderPath()) && !StepFileUtils.folderIsEmpty(FileLocations.getInputVlFolderPath()))
    {
      if (StepFileUtils.getLatestFileDate(FileLocations.getInternalUsxAFolderPath(), "usx") > StepFileUtils.getLatestFileDate(FileLocations.getInputVlFolderPath(), "usx"))
        return true

      FileCreator_InputVl_To_UsxA.pre()
      FileCreator_InputVl_To_UsxA.process()
      return true
    }

    return false
  }


  /****************************************************************************/
  private fun outputJson (filePath:String, header:String?, content:Any)
  {
    try
    {
      val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
      PrintWriter(filePath).use { out ->
                       val  s = gson.toJson(content)
                       if (null != header)
                       {
                         out.print(header)
                         out.print("\n")
                       }
                       out.print(s)
                       out.print("\n")
      }
    }
    catch (e:Exception)
    {
        throw StepException(e)
    }
  }


  /****************************************************************************/
  private val m_BibleTextStructure = BibleTextStructure()
  private val m_RunFeatures = RunFeatures()
}