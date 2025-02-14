/******************************************************************************/
package org.stepbible.textconverter.protocolagnosticutils.reversification

import org.stepbible.textconverter.applicationspecificutils.IssueAndInformationRecorder
import org.stepbible.textconverter.applicationspecificutils.Original
import org.stepbible.textconverter.applicationspecificutils.Revised
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MarkerHandlerFactory
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ParallelRunning
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.protocolagnosticutils.reversification.PA_ReversificationHandler_RunTime.setAlteredStructure
import org.w3c.dom.Node
import java.util.concurrent.ConcurrentHashMap


/******************************************************************************/
/**
 * Performs runtime reversification.  This entails making only minimal changes
 * to the text: the main purpose of the processing here is to identify the ways
 * in which the text deviates from KJVA, so that the relevant information can
 * be supplied to osis2mod.
 *
 * This class does three things.
 *
 * - It identifies those reversification rows which change the verse
 *   numbering.  These need to be reported to our osis2mod.
 *
 * - It may in some cases create verses to fill in holes.  This activity is
 *   limited to verses which the reversification data *anticipates* might be
 *   absent.  It is always possible that the text may contain other holes
 *   which reversification does *not* anticipate, so I have some backstop
 *   processing which runs later in the processing chain to fill in any
 *   remaining holes.
 *
 * - It may add footnotes to verses to give details of how the verse structure
 *   of different texts may differ at that point.  We do not do this on
 *   copyright texts, since we assume that the licensing conditions may
 *   preclude this.  Apart from this, not all verses affected by reversification
 *   are necessarily given footnotes, and where verses *are* given footnotes,
 *   the amount of information in the footnote will depend upon whether the
 *   text is aimed at an academic audience or a general audience.
 *
 *
 * The presence and complexity of the footnotes is determined by the NoteMarker
 * field of each reversification data row:
 *
 * If this starts with Nec, then the footnote is added to all modules where
 * copyright permits.
 *
 * If it starts Opt or Acd, then the associated footnotes are added only to
 * modules aimed at an academic audience.  (Such academic texts therefore
 * contain *all* footnotes, Nec, Opt or Acd.)
 *
 * In the case of academic texts, the content of the individual footnotes is
 * more extensive, because the content of the AncientVersions field is added to
 * it.
 *
 * The reversification data contains *two* footnote columns, one labelled
 * ReversificationNote, and the other VersificationNote.  The former contains
 * data relevant to conversion-time reversification (and may in any case no
 * longer be up to date).  In the present class, we are concerned only with
 * the VersificationNote field.
 *
 * The reversification data gives all note information in English.  Where
 * possible, I convert it to the appropriate vernacular.
 *
 * @author ARA "Jamie" Jamieson
 */

