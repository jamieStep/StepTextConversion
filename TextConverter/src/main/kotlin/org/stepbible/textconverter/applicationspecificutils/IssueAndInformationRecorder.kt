package org.stepbible.textconverter.applicationspecificutils

import com.google.gson.GsonBuilder
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleStructure
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefCollection
import java.io.PrintWriter
import java.util.*
import kotlin.collections.HashSet
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.contains
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.get
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefKey
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.stepbible.textconverter.osisonly.Osis_AudienceAndCopyrightSpecificProcessingHandler
import org.stepbible.textconverter.protocolagnosticutils.reversification.PA_ReversificationDataHandler
import org.stepbible.textconverter.protocolagnosticutils.reversification.PA_ReversificationUtilities
import org.w3c.dom.Node


/****************************************************************************/
/**
 * Handles all kinds of information which we might want to log and / or
 * report immediately and / or accumulate for use in summaries etc.
 *
 * Parallel running: This rather comes in two parts.  The first part allows
 * callers to report issues with the text, and needs to be capable of running
 * multithreaded.  The second part uses this information, and is always
 * called in a sequential environment.
 *
 * @author ARA "Jamie" Jamieson
 */

object IssueAndInformationRecorder: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  @Synchronized fun addChapterWhichWasMissingInTheRawText (text: String)
  {
     if (null == m_BibleTextStructure.ChaptersMissingFromRawTextLocations) m_BibleTextStructure.ChaptersMissingFromRawTextLocations = mutableListOf()
     m_BibleTextStructure.HasChaptersMissingFromRawText = true
     m_BibleTextStructure.ChaptersMissingFromRawTextLocations!!.add(text)
  }


  /****************************************************************************/
  @Synchronized fun addEmptyVerseGeneratedForReversification (text: String)
  {
    if (null == m_RunFeatures.GeneratedEmptyVerseLocations) m_RunFeatures.GeneratedEmptyVerseLocations = mutableListOf()
    m_RunFeatures.HasGeneratedEmptyVerses = true
    m_RunFeatures.GeneratedEmptyVerseLocations!!.add(text)
  }


  /****************************************************************************/
  @Synchronized fun addGeneratedFootnote (text: String)
  {
     if (null == m_RunFeatures.GeneratedFootnoteLocations) m_RunFeatures.GeneratedFootnoteLocations = mutableListOf()
     m_RunFeatures.HasGeneratedFootnotes = true
     m_RunFeatures.GeneratedFootnoteLocations!!.add(text)
  }


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

  @Synchronized fun addTranslatableTextWhichWasInEnglishWhenVernacularWouldBeBetter (key: String, text: String)
  {
    if (ConfigData.isEnglishTranslatableText(key) && "eng" != ConfigData["stepLanguageCode3Char"])
      m_RunFeatures.FootnoteTextWhichWasOutputInEnglishWhenPerhapsATranslationWouldBeBetter[key] = text
  }


  /****************************************************************************/
  @Synchronized fun addVerseWhichWasEmptyInTheRawText (text: String)
  {
    if (null == m_BibleTextStructure.VersesEmptyInRawTextLocations) m_BibleTextStructure.VersesEmptyInRawTextLocations = mutableListOf()
    m_BibleTextStructure.HasEmptyVersesInRawText = true
    m_BibleTextStructure.VersesEmptyInRawTextLocations!!.add(text)
  }


  /****************************************************************************/
  @Synchronized fun addVerseWhichWasMissingInTheRawText (text: String)
  {
     if (null == m_BibleTextStructure.VersesMissingFromRawTextLocations) m_BibleTextStructure.VersesMissingFromRawTextLocations = mutableListOf()
     m_BibleTextStructure.HasVersesMissingFromRawText = true
     m_BibleTextStructure.VersesMissingFromRawTextLocations!!.add(text)
  }


  /****************************************************************************/
  /* Definite issues which may usefully be reported and / or stored within the
     TextFeatures information, but which don't turn up in config files etc. */

  @Synchronized fun crossVerseBoundaryMarkup (location: RefKey, forceError: Boolean = false, reassurance: String? = null)
  {
    val locationAsString = Ref.rd(location).toString()
    report("Cross-verse-boundary markup at ${locationAsString}.", forceError, "LogWarning,Report", location, null, reassurance)
    if (null == m_BibleTextStructure.CrossVerseBoundaryMarkupLocations) m_BibleTextStructure.CrossVerseBoundaryMarkupLocations = mutableListOf()
    m_BibleTextStructure.CrossVerseBoundaryMarkupLocations!!.add(locationAsString)
    m_BibleTextStructure.HasCrossVerseBoundaryMarkup = true
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

  @Synchronized fun elidedVerse (locations: List<RefKey>, text: String)
  {
    report("Elided verse: $text", false, "LogInfo", locations.first(), m_ElidedVerses, null)
    m_BibleTextStructure.HasElisions = true
    if (null == m_BibleTextStructure.ElisionLocations) m_BibleTextStructure.ElisionLocations = mutableListOf()
    m_BibleTextStructure.ElisionLocations!!.addAll(locations.map { Ref.rd(it).toString() })
    if (locations.any { Ref.hasS(it) })
    {
      m_BibleTextStructure.HasElidedSubverses = true
      if (null == m_BibleTextStructure.SubverseElisionLocations) m_BibleTextStructure.SubverseElisionLocations = mutableListOf()
      m_BibleTextStructure.SubverseElisionLocations!!.addAll(locations.filter { Ref.hasS(it) }.map { Ref.rd(it).toString() } )

      if (!locations.all { Ref.hasS(it) })
      {
        m_BibleTextStructure.HasMismatchedVerseSubverseElisions = true
        if (null == m_BibleTextStructure.MismatchedVerseSubverseElisionLocations) m_BibleTextStructure.MismatchedVerseSubverseElisionLocations = mutableListOf()
        m_BibleTextStructure.MismatchedVerseSubverseElisionLocations!!.add(Ref.rd(locations.first()).toString())
      }
    }
  }



  /****************************************************************************/
  @Synchronized fun setHasAcrosticDivTags ()                                     { m_BibleTextStructure.HasAcrosticDivTags = true}
  @Synchronized fun setHasAcrosticSpanTags ()                                    { m_BibleTextStructure.HasAcrosticSpanTags = true}
  @Synchronized fun setChangedFootnoteCalloutsToHouseStyle ()                    { m_RunFeatures.ChangedFootnoteCalloutsToHouseStyle = true }
  @Synchronized fun setConversionTimeReversification ()                          { m_RunFeatures.ReversificationType = "Conversion time" }
  @Synchronized fun setRuntimeReversification ()                                 { m_RunFeatures.ReversificationType = "Run time" }
  @Synchronized fun setConvertedCrossReferencesToCanonicalForm ()                { m_RunFeatures.ConvertedCrossReferencesToCanonicalForm = true }
  @Synchronized fun setForcedSelfClosingParas ()                                 { m_RunFeatures.ForcedSelfClosingParas = true }
  @Synchronized fun setReformattedTrailingCanonicalTitles ()                     { m_RunFeatures.ReformattedTrailingCanonicalTitles = true }
  @Synchronized fun setSplitCrossVerseBoundarySpanTypeTags ()                    { m_RunFeatures.SplitCrossVerseBoundarySpanTypeTags = true }
  @Synchronized fun setStrongs ()                                                { m_RunFeatures.ModifiedStrongsReferences = true }

  @Synchronized fun setHasMultiVerseParagraphs () { TODO() } // Needs to turn up in Sword config.




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
         "Report"     -> Rpt.warning(Ref.rd(location).toString() + ": " + text)
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
      if (null == m_BibleTextStructure.TableLocations) m_BibleTextStructure.TableLocations = mutableListOf()
      m_BibleTextStructure.TableLocations!!.add(m_CurrentVerseSid)
      m_BibleTextStructure.HasTables = true
    }

    else if (m_FileProtocol.isSpeakerNode(node))
    {
      if (null == m_BibleTextStructure.SpeakerLocations) m_BibleTextStructure.SpeakerLocations = mutableListOf()
      m_BibleTextStructure.SpeakerLocations!!.add(m_CurrentVerseSid)
      m_BibleTextStructure.HasSpeakers = true
    }

    else if (m_FileProtocol.isAcrosticSpanNode(node))
    {
      if (null == m_BibleTextStructure.SpeakerLocations) m_BibleTextStructure.SpeakerLocations = mutableListOf()
      m_BibleTextStructure.SpeakerLocations!!.add(m_CurrentVerseSid)
      m_BibleTextStructure.HasSpeakers = true
    }

    else if (m_FileProtocol.isAcrosticDivNode(node))
    {
      if (null == m_BibleTextStructure.SpeakerLocations) m_BibleTextStructure.SpeakerLocations = mutableListOf()
      m_BibleTextStructure.SpeakerLocations!!.add(m_CurrentVerseSid)
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
      if (null == m_BibleTextStructure.SubverseLocations) m_BibleTextStructure.SubverseLocations =  mutableListOf()
      m_BibleTextStructure.SubverseLocations!!.add(m_CurrentVerseSid)
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
    /**************************************************************************/
    var ModuleName: String? = null



    /**************************************************************************/
    val Doc_HasXX = "True if this text contains any books in the given portion of the Bible."
    var HasOT = false
    var HasNT = false
    var HasDC = false



    /**************************************************************************/
    val Doc_HasFull = "True if the relevant portion of the Bible is complete (ie has all necessary books)."
    var HasFullOT = false
    var HasFullNT = false



    /**************************************************************************/
    val Doc_Books = "Lists of books in the various parts of the Bible."
    var BooksOT: List<String>? = null
    var BooksNT: List<String>? = null
    var BooksDC: List<String>? = null



    /**************************************************************************/
    val Doc_HasCrossReferences = "True if the text contains cross-references."
    var HasCrossReferences = false

    val Doc_HasFootnotes = "True if the text contains non-cross-reference footnotes."
    var HasFootnotes = false



    /**************************************************************************/
    val Doc_Elisions = "Details of elisions."
    var HasElisions = false
    var ElisionLocations: MutableList<String>? = null



    /**************************************************************************/
    val Doc_Subverses = "Details of subverses.  There should never be any of these, because we aren't allowed to have subverses."
    var HasSubverses = false
    var SubverseLocations: MutableList<String>? = null



    /**************************************************************************/
    val Doc_ElidedSubverses = "Details of Elisions which start and end with a subverse.  There should never be any of these, bceause we aren't allowed to have subverses."
    var HasElidedSubverses = false
    var SubverseElisionLocations: MutableList<String>? = null



    /**************************************************************************/
    val Doc_MismatchedVerseSubverseElisions = "Details of places where an elision runs from a verse to a subverse or vice versa.  Probably meaningless because we aren't allowed to have subverses."
    var HasMismatchedVerseSubverseElisions = false
    var MismatchedVerseSubverseElisionLocations: MutableList<String>? = null



    /**************************************************************************/
    val Doc_SpeakerTags = "Details of speaker tags."
    var HasSpeakers = false
    var SpeakerLocations: MutableList<String>? = null



    /**************************************************************************/
    val Doc_Tables = "Details of tables."
    var HasTables = false
    var TableLocations: MutableList<String>? = null



    /**************************************************************************/
    val Doc_AcrosticTags = "Details of acrostic tags -- span flavour or div flavour."
    var HasAcrosticSpanTags = false
    var HasAcrosticDivTags = false



    /**************************************************************************/
    val Doc_EmptyVersesInRawText = "Details of any verses which were recognisably empty in the raw text (ie had absolutely not content at all)."
    var HasEmptyVersesInRawText = false
    var VersesEmptyInRawTextLocations: MutableList<String>? = null

    val Doc_ChaptersMissingFromRawText = "Details of any chapters which were missing in the raw text."
    var HasChaptersMissingFromRawText = false
    var ChaptersMissingFromRawTextLocations: MutableList<String>? = null

    val Doc_VersesMissingFromRawText = "Details of any verses which were missing in the raw text."
    var HasVersesMissingFromRawText = false
    var VersesMissingFromRawTextLocations: MutableList<String>? = null



    /**************************************************************************/
    val Doc_OutOfOrderVerses = "Details of any places where verses were supplied out of order."
    var HasOutOfOrderVerses = false
    var OutOfOrderVerseLocations: List<String>? = null



    /**************************************************************************/
    val Doc_CrossBoundaryMarkup = "Details of any places where markup runs across verse boundaries and we were unable to resolve matters."
    var HasCrossVerseBoundaryMarkup = false
    var CrossVerseBoundaryMarkupLocations: MutableList<String>? = null



    /**************************************************************************/
    val Doc_TagNames = "Full list of all tag names used in the raw text."
    var TagNames: List<String>? = null
  }


  /****************************************************************************/
  private fun outputBibleStructureToJson (filePath: String)
  {
    populateBibleDetails(m_DataCollection.getBibleStructure())

    val header = """
                 //******************************************************************************
                 //
                 // Text features for ${ConfigData["stepModuleName"]!!}
                 //
                 //******************************************************************************
                   
                 """.trimIndent()

    outputJson(filePath, header, m_BibleTextStructure)
  }


  /****************************************************************************/
  private fun populateBibleDetails (bibleStructure: BibleStructure)
  {
    /**************************************************************************/
    m_DataCollection.getRootNodes().forEach { Dom.getAllNodesBelow(it).forEach { node -> populateTextFeaturesFromNode(node) } }



    /**************************************************************************/
    m_BibleTextStructure.ModuleName = ConfigData["stepModuleName"]!!
    m_BibleTextStructure.BooksOT    = bibleStructure.getAllBookAbbreviationsOt()
    m_BibleTextStructure.BooksNT    = bibleStructure.getAllBookAbbreviationsNt()
    m_BibleTextStructure.BooksDC    = bibleStructure.getAllBookAbbreviationsDc()
    m_BibleTextStructure.HasOT      = bibleStructure.hasAnyBooksOt()
    m_BibleTextStructure.HasNT      = bibleStructure.hasAnyBooksNt()
    m_BibleTextStructure.HasDC      = bibleStructure.hasAnyBooksDc()
    m_BibleTextStructure.HasFullOT  = bibleStructure.hasAllBooksOt()
    m_BibleTextStructure.HasFullNT  = bibleStructure.hasAllBooksNt()

    m_BibleTextStructure.OutOfOrderVerseLocations = bibleStructure.getOutOfOrderVerses().map { Ref.rd(it).toString() }
    m_BibleTextStructure.HasOutOfOrderVerses = m_BibleTextStructure.OutOfOrderVerseLocations!!.isNotEmpty()

    m_BibleTextStructure.TagNames = m_NodeNames.sorted()
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

    val Doc_CopyrightText = "True if this is a copyright text, or is to be treated as such."
    var CopyrightText = false

    val Doc_ForOnlineUseOnly = "If true, this text can be made available in offline STEPBible."
    var OfflineUsePermitted = false

    val Doc_TargetAudience = "Public / STEPBible only."
    var TargetAudience = ""

    val Doc_RunStartedFrom = "Kind of data used as input on this run."
    var RunStartedFrom = ""

    val Doc_VersificationScheme = "Relevant on public modules only.  Gives the Crosswire versification scheme selected for use."
    var VersificationScheme = ""



    /**************************************************************************/
    val Doc_ChangedFootnoteCalloutsToHouseStyle = "True if we've overridden callouts.  Probably not very meaningful, since if we have any notes, then invariably we _do_ change them."
    var ChangedFootnoteCalloutsToHouseStyle = false

    val Doc_ConvertedCrossReferencesToCanonicalForm = "There are all sorts of ways in which cross-references may be wrong.  This indicates we've had to sort them out."
    var ConvertedCrossReferencesToCanonicalForm = false

    val Doc_ForcedSelfClosingParas = "Indicates that enclosing para:p's have been converted to a self-closing tag at the start of the original paragraph.  Probably not very meaningful because we always _do_ change them."
    var ForcedSelfClosingParas = false

    val Doc_ModifiedStrongsReferences = "Indicates that Strongs references may not have been in canonical form to begin with, and we have had to patch them up."
    var ModifiedStrongsReferences = false

    val Doc_ReformattedTrailingCanonicalTitles = "A bug in osis2mod or the rendering means that canonical titles at the end of a chapter are not handled correctly.  This flags the fact that we have had to apply changes."
    var ReformattedTrailingCanonicalTitles = false

    val Doc_SplitCrossVerseBoundarySpanTypeTags = "Where span-type tags cross a verse boundary, something somewhere sometimes seems to go wrong.  Under such circumstances, we patch things up, and this flags the fact that we have done so."
    var SplitCrossVerseBoundarySpanTypeTags = false



    /**************************************************************************/
    val Doc_ReversificationType = "None or Runtime"
    var ReversificationType = ""

    val DocReversificationDataIssues = "Lists any issues found while processing the reversification data. (In particular, it lists reversification rows whose applicability could not be determined because they used rules which attempted to apply length tests to elided verses.)"
    var HasReversificationDataIssues = false
    var ReversificationDataIssues: MutableList<String>? = null

    val Doc_ReversificationMappings = "Where reversification has been applied, this lists old-to-new reference mappings where old and new are different."
    var HasReversificationMappings = false
    var ReversificationMappings: List<String>? = null

    val Doc_AcceptedReversificationRows = "Reversification rows which pass all the selection rules for this text.  Note that 'AllBibles' rows are selected even when we are not applying reversification."
    var HasAcceptedReversificationRows = false
    var AcceptedReversificationRows: List<String>? = null



    /**************************************************************************/
    val Doc_GeneratedEmptyVerses = "True if the text lacked verses which we have had to create."
    var HasGeneratedEmptyVerses = false
    var GeneratedEmptyVerseLocations: MutableList<String>? = null



    /**************************************************************************/
    val Doc_GeneratedFootnote = "Details of any footnotes generated in the course of the run."
    var HasGeneratedFootnotes = false
    var GeneratedFootnoteLocations: MutableList<String>? = null



    /**************************************************************************/
    val Doc_FootnoteTextWhichWasOutputInEnglishWhenPerhapsATranslationWouldBeBetter = "Details of any any translatable footnote texts used by this Bible for which the English was used because we were lacking a vernacular translation."
    val FootnoteTextWhichWasOutputInEnglishWhenPerhapsATranslationWouldBeBetter = TreeMap<String, String>()
  }


  /****************************************************************************/
  private fun outputRunDetailsToJson (filePath: String)
  {
    populateRunFeatures()

    val header = """
                 //******************************************************************************
                 //
                 // Run details for ${ConfigData["stepModuleName"]!!}
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
    m_RunFeatures.ModuleName = ConfigData["stepModuleName"]!!
    m_RunFeatures.CopyrightText = ConfigData.getAsBoolean("stepIsCopyrightText")
    m_RunFeatures.OfflineUsePermitted = !ConfigData.getAsBoolean("stepOnlineUsageOnly")
    m_RunFeatures.TargetAudience = ConfigData["stepTargetAudience"]!!
    m_RunFeatures.RunStartedFrom = ConfigData["stepOriginData"]!!
    m_RunFeatures.VersificationScheme = ConfigData["stepVersificationScheme"]!!
    m_RunFeatures.ReversificationType = ConfigData["stepReversificationType"]!!



    /**************************************************************************/
    val referenceMappings = Osis_AudienceAndCopyrightSpecificProcessingHandler.getReversificationHandler()?.getRuntimeReversificationMappings() ?: listOf()
    if (referenceMappings.isNotEmpty())
    {
      m_RunFeatures.HasReversificationMappings = true
      m_RunFeatures.ReversificationMappings = referenceMappings.map { Ref.rd(it.first.value).toString() + " -> " + Ref.rd(it.second.value).toString() }
    }



    /**************************************************************************/
    val acceptedRows = PA_ReversificationDataHandler.getSelectedRowsAsStrings()
    if (acceptedRows.isNotEmpty())
    {
      m_RunFeatures.AcceptedReversificationRows = acceptedRows
      m_RunFeatures.HasAcceptedReversificationRows = true
    }

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
        throw StepExceptionWithStackTraceAbandonRun(e)
      }
    }


  /****************************************************************************/
  private val m_BibleTextStructure = BibleTextStructure()
  private val m_RunFeatures = RunFeatures()
  private val m_NodeNames: MutableSet<String> = HashSet()
}
