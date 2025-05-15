/******************************************************************************/
package org.stepbible.textconverter.protocolagnosticutils.reversification

import org.stepbible.textconverter.applicationspecificutils.IssueAndInformationRecorder
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
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceAbandonRun
import org.stepbible.textconverter.protocolagnosticutils.PA
import org.w3c.dom.Node
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean


/******************************************************************************/
/**
 * ## Introduction
 *
 * This class, and the ones upon which it relies, is concerned with handling
 * texts which do not follow KJV(A) versification  Such texts are problematical,
 * because they cannot be used directly in things such as interlinear display --
 * words which one text places in verse n in these 'non-standard' texts another
 * text may place in verse m, and so you cannot simply display verse n in one
 * text against verse n in the other.
 *
 * By way of a historical note, originally the idea was that we would physically
 * restructure these texts during the conversion process, moving words around
 * and / or renumbering to produce something which *did* follow KJV(A).
 * However, this would have been precluded by the licence conditions which apply
 * to many of the texts we receive, and we have dropped this.  Instead, we
 * retain the texts as close as possible to their original form, and rely upon
 * our own homebrew osis2mod and JSword to handle divergences at run-time.  This
 * makes it possible to display the texts in their native form when viewing them
 * standalone, but to align them with other texts on-the-fly when things like
 * interlinear display require it.
 *
 *
 *
 *
 *
 * ## Processing overview
 *
 * The processing is driven by a large data file.  The salient portion of this
 * comprises a number of rows, each made up of a number of fields.
 *
 * Only a certain subset of the rows apply to any one text.  That subset is
 * determined by the Tests field of the data.  This field contains a collection
 * of criteria which must be satisfied by the text being processed in order for
 * the row to be applicable.  This aspect of things is handled by
 * [PA_ReversificationDataLoader], such that only rows which pass their
 * respective tests are supplied to the present class for processing.
 *
 * The selected rows may give rise to one or more of three actions here.
 *
 * The row may give rise to mapping information which we output for use by
 * osis2mod and JSword.
 *
 * And / or the row may result in an empty verse being created to fill a hole
 * in the text.  (At the time of writing, this action is taken only on rows
 * whose Action field contains EmptyVerse.)
 *
 * And / or the verse identified by the sourceRef may have a footnote added
 * to it to explain versification issues.
 *
 * All of this is discussed in more detail below.
 *
 *
 *
 *
 * ## Selecting rows
 *
 * The Tests column determines whether a given row applies or not.  It may
 * require that a given (sub)verse exist (or not exist); that it be the last in
 * its chapter; that its length bear some relationship to the length of some
 * other (sub)verse etc.  And it may require the conjunction of these things --
 * the verse must exist while some other does not, or whatever.
 *
 * I should perhaps highlight some implementation decisions in case they need to
 * be queried in future:
 *
 * - A verse is taken as existing if either the verse itself or a collection of
 *   subverses exists.
 *
 * - By contrast, a subverse exists only if the subverse itself exists.
 *
 * - At one stage subverse zero would have required special consideration.
 *   However, there is now no longer any reference to subverse zero within the
 *   Tests column.  (Subverse zero is mentioned elsewhere, but in this section
 *   I am concerned only with the tests.)
 *
 * - Where a verse is made up of subverses, the length of the verse is the
 *   aggregate length of its subverses.
 *
 * - A test involving the length of a verse which forms part of an elision
 *   always fails -- we have no way of assigning a length to the constituent
 *   elements of an elisions.
 *
 *
 *
 *
 *
 * ## Determining what to do
 *
 * Once rows have been selected, it is then necessary to apply them.
 *
 * - All selected rows potentially give rise to annotation, although in
 *   practice not all of them will do so.  See the Annotation section
 *   below for details.
 *
 * - Selected rows whose SourceRef and StandardRef values differ give rise to
 *   mappings which are used by our osis2mod processing.
 *
 * There is one potential wrinkle here.  Some SourceRefs are subverses.  We
 * have to ensure that the OSIS we supply to osis2mod does not contain
 * subverses, because osis2mod cannot cope with them.  It would therefore seem
 * to make sense to filter out any mappings which have subverses as their
 * SourceRef.  Currently I am being told that this is not appropriate -- we
 * want *all* mappings, including ones which refer to subverses.
 *
 *
 *
 *
 * ## Annotation
 *
 * One of the tasks of the processing here is to add annotation to verses in
 * some cases (in the form of footnotes).  Incidentally, the footnote data
 * in the reversification file is always in English, but I do have an extensive
 * file of translations, and try to use those wherever possible.
 *
 * The decision as to whether a footnote is generated at all -- and if so, how
 * comprehensive it should be -- is driven in part by the reversification data
 * and in part by the target audience.
 *
 * The VersificationNote field gives the text for the footnote.  If this field
 * is empty, then no footnote is generated.
 *
 * The SourceRef field indicates the (sub)verse to which any note should be
 * applied.  If that (sub)verse does not exist, then no footnote is generated.
 * (In fact, I have no evidence that the SourceRef might ever *not* exist.  It
 * may be that the nature of the Tests data is such that if a given row is
 * selected, its SourceRef is guaranteed to exist.)
 *
 * The NoteMarker field (or more specifically, the first part of it) determines
 * whether the note is appropriate to the target audience.  Where the
 * NoteMarker field starts with Acd or Opt, the footnote is generated only if
 * the target audience is academic.  Where the field starts with Nec, it is
 * generated for *all* audiences.
 *
 * In the case of academic texts, the content of the individual footnotes is
 * more extensive, because the content of the AncientVersions field is added to
 * it.
 *
 * Note that in addition to the VersificationNote field, there is also a
 * ReversificationNote field.  This contains text which was used in a
 * previous incarnation of the processing, and is no longer relevant.
 *
 * @author ARA "Jamie" Jamieson
 */

