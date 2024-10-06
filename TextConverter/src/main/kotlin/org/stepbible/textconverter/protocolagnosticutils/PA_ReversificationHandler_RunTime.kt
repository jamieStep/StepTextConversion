/******************************************************************************/
package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.applicationspecificutils.X_FileProtocol
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.MarkerHandlerFactory
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ParallelRunning
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.findNodesByAttributeName
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.protocolagnosticutils.PA_ReversificationHandler_RunTime.addFootnotes
import org.w3c.dom.Node
import java.util.*


/******************************************************************************/
/**
 * Performs runtime reversification.  This entails making only minimal changes
 * to the text: the main purpose of the processing here is to identify the ways
 * in which the text deviates from NRSVA, so that the relevant information can
 * be supplied to osis2mod.
 *
 *
 *
 *
 *
 * ## Functionality
 *
 * The processing here may create 'empty' verses to fill in any holes in the
 * versification scheme where the reversification data requires it.  (Not
 * actually entirely empty -- they contain a dash to indicate that the verse is
 * deliberately empty, and they may also hold a footnote.)
 *
 * I would prefer not to create these verses, because the whole aim of runtime
 * reversification is to leave the text as close as possible to its 'raw' form.
 * However, if there are holes in the versification, osis2mod and JSword are
 * unhappy.
 *
 * The processing may also (or indeed may not) add footnotes to verses to
 * provide reversification-related information.
 *
 * On copyright texts we do not do so at all, because we assume that licence
 * conditions may rule out the addition of footnotes.
 *
 * And even on non-copyright texts, the extent to which footnotes are added is
 * limited, being determined by part of the data in the NoteMarker field.
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
object PA_ReversificationHandler_RunTime: PA_ReversificationHandler()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun process (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    super.process(dataCollection) // Loads the relevant reversification data.



    /**************************************************************************/
    flagActions() // Records details of which actions do what.
    val callout = MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!).get()
    val selectedRowsForFillInMissingVerses = getSelectedRowsByBook(MayCreateEmptyVerse)
    markFootnoteTargets() // This just updates the footnote data to indicate which verse (source or standard) we attach the footnote to.
    m_CalloutGenerator = { _ -> callout }
    m_NotesColumnName = "Versification Note"

    with(ParallelRunning(true)) {
      run {
        dataCollection.getRootNodes().forEach { rootNode ->
          asyncable { PA_ReversificationHandler_RunTimePerBook(m_FileProtocol, m_EmptyVerseHandler, selectedRowsForFillInMissingVerses).processRootNode(rootNode) }
        } // forEach
      } // run
    } // parallel

    m_DataCollection.reloadBibleStructureFromRootNodes(false) // We've almost certainly invalidated the structure information by now.
  }


  /****************************************************************************/
  /**
  * Returns information needed by our own version of osis2mod and JSword to
  * handle the situation where we have a Bible which is not NRSV(A)-compliant,
  * and which we are not restructuring during the conversion process.
  *
  * The return value is a list of RefKey -> RefKey pairs, mapping source verse
  * to standard verse.  (On PsalmTitle rows, the verse for the standard ref is
  * set to zero.)
  *
  * @return List of mappings, ordered by source RefKey.
  */

  override fun getRuntimeReversificationMappings (): List<Pair<RefKey, RefKey>>
  {
    return getSelectedRows()
      .filter { 0 != (it.action.actions and ReportMapping) && it.sourceRefAsRefKey != it.standardRefAsRefKey }
      .map {
        if (Action.RenumberTitle == it.action)
          Pair(it.sourceRefAsRefKey, Ref.setV(it.standardRefAsRefKey, 0))
        else
          Pair(it.sourceRefAsRefKey, it.standardRefAsRefKey)
      }
      .sortedBy { it.first }
  }


  /****************************************************************************/
  /* Indicates whether footnotes should be attached to source or standard ref.

     Where we are permitted to create the target, we always want to attach
     footnotes to the standard ref (and should ignore the source ref, because
     it can sometimes be confusing).

     On other rows, I think we're ok annotating the source ref. */

  private fun markFootnoteTargets ()
  {
    getSelectedRows().forEach {
      if (0 != it.action.actions and MayCreateEmptyVerse)
        it.attachFootnoteTo = ReversificationDataRow.AttachFootnoteTo.Standard
      else
        it.attachFootnoteTo = ReversificationDataRow.AttachFootnoteTo.Source
    }
  }


  /****************************************************************************/
  /* Add to the Action instances details of the actions carried out by each. */

  private const val NoAction            = 0x00000001
  private const val MayCreateEmptyVerse = 0x00000002
  private const val ReportMapping       = 0x00000004

  private fun flagActions ()
  {
    Action.EmptyVerse   .actions = MayCreateEmptyVerse
    Action.KeepVerse    .actions = MayCreateEmptyVerse
    Action.MergedVerse  .actions = MayCreateEmptyVerse + ReportMapping
    Action.MissingVerse .actions = MayCreateEmptyVerse
    Action.PsalmTitle   .actions = NoAction
    Action.RenumberTitle.actions = ReportMapping
    Action.RenumberVerse.actions = ReportMapping

    Action.IfAbsent     .actions = NoAction // No action because these two actions are renamed elsewhere, so we never actually have to process them.
    Action.IfEmpty      .actions = NoAction
    Action.entries.forEach { if (0 == it.actions) Logger.error("Action " + it.toString() + "has not been assigned an action.")}
  }
}