/******************************************************************************/
object PA_ReversificationHandler_RunTime: PA_ReversificationHandler(), ObjectInterface
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
  * Runs the process.
  *
  * @param dataCollection
  */

  override fun process (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    super.extractCommonInformation(dataCollection, true)
    val callout = MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!).get()
    PA_ReversificationDataHandler.process(dataCollection) // Loads the relevant reversification data.
    PA_ReversificationUtilities.setCalloutGenerator { _ -> callout }
    PA_ReversificationUtilities.setFileProtocol(m_FileProtocol)
    PA_ReversificationUtilities.setNotesColumnName("Versification Note")

    if (PA_ReversificationDataHandler.getSelectedRows().isNotEmpty())
      IssueAndInformationRecorder.setRuntimeReversification()



    /**************************************************************************/
    val reversificationDataRowsPerBook = ConcurrentHashMap<Int, List<ReversificationDataRow>>()
    reversificationDataRowsPerBook.putAll(PA_ReversificationDataHandler.getSelectedRowsBySourceBook())



    /**************************************************************************/
    Rpt.reportWithContinuation(level = 1, "Carrying out reversification processing ...") {
      with(ParallelRunning(true)) {
        run {
          reversificationDataRowsPerBook.keys.forEach { bookNo ->
            val rowsForThisBook = reversificationDataRowsPerBook[bookNo]
            if (!rowsForThisBook.isNullOrEmpty())
              asyncable { PA_ReversificationHandler_RunTimePerBook(dataCollection).processRootNode(dataCollection.getRootNode(bookNo)!!, rowsForThisBook) }
          } // forEach
        } // run
      } // parallel
    } // report



    /**************************************************************************/
    if (hasAlteredStructure())
      m_DataCollection.reloadBibleStructureFromRootNodes(false)
  }


  /****************************************************************************/
  /**
  * Returns a map which maps original RefKey to revised RefKey, so that cross-
  * references can be updated if necessary.
  *
  * In run-time reversification, we do not alter the structure of the text, and
  * therefore if cross-references were correct to begin with, they remain correct.
  *
  * @return Map.
  */

  override fun getCrossReferenceMappings (): Map<Original<RefKey>, Revised<RefKey>> = mapOf()


  /****************************************************************************/
  /**
  * Returns information needed by our own version of osis2mod and JSword to
  * handle the situation where we have a Bible which is not KJV(A)-compliant,
  * and which we are not restructuring during the conversion process.
  *
  * The return value is a list of RefKey -> RefKey pairs, mapping source verse
  * to standard verse.  (On PsalmTitle rows, the verse for the standard ref is
  * set to zero.)
  *
  * @return List of mappings, ordered by source RefKey.
  */

  override fun getRuntimeReversificationMappings (): List<Pair<SourceRef<RefKey>, StandardRef<RefKey>>>
  {
    val x = PA_ReversificationHandler_RunTimePerBook(m_DataCollection)
    return PA_ReversificationDataHandler.getSelectedRows()
      .filter { x.reportMapping(it) && it.sourceRefAsRefKey != it.standardRefAsRefKey }
      .map {
        if (it.getField("Action").equals("RenumberTitle", ignoreCase = true))
          Pair(SourceRef(it.sourceRefAsRefKey), StandardRef(Ref.setV(it.standardRefAsRefKey, 0)))
        else
          Pair(SourceRef(it.sourceRefAsRefKey), StandardRef(it.standardRefAsRefKey))
      }
      .sortedBy { it.first.value }
  }
}





/******************************************************************************/
class PA_ReversificationHandler_RunTimePerBook (private val dataCollection: X_DataCollection)
{
  /****************************************************************************/
  fun processRootNode (rootNode: Node, rowsForThisBook: List<ReversificationDataRow>)
  {
    val fileProtocol = dataCollection.getFileProtocol()
    val bookName = fileProtocol.getBookAbbreviation(rootNode); Rpt.reportBookAsContinuation(bookName)
    val sidMap = PA_ReversificationUtilities.makeVerseSidMap(rootNode)
    val canonicalTitlesMap = PA_ReversificationUtilities.makeCanonicalTitlesMap(rootNode)
    rowsForThisBook.forEach { dataRow ->
      val refKey = dataRow.sourceRefAsRefKey // With runtime reversification, it's only the _source_ verse which is available to work with.
      val action = dataRow.action

      if (dataRow.sourceIsPsalmTitle)
      {
        val targetNode = canonicalTitlesMap[Ref.getC(dataRow.sourceRefAsRefKey)]
        m_Actions[action]!!.action(targetNode, dataRow, null) // Third arg says where to insert the new node if we have to generate one.
      }
      else
      {
        val targetNode = sidMap[refKey]
//        Dbg.d(Dom.toString(targetNode!!))
//        Dbg.d(Dom.toString(targetNode!!), "<verse osisID='2Cor.10.5' sID='2Cor.10.5'>")
        m_Actions[action]!!.action(targetNode, dataRow, sidMap.ceilingEntry(refKey).value!!) // Third arg says where to insert the new node if we have to generate one.
      }
    }
  }



