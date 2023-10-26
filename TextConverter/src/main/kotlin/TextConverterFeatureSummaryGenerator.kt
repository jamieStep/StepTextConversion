package org.stepbible.textconverter

import com.google.gson.GsonBuilder
import org.stepbible.textconverter.support.bibledetails.BibleBookAndFileMapperEnhancedUsx
import org.w3c.dom.Document
import org.stepbible.textconverter.support.bibledetails.BibleBookAndFileMapperRawUsx
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.getExtendedNodeName
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefCollection
import org.stepbible.textconverter.support.ref.RefRange
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Node
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
 * *IMPORTANT:* Note that this information genuinely is relevant only to
 * the *raw* USX text.
 *
 * @author ARA "Jamie" Jamieson
 */

object TextConverterFeatureSummaryGenerator: TextConverterProcessorBase()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner (): String
  {
    return "Recording summary of text features"
  }


  /****************************************************************************/
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("summariseTextFeaturesOnly", 0, "Generate summary of text features, but do not generate OSIS or module.", null, null, false)
  }


  /****************************************************************************/
  override fun pre (): Boolean
  {
    deleteFile(Pair(StandardFileLocations.getTextFeaturesFilePath(), null))
    return true
  }


  /****************************************************************************/
  override fun runMe (): Boolean
  {
    return true
  }


  /****************************************************************************/
  override fun process (): Boolean
  {
    Files.createDirectories(Paths.get(StandardFileLocations.getTextFeaturesFolderPath()))
    outputBibleStructureToJson()
    outputTextFeaturesToJson()
    return !ConfigData.getAsBoolean("summariseTextFeaturesOnly", "no") // Prevent further processing from running.
  }


  /****************************************************************************/
  /**
  * Adds to the text features details of a verse which is excess to the
  * selected versification scheme.
  *
  * @param refAsString Reference for verse.
  */

  fun addExcessVerse (refAsString: String)
  {
    if (null == m_TextFeatures.ExcessVerses) m_TextFeatures.ExcessVerses = ArrayList()
    m_TextFeatures.ExcessVerses!!.add(refAsString)
  }


  /****************************************************************************/
  /**
  * Adds to the text features details of a verse which is excess to the
  * selected versification scheme.
  *
  * @param refAsString Reference for verse.
  */

  fun addMissingVerse (refAsString: String)
  {
    if (null == m_TextFeatures.MissingVerses) m_TextFeatures.MissingVerses = ArrayList()
    m_TextFeatures.MissingVerses!!.add(refAsString)
  }

  /****************************************************************************/
  /**
  * Adds to the text features details of a reversification issues.
  *
  * @param details Details of issue.
  */

  fun addReversificationIssue (details: String)
  {
    if (null == m_TextFeatures.ReversificationDataIssues) m_TextFeatures.ReversificationDataIssues = ArrayList()
    m_TextFeatures.ReversificationDataIssues!!.add(details)
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
    if (ConfigData.isEnglishTranslatableText(key))
      m_TextFeatures.TranslatableText[key] = text
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
     var ModuleName: String? = null

     var RawTextHasOt = false
     var RawTextHasNt = false
     var RawTextHasDc = false
     var RawTextHasFullOt = false
     var RawTextHasFullNt = false
     var RawTextBooksOt: List<String>? = null
     var RawTextBooksNt: List<String>? = null
     var RawTextBooksDc: List<String>? = null

     var EnhancedTextHasOt = false
     var EnhancedTextHasNt = false
     var EnhancedTextHasDc = false
     var EnhancedTextHasFullOt = false
     var EnhancedTextHasFullNt = false
     var EnhancedTextBooksOt: List<String>? = null
     var EnhancedTextBooksNt: List<String>? = null
     var EnhancedTextBooksDc: List<String>? = null

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
                 // NOTE: The information in this file relates to the Bible structure as it
                 // appears in the raw USX files.  Subsequent processing may result in a
                 // structure which differs from the original.  I do, however, show the
                 // revised book list where it differs from the original (as might be the case
                 // if reversification creates any additional books to accommodate verses moved
                 // from elsewhere).
                 //
                 //******************************************************************************   
                 """.trimIndent()
    outputJson(StandardFileLocations.getVernacularBibleStructureFilePath(), header, m_BibleStructure)
  }


  /****************************************************************************/
  private fun populateBibleStructure ()
  {
    m_BibleStructure.ModuleName = ConfigData["stepModuleName"]!!
    m_BibleStructure.RawTextBooksOt = BibleBookAndFileMapperRawUsx.getBooksOt()
    m_BibleStructure.RawTextBooksNt = BibleBookAndFileMapperRawUsx.getBooksNt()
    m_BibleStructure.RawTextBooksDc = BibleBookAndFileMapperRawUsx.getBooksDc()
    m_BibleStructure.RawTextHasOt = BibleBookAndFileMapperRawUsx.hasOt()
    m_BibleStructure.RawTextHasNt = BibleBookAndFileMapperRawUsx.hasNt()
    m_BibleStructure.RawTextHasDc = BibleBookAndFileMapperRawUsx.hasDc()
    m_BibleStructure.RawTextHasFullOt = BibleBookAndFileMapperRawUsx.hasFullOt()
    m_BibleStructure.RawTextHasFullNt = BibleBookAndFileMapperRawUsx.hasFullNt()

     m_BibleStructure.EnhancedTextBooksOt = BibleBookAndFileMapperEnhancedUsx.getBooksOt()
     m_BibleStructure.EnhancedTextBooksNt = BibleBookAndFileMapperEnhancedUsx.getBooksNt()
     m_BibleStructure.EnhancedTextBooksDc = BibleBookAndFileMapperEnhancedUsx.getBooksDc()
     m_BibleStructure.EnhancedTextHasOt = BibleBookAndFileMapperEnhancedUsx.hasOt()
     m_BibleStructure.EnhancedTextHasNt = BibleBookAndFileMapperEnhancedUsx.hasNt()
     m_BibleStructure.EnhancedTextHasDc = BibleBookAndFileMapperEnhancedUsx.hasDc()
     m_BibleStructure.EnhancedTextHasFullOt = BibleBookAndFileMapperEnhancedUsx.hasFullOt()
     m_BibleStructure.EnhancedTextHasFullNt = BibleBookAndFileMapperEnhancedUsx.hasFullNt()

     if (m_BibleStructure.EnhancedTextBooksOt!!.joinToString("").equals(m_BibleStructure.RawTextBooksOt!!.joinToString(""), true))
       m_BibleStructure.EnhancedTextBooksOt = null

     if (m_BibleStructure.EnhancedTextBooksNt!!.joinToString("").equals(m_BibleStructure.RawTextBooksNt!!.joinToString(""), true))
       m_BibleStructure.EnhancedTextBooksNt = null

     if (m_BibleStructure.EnhancedTextBooksDc!!.joinToString("").equals(m_BibleStructure.RawTextBooksDc!!.joinToString(""), true))
       m_BibleStructure.EnhancedTextBooksDc = null
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

  private class TextFeatures
  {
    /**************************************************************************/
    var ModuleName:String = ""
    var VersificationScheme = ""
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
    var HasTables = false
    val TableLocations: MutableList<String> = ArrayList()



    /**************************************************************************/
    var HasSpeakers = false
    val SpeakerLocations: MutableList<String> = ArrayList()



    /**************************************************************************/
    var ReversificationType: String = ""
    var HasReversificationDataIssues = false
    var ReversificationDataIssues: MutableList<String>? = null
    var ReversificationMappings: List<String>? = null
    var AcceptedReversificationRows: List<String>? = null


    /**************************************************************************/
    var TagNamesInRawUsx: List<String>? = null



    /**************************************************************************/
    var ExcessVerses: MutableList<String>? = null // Verses in the text which the selected osis2mod scheme doesn't cater for.
    var MissingVerses: MutableList<String>? = null  // Verses which the selected osis2mod scheme expects and which I had to create at the end of processing to fill the blanks.



    /**************************************************************************/
    val TranslatableText = TreeMap<String, String>()
  }


  /****************************************************************************/
  private fun outputTextFeaturesToJson ()
  {
    populateTextFeatures()

    val header = """
                 //******************************************************************************
                 //
                 // ${ConfigData["stepModuleName"]!!}
                 //
                 // The information in this file is intended mainly for debug purposes, and to
                 // make it possible to locate files with particular characteristics.  The
                 // content is as follows :-
                 //
                 // * A number of elements come in pairs -- Xxx and HasXxx.  In these cases,
                 //   Xxx is a list, and HasXxx is true if Xxx is non-empty.  The HasXxx
                 //   in these cases are intended to make it slightly easier to search for texts
                 //   with the corresponding characteristics.  (Some HasXxx elements are not
                 //   paired with lists, but simply indicate that a particular characteristic is
                 //   or is not present.)
                 //
                 // * ModuleName: What it says on the tin.
                 //
                 // * VersificationScheme: Again, what it says on the tin.  Will be NRSV(A)
                 //   on reversified texts.  Otherwise, it is whatever you specified on the
                 //   command line.
                 //
                 // * HasCrossReferences is true if the text contains ref tags or char:xt tags.
                 //   I do not (presently) record here any details of cross-references which
                 //   don't work for any reason.
                 //
                 // * HasFootnotes is true if the text contains note:f, note:ef or note:fe.
                 //
                 // * HasElisions / AllElisionLocations: All ElisionLocations lists the locations
                 //   of _all_ elisions, regardless of flavour (see below to make sense of that).
                 //   HasElisions is true if AllElisionLocations is non-empty.
                 //
                 // * HasSubverses /SubverseLocations: SubverseLocations indicates all of the
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
                 // * HasTables / TableLocations: TableLocations lists the locations of all tables.
                 //   HasTables will be true if TableLocations is non-empty.
                 //
                 // * HasSpeakers / SpeakerLocations: SpeakerLocations lists the locations of all
                 //   speaker markup.  HasSpeakers will be true if SpeakerLocations is non-empty.
                 //
                 // * ReversificationType: None, Basic, or Academic.
                 //
                 // * HasReversificationDataIssues / ReversificationDataIssues:
                 //   ReversificationDataIssues lists any issues found while processing the
                 //   reversification.  (In particular, it lists reversification rows whose
                 //   applicability could not be determined because they used rules which
                 //   attempted to apply length tests to elided verses.)
                 //   HasReversificationDataIssues is true of any data issues have been recorded.
                 //
                 // * ReversificationMappings: Where reversification has been applied, this lists
                 //   old-to-new reference mappings where old and new are different.
                 //
                 // * AcceptedVersificationRows: Lists all of the applicable versification rows.
                 //
                 // * TagNamesInRawUsx: What it says on the tin: a full list of tag names.
                 //
                 // * TranslatableText: A collection of key/value pairs listing all of the
                 //   translatable footnote texts used by this Bible.  It is intended to give
                 //   a handle on the list of texts which might require translation.
                 //   Unfortunately I have no easy way of indicating whether a particular
                 //   item is in English or not, so this list will have to be scanned manually.
                 //
                 //******************************************************************************
                 """.trimIndent()

    outputJson(StandardFileLocations.getTextFeaturesFilePath(), header, m_TextFeatures)
  }


  /****************************************************************************/
  private fun populateTextFeatures ()
  {
    /**************************************************************************/
    m_TextFeatures.ModuleName = ConfigData["stepModuleName"]!!
    m_TextFeatures.VersificationScheme = ConfigData["stepVersificationSchemeCanonical"]!!
    m_TextFeatures.ReversificationType = ConfigData["stepReversificationType"]!!
    BibleBookAndFileMapperRawUsx.iterateOverAllFiles(::populateTextFeatures)
    m_TextFeatures.TagNamesInRawUsx = m_NodeNames.sorted()



    /**************************************************************************/
    val referenceMappings = ReversificationData.getReferenceMappings()
    if (referenceMappings.isNotEmpty())
      m_TextFeatures.ReversificationMappings = referenceMappings.map { Ref.rd(it.key).toString() + " -> " + Ref.rd(it.value).toString() }



    /**************************************************************************/
    val acceptedRows = ReversificationData.getAllAcceptedRows()
    if (acceptedRows.isNotEmpty())
      m_TextFeatures.AcceptedReversificationRows = acceptedRows.map { it.toString() }

    m_TextFeatures.HasReversificationDataIssues = null != m_TextFeatures.ReversificationDataIssues
  }


  /****************************************************************************/
  private fun populateTextFeatures (bookName: String, filePath: String, document: Document)
  {
    Dom.collectNodesInTree(document).forEach { populateTextFeaturesFromNode(it) }
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
      "note:f", "note:ef", "note:fe" -> m_TextFeatures.HasFootnotes = true

      "para:sp" ->
      {
        m_TextFeatures.SpeakerLocations.add(m_CurrentVerseSid)
        m_TextFeatures.HasSpeakers = true
      }

      "ref", "char:xt" -> m_TextFeatures.HasCrossReferences = true

      "table" ->
      {
        m_TextFeatures.TableLocations.add(m_CurrentVerseSid)
        m_TextFeatures.HasTables = true
      }

      "verse" ->
      {
        val rc = RefCollection.rd(m_CurrentVerseSid)
        if (rc.getLowAsRef().hasS() || rc.getHighAsRef().hasS())
        {
          m_TextFeatures.SubverseLocations.add(m_CurrentVerseSid)
          m_TextFeatures.HasSubverses = true
        }

        if (m_CurrentVerseSid.contains("-"))
        {
          if (m_CurrentVerseSid !in m_TextFeatures.AllElisionLocations)
            m_TextFeatures.AllElisionLocations.add(m_CurrentVerseSid)
          m_TextFeatures.HasElisions = true

          val range = RefRange.rdUsx(m_CurrentVerseSid)
          if (range.getLowAsRef().hasS() || range.getHighAsRef().hasS())
          {
            m_TextFeatures.SubverseElisionLocations.add(m_CurrentVerseSid)
            m_TextFeatures.HasElidedSubverses = true
            if (!range.getLowAsRef().hasS() || range.getHighAsRef().hasS())
            {
              m_TextFeatures.MismatchedVerseSubverseElisionLocations.add(m_CurrentVerseSid)
              m_TextFeatures.HasMismatchedVerseSubverseElisions = true
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
  private val m_BibleStructure = BibleStructure()
  private val m_TextFeatures = TextFeatures()
}