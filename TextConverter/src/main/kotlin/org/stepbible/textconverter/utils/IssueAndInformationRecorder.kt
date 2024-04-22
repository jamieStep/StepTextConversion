package org.stepbible.textconverter.utils

import com.google.gson.GsonBuilder
import org.stepbible.textconverter.processingelements.PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.bibledetails.VersificationSchemesSupportedByOsis2mod
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.ref.RefCollection
import org.stepbible.textconverter.support.stepexception.StepException
import java.io.PrintWriter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.contains
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey
import org.w3c.dom.Node


/****************************************************************************/
/**
 * Handles all kinds of information which we might want to log and / or
 * report immediately and / or accumulate for use in summaries etc.
 *
 * @author ARA "Jamie" Jamieson
 */

object IssueAndInformationRecorder
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Records the key and value of a piece of translatable text which has
  * actually been used during processing.  The idea is that this can make it
  * easier to identify things for which we might want translations.  To this
  * end I record only texts which are in English.
  *
  * This text goes to TextFeatures only.
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
  /* Definite issues which may usefully be reported and / or stored within the
     TextFeatures information, but which don't turn up in config files etc. */

  fun crossVerseBoundaryMarkup (badRef: String, location: RefKey, forceError: Boolean = false, reassurance: String? = null)
  {
    val locationAsString = Ref.rd(location).toString()
    report("Cross-verse-boundary markup at ${locationAsString}.", forceError, "LogWarning,Report", location, null, reassurance)
    m_BibleTextStructure.CrossVerseBoundaryMarkupLocations.add(locationAsString)
  }

  fun crossReferenceInternalAndVernacularTargetDoNotMatch (badRef: String, location: RefKey, forceError: Boolean = false, reassurance: String? = null) =
    report("Cross-reference: Internal and vernacular references do not match '$badRef' at ${Ref.rd(location)}.", forceError, "LogWarning,Report", location, m_CrossReferencesInvalidInternalReference, reassurance)

  fun crossReferenceInvalidReference (badRef: String, location: RefKey, forceError: Boolean = false, reassurance: String? = null) =
    report("Cross-reference: Invalid reference '$badRef' at ${Ref.rd(location)}.", forceError, "LogWarning,Report", location, m_CrossReferencesInvalidInternalReference, reassurance)

  fun crossReferenceInvalidVernacularText (badRef: String, location: RefKey, forceError: Boolean = false, reassurance: String? = null) =
    report("Cross-reference: Invalid vernacular text '$badRef' at ${Ref.rd(location)}.", forceError, "LogWarning,Report", location, m_CrossReferencesInvalidVernacularReference, reassurance)

  fun crossReferenceNonExistentTarget (badRef: String, location: RefKey, forceError: Boolean = false, reassurance: String? = null) =
    report("Cross-reference: Invalid reference '$badRef' at ${Ref.rd(location)}.", forceError, "LogWarning,Report", location, m_CrossReferencesNonExistentTargets, reassurance)

  fun reversificationIssue (text: String) =
    report(text, false, "LogWarning", 0, m_ReversificationIssues, "Issue has been ignored.")


  /****************************************************************************/
  /* Information which may be reported and which also turns up in the
     features information, but is not of interest for the config file. */

  fun elidedVerse (locations: List<RefKey>, text: String)
  {
    report("Elided verse: $text", false, "LogInfo", locations.first(), m_ElidedVerses, null)
    m_BibleTextStructure.HasElisions = true
    m_BibleTextStructure.AllElisionLocations.addAll(locations.map { Ref.rd(it).toString() })
    if (locations.any { Ref.hasS(it) })
    {
      m_BibleTextStructure.HasElidedSubverses = true
      m_BibleTextStructure.SubverseElisionLocations.addAll(locations.filter { Ref.hasS(it) }.map { Ref.rd(it).toString() } )

      if (!locations.all { Ref.hasS(it) })
      {
        m_BibleTextStructure.HasMismatchedVerseSubverseElisions = true
        m_BibleTextStructure.MismatchedVerseSubverseElisionLocations.add(Ref.rd(locations.first()).toString())
      }
    }
  }



  /****************************************************************************/
  fun setDivergencesFromSelectedVersificationScheme (text: String) { m_RunFeatures.DivergencesFromSelectedVersificationScheme = text }
  fun setChangedFootnoteCalloutsToHouseStyle ()                    { m_RunFeatures.ChangedFootnoteCalloutsToHouseStyle = true }
  fun setConversionTimeReversification ()                          { m_RunFeatures.ReversificationType = "Conversion time" }
  fun setRuntimeReversification ()                                 { m_RunFeatures.ReversificationType = "Run time" }
  fun setConvertedCrossReferencesToCanonicalForm ()                { m_RunFeatures.ConvertedCrossReferencesToCanonicalForm = true }
  fun setForcedSelfClosingParas ()                                 { m_RunFeatures.ForcedSelfClosingParas = true }
  fun setReformattedTrailingCanonicalTitles ()                     { m_RunFeatures.ReformattedTrailingCanonicalTitles = true }
  fun setSplitCrossVerseBoundarySpanTypeTags ()                    { m_RunFeatures.SplitCrossVerseBoundarySpanTypeTags = true }
  fun setStrongs ()                                                { m_RunFeatures.ModifiedStrongsReferences = true }

  fun setHasMultiVerseParagraphs () { TODO() } // Needs to turn up in Sword config.




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun report (text: String, forceError: Boolean, actions: String, location: RefKey, store: MutableList<String>?, reassurance: String?)
  {
    val myActions = if (forceError) "LogError" else actions
    var myText = text
    if ("LogError" !in myActions) myText += (if (null == reassurance) "" else "  $reassurance")

    for (action in myActions.split(","))
      when (action.trim())
      {
         "LogInfo"    -> Logger.info   (location, text)
         "LogWarning" -> Logger.warning(location, text)
         "LogError"   -> Logger.error  (location, text)
         "Report"     -> Dbg.reportProgress(Ref.rd(location).toString() + ": " + text)
      }

      store?.add(Ref.rd(location).toString() +":" + text)
  }


  /****************************************************************************/
  private val m_CrossReferencesInvalidInternalReference = mutableListOf<String>()
  private val m_CrossReferencesInvalidVernacularReference = mutableListOf<String>()
  private val m_CrossReferencesNonExistentTargets = mutableListOf<String>()

  private val m_ElidedVerses = mutableListOf<String>()

  private val m_ReversificationIssues = mutableListOf<String>()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                        Text and run summary                            **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Intended for use only within this section, and not required in advance of
     this. */

  private lateinit var m_DataCollection: X_DataCollection
  private lateinit var m_FileProtocol: X_FileProtocol


  /****************************************************************************/
  /**
  * Outputs features details.
  *
  * @param outputFilePath File to which to write the details.
  * @param dataCollection
  */

  fun processFeaturesSummaryBibleDetails (outputFilePath: String, dataCollection: X_DataCollection)
  {
    m_DataCollection = dataCollection
    m_FileProtocol = m_DataCollection.getFileProtocol()

    if ("tbd" == (ConfigData["stepVersificationScheme"] ?: "tbd"))
      ConfigData["stepVersificationScheme"] = /* "x11n_" + */ ConfigData["stepModuleName"]!!

    outputBibleStructureToJson(outputFilePath)
  }


  /****************************************************************************/
  fun processFeaturesSummaryRunDetails (filePath: String)
  {
    if ("tbd" == (ConfigData["stepVersificationScheme"] ?: "tbd"))
      ConfigData["stepVersificationScheme"] = /* "x11n_" + */ ConfigData["stepModuleName"]!!

    outputRunDetailsToJson(filePath)
  }


  /****************************************************************************/
  private fun populateTextFeaturesFromNode (node: Node)
  {
    /**************************************************************************/
    val nodeName = Dom.getNodeName(node)
    m_NodeNames.add(nodeName)

    if (m_FileProtocol.tagName_verse() == nodeName)
      populateTextFeaturesFromVerse(node)

     if (m_FileProtocol.isExplanatoryFootnoteNode(node))
      m_BibleTextStructure.HasFootnotes = true

    else if (m_FileProtocol.isCrossReferenceFootnoteNode(node))
      m_BibleTextStructure.HasCrossReferences = true

    else if (m_FileProtocol.tagName_table() == nodeName)
    {
        m_BibleTextStructure.TableLocations.add(m_CurrentVerseSid)
        m_BibleTextStructure.HasTables = true
    }

    else if (m_FileProtocol.isSpeakerNode(node))
    {
      m_BibleTextStructure.SpeakerLocations.add(m_CurrentVerseSid)
      m_BibleTextStructure.HasSpeakers = true
    }

    else if (m_FileProtocol.isAcrosticSpanNode(node))
    {
      m_BibleTextStructure.SpeakerLocations.add(m_CurrentVerseSid)
      m_BibleTextStructure.HasSpeakers = true
    }

    else if (m_FileProtocol.isAcrosticDivNode(node))
    {
      m_BibleTextStructure.SpeakerLocations.add(m_CurrentVerseSid)
      m_BibleTextStructure.HasSpeakers = true
    }
  }// populateTextFeaturesFromNode


  /****************************************************************************/
  /* Records things like whether this is a subverse, whether we have elisions,
     etc. */

  private fun populateTextFeaturesFromVerse (verse: Node)
  {
    /**************************************************************************/
    /* We pick up information from sids only. */

    if (m_FileProtocol.attrName_verseSid() !in verse) return



    /**************************************************************************/
    /* Check if we have a subverse. */

    val m_CurrentVerseSid = m_FileProtocol.readRefCollection(verse[m_FileProtocol.attrName_verseSid()]!!).toString() // Save as USX.
    val rc = RefCollection.rd(m_CurrentVerseSid)
    if (rc.getAllAsRefKeys().any { Ref.hasS(it) } )
    {
      m_BibleTextStructure.SubverseLocations.add(m_CurrentVerseSid)
      m_BibleTextStructure.HasSubverses = true
    }
  }


  private var m_CurrentVerseSid = ""



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
    var HasCrossVerseBoundaryMarkup = false
    val CrossVerseBoundaryMarkupLocations: MutableList<String> = ArrayList() // Elisions which start with a verse and end with a subverse or vice versa.



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
    var HasAcrosticSpanTags = false
    val AcrosticSpanTagLocations: MutableList<String> = ArrayList()



    /**************************************************************************/
    var HasAcrosticDivTags = false
    val AcrosticDivTagLocations: MutableList<String> = ArrayList()



    /**************************************************************************/
    var TagNames: List<String>? = null


    /**************************************************************************/
    var HasEmptyVersesInRawText = false
    var VersesEmptyInRawText: MutableList<String> = mutableListOf()


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
  fun verseEmptyInRawText (refKey: RefKey)
  {
    m_BibleTextStructure.HasEmptyVersesInRawText = true
    m_BibleTextStructure.VersesEmptyInRawText.add(Ref.rd(refKey).toString())
  }


  /****************************************************************************/
  private fun outputBibleStructureToJson (filePath: String)
  {
    populateBibleDetails(m_DataCollection.getBibleStructure())

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
                 // * HasTables / TableLocations: TableLocations lists the locations of all.
                 //   tables.  HasTables will be true if TableLocations is non-empty.
                 //
                 // * HasAcrosticSpanTags / AcrosticSpanTagLocations / HasAcrosticDivTags /
                 //   AcrosticDivTagLocations: Locations of acrostic tags.  OSIS supports two
                 //   flavours -- a span-type which is usually used to mark single letters
                 //   (hi:acrostic), and a div-type which is used as a heading (title:acrostic).
                 //
                 // * TagNames: a full list of tag names.
                 //
                 // * OutOfOrderVerses: If any verses in the text are out of order, this gives a
                 //   list of the _first_ verse in each chapter which is out of order in relation
                 //   to the verse which appears in the text physically immediately before it.
                 //
                 //******************************************************************************
                   
                 """.trimIndent()

    outputJson(filePath, header, m_BibleTextStructure)
  }


  /****************************************************************************/
  private fun populateBibleDetails (bibleStructure: BibleStructure)
  {
    /**************************************************************************/
    m_DataCollection.getRootNodes().forEach { Dom.getNodesInTree(it).forEach { node -> populateTextFeaturesFromNode(node) } }



    /**************************************************************************/
    m_BibleTextStructure.ModuleName = ConfigData["stepModuleName"]!!
    m_BibleTextStructure.BooksOt =   bibleStructure.getAllBookAbbreviationsOt()
    m_BibleTextStructure.BooksNt =   bibleStructure.getAllBookAbbreviationsNt()
    m_BibleTextStructure.BooksDc =   bibleStructure.getAllBookAbbreviationsDc()
    m_BibleTextStructure.HasOt =     bibleStructure.hasAnyBooksOt()
    m_BibleTextStructure.HasNt =     bibleStructure.hasAnyBooksNt()
    m_BibleTextStructure.HasDc =     bibleStructure.hasAnyBooksDc()
    m_BibleTextStructure.HasFullOt = bibleStructure.hasAllBooksOt()
    m_BibleTextStructure.HasFullNt = bibleStructure.hasAllBooksNt()

    m_BibleTextStructure.OutOfOrderVerses = bibleStructure.getOutOfOrderVerses().map { Ref.rd(it).toString() }
    m_BibleTextStructure.HasOutOfOrderVerses = m_BibleTextStructure.OutOfOrderVerses!!.isNotEmpty()

    m_BibleTextStructure.TagNames = m_NodeNames.sorted()



    /**************************************************************************/
    when (ConfigData["stepReversificationType"]!!.lowercase())
    {
      "conversiontime" ->
      {
        m_BibleTextStructure.ABOUT_THE_FOLLOWING_VERSIFICATION_DATA = "Conversion-time reversification is being applied.  As a result, the text will be forced to be NRSVA compliant, and therefore the following data is not meaningful."
        return
      }

      "runtime" ->
      {
        m_BibleTextStructure.ABOUT_THE_FOLLOWING_VERSIFICATION_DATA = "We are using a bespoke versification scheme.  The text will necessarily comply with that, and therefore the following data is not meaningful."
        return
      }
    }



    /**************************************************************************/
    if (ConfigData["stepVersificationScheme"]!! !in VersificationSchemesSupportedByOsis2mod.getSchemes())
    {
      m_BibleTextStructure.ABOUT_THE_FOLLOWING_VERSIFICATION_DATA = "We are using our own versification scheme, to which the text necessarily conforms, so the following information is irrelevant."
      return
    }



    /**************************************************************************/
    m_BibleTextStructure.ABOUT_THE_FOLLOWING_VERSIFICATION_DATA = "Because reversification is not being applied, the text needs to conform to the selected osis2mod versification scheme.  The following details highlight any issues."

    val analysis = PE_InputVlInputOrUsxInputOrImpInputOsis_To_SchemeEvaluation.evaluateSingleSchemeDetailed(ConfigData["stepVersificationScheme"]!!, m_DataCollection.getBibleStructure())

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
  /****************************************************************************/
  /**                                                                        **/
  /**                             Run features                               **/
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
    var ChangedFootnoteCalloutsToHouseStyle = false
    var ConvertedCrossReferencesToCanonicalForm = false
    var ForcedSelfClosingParas = false
    var ModifiedStrongsReferences = false
    var ReformattedTrailingCanonicalTitles = false
    var SplitCrossVerseBoundarySpanTypeTags = false



    /**************************************************************************/
    var ReversificationType: String = ""
    var HasReversificationDataIssues = false
    var ReversificationDataIssues: MutableList<String>? = null
    var ReversificationMappings: List<String>? = null
    var AcceptedReversificationRows: List<String>? = null
    var DivergencesFromSelectedVersificationScheme = "N/A -- applies to open access modules only."



    /**************************************************************************/
    val FootnoteTextWhichWasOutputInEnglishWhenPerhapsATranslationWouldBeBetter = TreeMap<String, String>()
  }


  /****************************************************************************/
  private fun outputRunDetailsToJson (filePath: String)
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
                 // * ChangedFootnoteCalloutsToHouseStyle: Any user-defined footnote callouts
                 //   have been changed to conform to house style.
                 //
                 // * ForcedSelfClosingParas: If there were any enclosing paragraphs, they have
                 //   been converted into self-closing paragraphs.  (Note that even if we do
                 //   not do this in the converter, it looks as though osis2mod does so
                 //   anyway.)
                 //
                 // * ModifiedStrongsReferences: Strong's references have been standardised.
                 //
                 // * ReformattedTrailingCanonicalTitles: Semantic markup on canonical titles
                 //   at the ends of chapters have been changed to formatting markup.  (There
                 //   seems to be a bug in either osis2mod or in JSword / STEPBible which means
                 //   that canonical titles which are marked as such are not rendered correctly.)
                 //
                 // * SplitCrossVerseBoundarySpanTypeTags: If verse tags fall within an enclosing
                 //   span-type tag, the enclosing tag has been split so that the verse tag can
                 //   be placed outside it, thus avoiding cross-boundary markup.
                 //
                 // * HasFootnotes: What it says on the tin.
                 //
                 // * TranslatableText: A collection of key/value pairs listing all of the
                 //   translatable footnote texts used by this Bible.  It is intended to give
                 //   a handle on the list of texts which might require translation.
                 //   Unfortunately I have no easy way of indicating whether a particular
                 //   item is in English or not, so this list will have to be scanned manually.
                 //
                 //******************************************************************************
                 
                 """.trimIndent()

    outputJson(filePath, header, m_RunFeatures)
  }


  /****************************************************************************/
  /* Sets up portions of the RunFeatures structure which aren't populated by
     any other means. */

  private fun populateRunFeatures ()
  {
    val isOsisRun = "osis" == ConfigData["stepOriginData"]
    m_RunFeatures.ModuleName = ConfigData["stepModuleName"]!!
    m_RunFeatures.RunStartedFrom = ConfigData["stepOriginData"]!!
    m_RunFeatures.VersificationScheme = ConfigData["stepVersificationScheme"]!!
    m_RunFeatures.ReversificationType = if (isOsisRun) "ReversificationType and related parameters are not meaningful when starting from OSIS" else ConfigData["stepReversificationType"]!!



    /**************************************************************************/
    val referenceMappings = ReversificationData.getAugmentedReferenceMappings()
    if (referenceMappings.isNotEmpty())
      m_RunFeatures.ReversificationMappings = referenceMappings.map { Ref.rd(it.key).toString() + " -> " + Ref.rd(it.value.first).toString() + ", " + ReversificationData.getSourceType(it.value.second) }



    /**************************************************************************/
    val acceptedRows = ReversificationData.getAllAcceptedRows()
    if (acceptedRows.isNotEmpty())
      m_RunFeatures.AcceptedReversificationRows = acceptedRows.map { it.toString() }

    m_RunFeatures.HasReversificationDataIssues = null != m_RunFeatures.ReversificationDataIssues
  }





    /****************************************************************************/
    /****************************************************************************/
    /**                                                                        **/
    /**                           Common utilities                             **/
    /**                                                                        **/
    /****************************************************************************/
    /****************************************************************************/

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
  private val m_NodeNames: MutableSet<String> = HashSet()
}