/******************************************************************************/
object PA_ReversificationHandler: PA(), ObjectInterface
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

  fun process (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    extractCommonInformation(dataCollection, true)
    dataCollection.reloadBibleStructureFromRootNodes(wantCanonicalTextSize = true)
    val callout = MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!).get()
    PA_ReversificationDataLoader.process(dataCollection) // Loads the relevant reversification data.
    PA_ReversificationUtilities.setCalloutGenerator { _ -> callout }
    PA_ReversificationUtilities.setFileProtocol(m_FileProtocol)
    PA_ReversificationUtilities.setNotesColumnName("Versification Note")

    if (PA_ReversificationDataLoader.getSelectedRows().isNotEmpty())
      IssueAndInformationRecorder.setRuntimeReversification()



    /**************************************************************************/
    /* Split out the selected rows per book, so that we can process things in
       parallel. */

    val reversificationDataRowsPerBook = ConcurrentHashMap<Int, List<ReversificationDataRow>>()
    reversificationDataRowsPerBook.putAll(PA_ReversificationDataLoader.getSelectedRowsBySourceBook())



    /**************************************************************************/
    Rpt.reportWithContinuation(level = 1, "Carrying out reversification processing ...") {
      with(ParallelRunning(true)) {
        run {
          reversificationDataRowsPerBook.keys.forEach { bookNo ->
            val rowsForThisBook = reversificationDataRowsPerBook[bookNo]
            if (!rowsForThisBook.isNullOrEmpty())
              asyncable {
                val createdVerse = PA_ReversificationHandlerPerBook(dataCollection).processRootNode(dataCollection.getRootNode(bookNo)!!, rowsForThisBook)
                if (createdVerse)
                  m_CreatedVerses.set(true)
              } // asyncable
          } // forEach
        } // run
      } // parallel
    } // report



    /**************************************************************************/
    if (m_CreatedVerses.get())
    {
      //Dbg.d(m_DataCollection.convertToDoc())
      m_DataCollection.reloadBibleStructureFromRootNodes(false)
    }
  }


  /****************************************************************************/
  /**
  * Returns information needed by our own version of osis2mod and JSword to
  * handle the situation where we have a Bible which is not KJV(A)-compliant,
  * and which we are not restructuring during the conversion process.
  *
  * The return value is a list of OSISRef -> OSISRef pairs, mapping source verse
  * to standard verse.  (On PsalmTitle rows, the verse for the standard ref is
  * set to zero.)
  *
  * @return List of mappings, following the order of the reversification data
  *   itself.  Each mapping is a pair, the first element being the SourceRef,
  *   and the second being the StandardRef.
  */

  fun getRuntimeReversificationMappings (): List<Pair<SourceRef<String>, StandardRef<String>>>
  {
    val titlePseudoVerseNumber = RefBase.C_TitlePseudoVerseNumber.toString()
    fun replaceTitle (s: String) = s.replace(titlePseudoVerseNumber, "0")

    return PA_ReversificationDataLoader.getSelectedRows()
      .filter { row -> row.sourceRef != row.standardRef }
      .map { row -> Pair(SourceRef(replaceTitle(row.sourceRef.toStringOsis())), StandardRef(replaceTitle(row.standardRef.toStringOsis()))) }
  }


  private var m_CreatedVerses = AtomicBoolean(false)
}