  /****************************************************************************/
  fun reportMapping (reversificationDataRow: ReversificationDataRow): Boolean
  {
    return m_Actions[reversificationDataRow["Action"].replace("*", "")]!!.reportMapping
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Actions                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private class ActionDescriptor (val action: (Node?, ReversificationDataRow, Node?) -> Unit, val reportMapping: Boolean = false)
  private val m_Actions: Map<String, ActionDescriptor> = mapOf(
      "ifemptyverse"    to ActionDescriptor(action = ::ifEmpty),
      "keepverse"       to ActionDescriptor(action = ::addFootnote),
      "mergednextverse" to ActionDescriptor(action = ::ignoreProTem),
      "mergedprevverse" to ActionDescriptor(action = ::ignoreProTem),
//      "mergednextverse" to ActionDescriptor(action = ::addFootnote, reportMapping = true),
//      "mergedprevverse" to ActionDescriptor(action = ::addFootnote, reportMapping = true),
//      "mergeverse"      to ActionDescriptor(action = ::addFootnote, reportMapping = true), // Temporary until data is fixed.
//      "mergedverse"     to ActionDescriptor(action = ::addFootnote, reportMapping = true),
      "psalmtitle"      to ActionDescriptor(action = ::addFootnoteToPsalmTitle),
      "renumbertitle"   to ActionDescriptor(action = ::addFootnote, reportMapping = true),
      "renumberverse"   to ActionDescriptor(action = ::addFootnote, reportMapping = true),
  )


  /****************************************************************************/
  private fun ignoreProTem (targetNode: Node?, reversificationDataRow: ReversificationDataRow, dummy: Node?)
  {
  }


  /****************************************************************************/
  private fun addFootnote (targetNode: Node?, reversificationDataRow: ReversificationDataRow, dummy: Node?)
  {
    if (null == targetNode)
      Logger.error(reversificationDataRow.sourceRefAsRefKey, "Runtime reversification source verse does not exist: $reversificationDataRow.")
    else
    {
      val wordCount = try { // On empty verses which we have just created, this may fail, but that's fine -- we want these to appear empty anyway.
        dataCollection.getBibleStructure().getCanonicalTextSize(dataCollection.getFileProtocol().getSidAsRefKey(targetNode))
      }
      catch (_: Exception)
      {
        0
      }


      PA_ReversificationUtilities.addFootnote(targetNode, reversificationDataRow, wordCount)
    }
  }


  /****************************************************************************/
  private fun addFootnoteToPsalmTitle (targetNode: Node?, reversificationDataRow: ReversificationDataRow, dummy: Node?)
  {
    if (null == targetNode)
      Logger.error(reversificationDataRow.sourceRefAsRefKey, "Runtime reversification canonical title does not exist: $reversificationDataRow.")
    else
      PA_ReversificationUtilities.addFootnoteToCanonicalTitle(targetNode, reversificationDataRow)
  }


  /****************************************************************************/
  private fun ifEmpty (targetNode: Node?, reversificationDataRow: ReversificationDataRow, ifInsertingNewNodeInsertBefore: Node?)
  {
    return// $$$$$ DO anything?  Map?
    /**************************************************************************/
    /* If the verse does not exist, create it. */

    var verseNode = targetNode
    if (null == targetNode)
    {
      setAlteredStructure()
      val refKeyForNewVerse = reversificationDataRow.standardRefAsRefKey
      verseNode = PA_ReversificationUtilities.createEmptyVerseForReversification(ifInsertingNewNodeInsertBefore!!, refKeyForNewVerse).first // Create the new verse, and select the sid.
    }



    /**************************************************************************/
    /* In fact we _don't_ always annotate the node, but addFootnote -- or
       the things which it calls -- takes care of that, so we just call it
       here regardless. */

    addFootnote(verseNode, reversificationDataRow, null)
  }
}