/******************************************************************************/
private class PA_ReversificationHandler_RunTimePerBook (val m_FileProtocol: X_FileProtocol,
                                                        val m_EmptyVerseHandler: PA_EmptyVerseHandler,
                                                        val selectedRowsForFillInMissingVerses: Map<Int, List<ReversificationDataRow>>)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Missing verse processing                       **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun processRootNode (rootNode: Node)
  {
    val bookName = m_FileProtocol.getBookAbbreviation(rootNode)
    Rpt.reportBookAsContinuation(bookName)
    fillInMissingVerses(rootNode, selectedRowsForFillInMissingVerses)
    addFootnotes(rootNode)
  }


  /****************************************************************************/
  /**
  * Creates any verses which we identify as missing.  Note that it is always
  * possible that holes remain even after this, because there's no guarantee
  * that translators will not have missed out a verse which reversification
  * does not anticipate.  To cater for this, there is some backstop processing
  * which fills in any holes which have been left after all of the other
  * processing is complete.
  */

  private fun fillInMissingVerses (rootNode: Node, selectedRows: Map<Int, List<ReversificationDataRow>>)
  {
    val bookNo = m_FileProtocol.getBookNumber(rootNode)
    val selectedRowsForThisBook = selectedRows[bookNo]
    if (selectedRowsForThisBook.isNullOrEmpty())
      return

    val sidMap: NavigableMap<RefKey, Node> = TreeMap()
    rootNode.findNodesByAttributeName(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid()).forEach { sidMap[m_FileProtocol.getSidAsRefKey(it)] = it }
    fillInMissingVerses(selectedRowsForThisBook, sidMap)
  }


  /****************************************************************************/
  private fun fillInMissingVerses (reversificationRows: List<ReversificationDataRow>, sidMap: NavigableMap<RefKey, Node>)
  {
    reversificationRows.forEach { dataRow ->
      val refKeyForNewVerse = dataRow.standardRefAsRefKey
      if (refKeyForNewVerse in m_FilledInVerses) return@forEach
      m_FilledInVerses.add(refKeyForNewVerse)
      if (refKeyForNewVerse in sidMap) return@forEach // Verse already exists.
      m_EmptyVerseHandler.createEmptyVerseForReversification(sidMap.ceilingEntry(refKeyForNewVerse)!!.value, refKeyForNewVerse)
    }
  }

  private val m_FilledInVerses: MutableSet<RefKey> = mutableSetOf()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

}