/******************************************************************************/
class PA_ReversificationHandlerPerBook (private val dataCollection: X_DataCollection)
{
  /****************************************************************************/
  /* Returns true if any nodes were created. */

  fun processRootNode (rootNode: Node, rowsForThisBook: List<ReversificationDataRow>): Boolean
  {
    /**************************************************************************/
    val fileProtocol = dataCollection.getFileProtocol()
    val bookName = fileProtocol.getBookAbbreviation(rootNode); Rpt.reportBookAsContinuation(bookName)
    var createdVerses = false



    /**************************************************************************/
    /* Canonical titles are relatively easy because we should be able to rely
       upon them existing -- we never create them here, and if they _don't_
       exist here, it probably indicates an error. */

    val canonicalTitlesMap = PA_ReversificationUtilities.makeCanonicalTitlesMap(rootNode)
    rowsForThisBook
      .filter {dataRow -> RefBase.C_TitlePseudoVerseNumber == dataRow.sourceRef.getV() }
      .forEach { dataRow ->
        val targetNode = canonicalTitlesMap[dataRow.sourceRef.getC()] ?: throw StepExceptionWithoutStackTraceAbandonRun("Reversification -- missing canonical title: $dataRow")
        PA_ReversificationUtilities.addFootnoteToCanonicalTitle(targetNode, dataRow) }



    /**************************************************************************/
    /* Probably a good idea to create any missing verses next.  At the time of
       writing, only IfEmpty can do that, and I think that's best seen really
       as a kind of side-effect -- the main purpose of IfEmpty is to annotate
       if the verse is empty, but if the verse is _missing_ then it's ok to
       create it. */

    var sidMap = PA_ReversificationUtilities.makeVerseSidMap(rootNode)
    val chapterMap = PA_ReversificationUtilities.makeChapterSidMap(rootNode)
    rowsForThisBook
      .filter {dataRow -> dataRow["Action"].startsWith("IfEmpty", ignoreCase = true) }
      .filterNot { dataRow -> dataRow.sourceRef.toRefKey() in sidMap }
      .forEach { dataRow ->
        //Dbg.d(dataRow.rowNumber == 22928 || dataRow.rowNumber == 22820)
        createdVerses = true
        val newNodes = dataCollection.getFileProtocol().getEmptyVerseHandler().createEmptyVerseForReversification(rootNode.ownerDocument, dataRow.sourceRef.toRefKey())
        val refKey = dataRow.sourceRef.toRefKey()
        val insertBefore = sidMap.ceilingEntry(refKey)
        if (null == insertBefore)
        {
          val chapterNode = chapterMap[dataRow.sourceRef.toRefKey_bc()]!!
          Dom.addChildren(chapterNode, newNodes)
        }
        else
          Dom.insertNodesBefore(insertBefore.value, newNodes)
      }



    /**************************************************************************/
    /* That just leaves the job of annotating everything bar psalm titles
       (which we already annotated earlier).  We need to rebuild the sid map
       if we created any new verses, so that they appear in the map. */

    if (createdVerses)
      sidMap = PA_ReversificationUtilities.makeVerseSidMap(rootNode)

    rowsForThisBook
      .filterNot {dataRow -> RefBase.C_TitlePseudoVerseNumber == dataRow.sourceRef.getV() }
      .forEach { addFootnote(it, sidMap) }


    /**************************************************************************/
    return createdVerses
  }


  /****************************************************************************/
  private fun addFootnote (dataRow: ReversificationDataRow, sidMap: Map<RefKey, Node>)
  {
    val sourceRefKey = dataRow.sourceRef.toRefKey()
    val sourceNode = sidMap[sourceRefKey] ?: sidMap[Ref.clearS(sourceRefKey)] // 03-Apr-2025: Added clearS.
    if (null == sourceNode)
    {
      Logger.warning(sourceRefKey, "Runtime reversification source verse does not exist: $dataRow.")
      return
    }

    val wordCount = try { // On empty verses which we have just created, this may fail, but that's fine -- we want these to appear empty anyway.
        dataCollection.getBibleStructure().getCanonicalTextSize(dataCollection.getFileProtocol().getSidAsRefKey(sourceNode))
      }
      catch (_: Exception)
      {
        0
      }

      PA_ReversificationUtilities.addFootnote(sourceNode, dataRow, wordCount)
  }
}

@JvmInline value class SourceRef  <T> (val value: T)
@JvmInline value class StandardRef<T> (val value: T)

