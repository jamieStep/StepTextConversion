/******************************************************************************/
package org.stepbible.textconverter.protocolagnosticutils.reversification

import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceAbandonRun
import org.stepbible.textconverter.protocolagnosticutils.reversification.PA_ReversificationHandler_RunTime.setAlteredStructure
import org.w3c.dom.Node
import java.util.*
import java.util.concurrent.ConcurrentHashMap


/******************************************************************************/
/**
 * <span class='important'>IMPORTANT: This class is still under construction
 * at the time of writing.  It is probably incomplete, and definitely entirely
 * untested.</span>
 *
 * Performs conversion time reversification.  This may entail making
 * significant changes to the verse structure.
 *
 * This form of reversification may create verses, may renumber verses in situ,
 * may move verses to different locations (including to different books -- even
 * books which do not exist in the text as supplied by the translators), may
 * rejig psalm titles, and may add extensive footnotes.
 *
 * These kinds of changes will almost certainly be ruled out by the licence
 * conditions on copyright texts.  In addition, they may produce a text which
 * looks wrong to a layman who is reasonably familiar with the text in its 'raw'
 * form.  For these reasons, this form of processing is likely to be used only
 * very rarely if at all (and therefore will almost certainly not have been
 * exercised particularly thoroughly).  It is anticipated that use of this class
 * will be limited to non-copyright texts aimed mainly at an academic audience.
 *
 * @author ARA "Jamie" Jamieson
 */

/******************************************************************************/
object PA_ReversificationHandler_ConversionTime: PA_ReversificationHandler()
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
  * Returns a map of mappings keyed on original RefKey and giving the revised
  * RefKey, which can be used to update cross-references.
  *
  * @return Mappings.
  */

  override fun getCrossReferenceMappings () = m_CrossReferenceMappings


  /****************************************************************************/
  /**
  * Runs the process.
  *
  * @param dataCollection
  */

  override fun process (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    if (true) TODO("*** This code has never been tested ***")



    /**************************************************************************/
    super.extractCommonInformation(dataCollection, true)
    val callout = MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!).get()
    PA_ReversificationDataHandler.process(dataCollection) // Loads the relevant reversification data.
    PA_ReversificationUtilities.setCalloutGenerator { _ -> callout }
    PA_ReversificationUtilities.setFileProtocol(m_FileProtocol)
    PA_ReversificationUtilities.setNotesColumnName("Reversification Note")



    /**************************************************************************/
    val reversificationDataRowsPerBook = ConcurrentHashMap<Int, List<ReversificationDataRow>>()
    reversificationDataRowsPerBook.putAll(PA_ReversificationDataHandler.getSelectedRowsBySourceBook())
    makeMappingsNeededToReviseCrossReferences()



    /**************************************************************************/
    Rpt.reportWithContinuation(level = 1, "Carrying out reversification processing ...") {
      with(ParallelRunning(true)) {
        run {
          reversificationDataRowsPerBook.keys.forEach { bookNo ->
            val rowsForThisBook = reversificationDataRowsPerBook[bookNo]
            if (!rowsForThisBook.isNullOrEmpty())
              asyncable {
                val mappings = PA_ReversificationHandler_ConversionTimePerBookGeneral(m_DataCollection).processRootNode(dataCollection.getRootNode(bookNo)!!, rowsForThisBook)
                recordDetailsOfEntireChapterMapping(mappings)
              } // asyncable
          } // forEach
        } // run
      } // parallel
    } // report



    /**************************************************************************/
    Rpt.reportWithContinuation(level = 1, "Carrying out reversification processing part 2...") {
      with(ParallelRunning(true)) {
        run {
          m_MoveBlocks.keys.forEach { bookNo ->
            asyncable { PA_ReversificationHandler_ConversionTimePerBookMovePartB(m_DataCollection).processRootNode(dataCollection.getRootNode(bookNo)!!, m_MoveBlocks[bookNo]!!) }
          } // forEach
        } // run
      } // parallel
    } // report



    /**************************************************************************/
    clearMoveBlocks() // Free up memory.
    if (hasAlteredStructure())
      m_DataCollection.reloadBibleStructureFromRootNodes(false)
  }


  /****************************************************************************/
  /* Stores move data to the holding structure.  Intended for use only by
     PA_ReversificationHandler_ConversionTimePerBookMovePartB. */

  @Synchronized internal fun saveMoveDetails (bookNo: Int, container: Node)
  {
    var details = m_MoveBlocks[bookNo]
    if (null == details)
    {
      details = mutableListOf()
      m_MoveBlocks[bookNo] = details
    }

    details.add(container)
  }


  /****************************************************************************/
  private fun clearMoveBlocks ()
  {
    m_MoveBlocks.clear()
  }


  /****************************************************************************/
  /* This is called outside of parallel processing, so no precautions are
     needed here.  It treats each line as a potential source of mappings in
     its own right.  (It is possible that we may be moving an entire chapter,
     something which this present class won't itself know.  We rely upon

   */

  private fun makeMappingsNeededToReviseCrossReferences ()
  {
     PA_ReversificationDataHandler.getSelectedRows()
       .filter { "renumberverse" == it.action }
       .forEach { m_CrossReferenceMappings[Original(it.sourceRefAsRefKey)] = Revised(it.standardRefAsRefKey) }
  }


  /****************************************************************************/
  /* Records details of an entire chapter being moved to a new location.  This
     is called within the parallel processing part of the code, so precautions
     are needed. */

  @Synchronized private fun recordDetailsOfEntireChapterMapping (mappings: Map<Original<RefKey>, Revised<RefKey>>)
  {
    m_CrossReferenceMappings.putAll(mappings)
  }


  /****************************************************************************/
  private val m_CrossReferenceMappings = mutableMapOf<Original<RefKey>, Revised<RefKey>>()
  private val m_MoveBlocks = ConcurrentHashMap<Int, MutableList<Node>>()
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* Moves are handled by PA_ReversificationHandler_ConversionTimePerBookMovePartA
   (qv).  The present class handles everything else, and is instantiated
   separately for each book being processed. */

internal class PA_ReversificationHandler_ConversionTimePerBookGeneral (private val dataCollection:X_DataCollection)
{
  /****************************************************************************/
  /* The return value contains mappings relating old chapter to new chapter for
     moves which affect entire chapters.  (Moves affecting individual verses or
     subverses are handled in PA_ReversificationHandler_ConversionTime.) */

  fun processRootNode (rootNode: Node, rowsForThisBook: List<ReversificationDataRow>): Map<Original<RefKey>, Revised<RefKey>>
  {
    /**************************************************************************/
    m_RootNode = rootNode
    m_FileProtocol = dataCollection.getFileProtocol()
    val bookName = m_FileProtocol.getBookAbbreviation(rootNode); Rpt.reportBookAsContinuation(bookName)



    /**************************************************************************/
    /* Moves are flagged with an asterisk in the Action field of the
       reversification data.  At the time of writing, they may be
       RenumberVerse, KeepVerse or MergedVerse.  The last two of these may seem
       a little odd, particularly since in a few cases the source and standard
       ref are the same.  I _think_ the explanation is that these verses may
       individually not be subject to renumbering, but fall within a block of
       verses which overall are being altered.  Perhaps, for instance, we have
       verses a, b and c in the input, and want to end up with c, b, a.  Here
       the three verses taken jointly are regarded as a Move block, but verse
       b is not actually changing. */

    val reversificationRowsForRenumberTitle = rowsForThisBook.filter { "renumbertitle" == it.action }
    val reversificationRowsForMoves = rowsForThisBook.filter { it.isMove }
    val reversificationRowsForRenumberInSitu = rowsForThisBook.filter { !it.isMove && "renumberverse" == it.action}
    val reversificationRowsForGeneral = rowsForThisBook - reversificationRowsForRenumberTitle.toSet() - reversificationRowsForMoves.toSet() - reversificationRowsForRenumberInSitu.toSet()



    /**************************************************************************/
    val res = PA_ReversificationHandler_ConversionTimePerBookRenumbersAndMovesPartA(dataCollection)
      .process(m_RootNode, reversificationRowsForRenumberInSitu, reversificationRowsForMoves, reversificationRowsForRenumberTitle)
    processRowsNotCateredForBySpecialistProcessing(reversificationRowsForGeneral)
    return res
  }


  /****************************************************************************/
  private fun processRowsNotCateredForBySpecialistProcessing (dataRows: List<ReversificationDataRow>)
  {
    dataRows.forEach { dataRow ->
      val refKey = dataRow.standardRefAsRefKey // With runtime reversification, it's  the _standard_ verse which is available to work with.

      if (dataRow.sourceIsPsalmTitle)
      {
        val targetNode = m_CanonicalTitlesMap[Ref.getC(dataRow.sourceRefAsRefKey)]
        m_Actions[dataRow["Action"]]!!(targetNode, dataRow, null) // Third arg says where to insert the new node if we have to generate one.
      }
      else
      {
        val targetNode = m_VerseSidMap[refKey]
        m_Actions[dataRow["Action"]]!!(targetNode, dataRow, m_VerseSidMap.ceilingEntry(refKey).value!!) // Third arg says where to insert the new node if we have to generate one.
      }
    }
  }


  /****************************************************************************/
  private lateinit var m_FileProtocol: X_FileProtocol
  private lateinit var m_RootNode: Node
  private val m_CanonicalTitlesMap: Map<Int, Node> by lazy { PA_ReversificationUtilities.makeCanonicalTitlesMap(m_RootNode) }
  private val m_VerseSidMap: NavigableMap<RefKey, Node> by lazy { PA_ReversificationUtilities.makeVerseSidMap(m_RootNode) }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Actions                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* This lists the 'general' actions, after we've removed Moves. */

  private val m_Actions: Map<String, (Node?, ReversificationDataRow, Node?) -> Unit> = mapOf(
      "ifemptyverse"  to ::ifEmpty,
      "keepverse"     to ::createIfNecessaryAndAddFootnote,
      "mergedverse"   to ::createIfNecessaryAndAddFootnote,
      "psalmtitle"    to ::psalmTitle,
      "renumbertitle" to ::exception, // These should definitely have been weeded out before the code uses this table.
      "renumberverse" to ::exception, // These should definitely have been weeded out before the code uses this table.
  )


  /****************************************************************************/
  private fun addFootnote (verseNode: Node?, reversificationDataRow: ReversificationDataRow, wordsAlreadyInVerse: Int)
  {
    if (null == verseNode)
      Logger.error(reversificationDataRow.sourceRefAsRefKey, "Runtime reversification source verse does not exist: $reversificationDataRow.")
    else
      PA_ReversificationUtilities.addFootnote(verseNode, reversificationDataRow, wordsAlreadyInSupposedlyEmptyVerse = wordsAlreadyInVerse)
  }


  /****************************************************************************/
  private fun createIfNecessaryAndAddFootnote (verseNode: Node?, reversificationDataRow: ReversificationDataRow, ifInsertingNewNodeInsertBefore: Node?)
  {
    /**************************************************************************/
    /* Create the verse if it does not already exist. */

    if (null == verseNode)
    {
      setAlteredStructure()
      val refKeyForNewVerse = reversificationDataRow.standardRefAsRefKey
      val newVerseNode = PA_ReversificationUtilities.createEmptyVerseForReversification(ifInsertingNewNodeInsertBefore!!, refKeyForNewVerse).first // Create the new verse, and select the sid.
      addFootnote(newVerseNode, reversificationDataRow, 0)
      return
    }



    /**************************************************************************/
    /* And annotate it regardless of whether it existed before or not. */

    addFootnote(verseNode, reversificationDataRow, dataCollection.getBibleStructure().getCanonicalTextSize(m_FileProtocol.getSid(verseNode))) // Standard verse definitely _does_ exist.
  }


  /****************************************************************************/
  private fun exception (verseNode: Node?, reversificationDataRow: ReversificationDataRow, dummy: Node?)
  {
    throw StepExceptionWithoutStackTraceAbandonRun("Conversion-time reversification general action processing cannot handle $reversificationDataRow.")
  }


  /****************************************************************************/
  private fun ifEmpty (verseNode: Node?, reversificationDataRow: ReversificationDataRow, ifInsertingNewNodeInsertBefore: Node?)
  {
    /**************************************************************************/
    /* If the verse does not exist, we create it and annotate it. */

    if (null == verseNode)
    {
      setAlteredStructure()
      val refKeyForNewVerse = reversificationDataRow.standardRefAsRefKey
      val newVerseNode = PA_ReversificationUtilities.createEmptyVerseForReversification(ifInsertingNewNodeInsertBefore!!, refKeyForNewVerse).first // Create the new verse, and select the sid.
      addFootnote(newVerseNode, reversificationDataRow, 0)
      return
    }



    /**************************************************************************/
    /* That just leaves the case that the verse exists but is empty.  Here I've
       been asked to add the note even if one already exists. */

    addFootnote(verseNode, reversificationDataRow, dataCollection.getBibleStructure().getCanonicalTextSize(dataCollection.getFileProtocol().getSidAsRefKey(verseNode))) // Standard verse definitely _does_ exist.
  }


  /****************************************************************************/
  private fun psalmTitle (targetNode: Node?, reversificationDataRow: ReversificationDataRow, dummy: Node?)
  {
    PA_ReversificationUtilities.addFootnoteToCanonicalTitle(targetNode!!, reversificationDataRow)
  }
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* This class processes Moves and Renumbers.  It is convenient to gather both
   together because they both rely upon the same data structures -- structures
   which are expensive to build up, and which therefore we do not want to
   recreate.

   At the time of writing, Moves incorporate RenumberVerse, KeepVerse and
   MergedVerse. */

internal class PA_ReversificationHandler_ConversionTimePerBookRenumbersAndMovesPartA (val dataCollection: X_DataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process (bookNode: Node,
               renumberInSituRows: List<ReversificationDataRow>,
               moveRows: List<ReversificationDataRow>,
               renumberTitleRows: List<ReversificationDataRow>): Map<Original<RefKey>, Revised<RefKey>>
  {
    /**************************************************************************/
    m_FileProtocol = dataCollection.getFileProtocol()
    m_BookNode = bookNode
    getNodesAndIndices(bookNode) // Gets a mapping between sids / eids and indexes into a full list of nodes.
    val res = mutableMapOf<Original<RefKey>, Revised<RefKey>>() // Details of entire _chapters_ which have been moved (if any).



    /**************************************************************************/
    renumberInSituRows.forEach(::renumberInSitu)



    /**************************************************************************/
    val moveGroups = accumulateMoveGroups(moveRows) // Where several consecutive rows move a block of verses from one place to another, it's convenient to know.
    markSpecialistMoves(moveGroups) // Currently this does just one thing -- it identifies cases where we are actually moving an entire chapter.

    moveGroups.forEach {
      if (it.isEntireChapter)
      {
        val x = processMovePartAEntireChapter(it)
        res[x.first] = x.second // This is one mapping the caller won't have identified, because they didn't look for chapter moves.  This lets them know.
      }
      else
        processMovePartAVerses(m_NodesAndIndices.sidMap[it.rows.first().sourceRefAsRefKey]!!,
                               m_NodesAndIndices.eidMap[it.rows.last().sourceRefAsRefKey]!!,
                               it, "_X_reversificationMovedVersesBlock")
    }



    /**************************************************************************/
    val renumberTitleGroups = accumulateMoveGroups(renumberTitleRows) // Groups together those few places where _two_ verses feed into the title.
    renumberTitleGroups.forEach { grp ->
        val containerNode = processMovePartAVerses(m_NodesAndIndices.sidMap[grp.rows.first().sourceRefAsRefKey]!!,
                                                   m_NodesAndIndices.eidMap[grp.rows.last().sourceRefAsRefKey]!!,
                                                   grp, "_X_reversificationMovedCanonicalTitleVerses")

        // This may be belt and braces.  I want to make the block look as though it ends at verse 0, so I'll insert
        // it before v1.  I think, in fact, that the data may already say that it ends at 0, but just in case ...
        var x = grp.rows.first().standardRefAsRefKey; x = Ref.setV(x,0)
        containerNode["start"] = x.toString()
        containerNode["end"] = x.toString()
    }



    /**************************************************************************/
    return res
  }


  /****************************************************************************/
  private lateinit var m_BookNode: Node
  private lateinit var m_FileProtocol: X_FileProtocol
  private lateinit var m_NodesAndIndices: NodesAndIndices // Maps between RefKeys and the sids / eids where they appear.





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Acquiring data                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Collects together consecutive Move rows which can be actioned as a block.
     This is useful because USX and OSIS both support (and by implication,
     encourage) markup which runs across verse boundaries.  It is not possible
     to move a verse to a new location if there is markup which runs across its
     boundaries, and although I do my best in earlier processing to organise
     things to minimise such issues, there is no guarantee I can get rid of
     them altogether.  If I have 100 verses to move and I move them all
     separately, then if any one of them has cross-boundary markup, I'm stuck.
     If I can move all 100 as a single block, by contrast, then I need worry
     about cross-boundary markup only in respect of the first and last verses
     in the block.

     Note that where dealing with subverses, groups do not extend across the
     owning verse boundary.

     The aim here is to identify groups of consecutive rows where ...

     * All the source elements are verses, or all are subverses.

     * All of the standard elements are verses, or all are subverses.

     * The source elements increment by one verse if they are verses, or by one
       subverse if they are subverses.

     * Ditto for standard elements.

     The return value is a map keyed on old RefKey with new RefKey as the
     associated value, covering moves for entire chapters only.  Mappings for
     individual verses and subverses are handed in PA_ReversificationHandler_ConversionTime.
  */

  private fun accumulateMoveGroups (dataRows: List<ReversificationDataRow>): List<MoveGroup>
  {
    val res = mutableListOf<MoveGroup>()
    val sortedRows = dataRows.sortedWith(::cfForMoveGroups)
    var  ixLow = 0
    while (ixLow < sortedRows.size)
    {
      var refKeySourcePrev = sortedRows[ixLow].sourceRefAsRefKey
      var refKeyStandardPrev = sortedRows[ixLow].standardRefAsRefKey
      val refKeySourceInterval = if (Ref.hasS(refKeySourcePrev)) 1 else RefBase.C_Multiplier
      val refKeyStandardInterval = if (Ref.hasS(refKeyStandardPrev)) 1 else RefBase.C_Multiplier

      var  ixHigh = ixLow + 1
      while (ixHigh < sortedRows.size)
      {
        val refKeySourceThis = sortedRows[ixHigh].sourceRefAsRefKey
        val refKeyStandardThis = sortedRows[ixHigh].standardRefAsRefKey
        if (refKeySourceThis - refKeySourcePrev != refKeySourceInterval) break
        if (refKeyStandardThis - refKeyStandardPrev != refKeyStandardInterval) break
        refKeySourcePrev = refKeySourceThis
        refKeyStandardPrev = refKeyStandardThis
        ++ixHigh
      }

      res.add(MoveGroup(sortedRows.subList(ixLow, ixHigh)))
      ixLow = ixHigh
    }

    return res
  }


  /****************************************************************************/
  /**
   * Gives ordering information.  Rows will be ordered on increasing source
   * refKey, and within that on increasing standard refKey.
   *
   * @param a Row to compare.
   * @param b Row to compare.
   *
   * @return  Ordering information.
   */

  private fun cfForMoveGroups (a: ReversificationDataRow, b: ReversificationDataRow): Int
  {
    fun zeroiseSubverseIfLacking (x: RefKey): RefKey = if (Ref.hasS(x)) x else Ref.setS(x, 0)

    val aSourceRefKey   = zeroiseSubverseIfLacking(a.sourceRefAsRefKey)
    val bSourceRefKey   = zeroiseSubverseIfLacking(b.sourceRefAsRefKey)
    val aStandardRefKey = zeroiseSubverseIfLacking(a.standardRefAsRefKey)
    val bStandardRefKey = zeroiseSubverseIfLacking(b.standardRefAsRefKey)

    return if (aSourceRefKey < bSourceRefKey) -1
           else if (aSourceRefKey > bSourceRefKey) 1
           else if (aStandardRefKey < bStandardRefKey) -1
           else if (aStandardRefKey > bStandardRefKey) 1
           else 0
  }


  /****************************************************************************/
  /* Marks groups which involve an entire chapter. */

  private fun markSpecialistMoves (moveGroups: List<MoveGroup>)
  {
    fun process (grp: MoveGroup)
    {
      if (grp.rows.first().sourceRef.hasS()) return
      if (grp.rows.first().standardRef.hasS()) return

      if (1 != grp.rows.first().sourceRef.getV()) return
      if (1 != grp.rows.first().standardRef.getV()) return

      if (dataCollection.getBibleStructure().getLastVerseNo(grp.rows.first().sourceRef) != grp.rows.last().sourceRef.getV()) return

      grp.isEntireChapter = true
    }

    moveGroups.forEach { process(it) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Renumber in situ                            **/
  /**    Processing in its own right, and also used from Move processing.    **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Changes the sid and eid of the source verse and adds a footnote if
     appropriate. */

  private fun renumberInSitu (row: ReversificationDataRow)
  {
    val newRef = m_FileProtocol.refToString(row.standardRefAsRefKey)
    val (sidVerse, eidVerse) = m_NodesAndIndices.getSidAndEid(row.sourceRefAsRefKey)
    sidVerse[m_FileProtocol.attrName_verseSid()] = newRef
    eidVerse[m_FileProtocol.attrName_verseEid()] = newRef
    PA_ReversificationUtilities.addFootnote(sidVerse, row, wordsAlreadyInSupposedlyEmptyVerse = 999) // I assume the verse will already have content, and 999 is as good a number as any to indicate this.
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Moving chapters                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* I don't think we move entire chapters much, so it's probably ok to search
     for the chapter on each call here, rather than maintain an index whose
     accuracy we need to worry about. By the time this processing is reached,
     we can rely upon chapters being containing nodes, and therefore there is
     no possibility that we might have to contend with cross-boundary markup. */

  private fun processMovePartAEntireChapter (moveGroup: MoveGroup): Pair<Original<RefKey>, Revised<RefKey>>
  {
    moveGroup.rows.forEach { renumberInSitu(it) } // Change the references on all verses.

    val row = moveGroup.rows[0] // We can determine the chapter which is being moved by reference to the sourceRef of the first record in the group.
    val sourceChapterRefKey = Ref.clearS(Ref.clearV(row.sourceRefAsRefKey))
    val standardChapterRefKey = Ref.clearS(Ref.clearV(row.standardRefAsRefKey))

    val container = Dom.createNode(m_BookNode.ownerDocument, "<_X_reversificationMovedChapter/>") // Create a container node to hold the data.
    val x = sourceChapterRefKey.toString(); container["start"] = x; container["end"] = x

    val chapterNodeBeingMoved = m_BookNode.findNodesByName("chapter").firstOrNull { sourceChapterRefKey == m_FileProtocol.readRef(it[m_FileProtocol.attrName_chapterSid()]!!).toRefKey() } ?: throw StepExceptionWithStackTraceAbandonRun("Conversion-time reversification: Couldn't find source chapter for $row.")
    Dom.deleteNode(chapterNodeBeingMoved) // We can now get rid of the chapter from the source.
    container.appendChild(chapterNodeBeingMoved) // Save the chapter in the container.
    PA_ReversificationHandler_ConversionTime.saveMoveDetails(row.standardRef.getB(), container) // Save the container for part B of the processing.
    setAlteredStructure() // We are definitely altering the structure.

    return Pair(Original(sourceChapterRefKey), Revised(standardChapterRefKey))
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Moving verses                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Gathers up all nodes and maps RefKeys to nodes. */

  private class NodesAndIndices
  {
    fun getSidAndEid (refKey: RefKey) = Pair(nodeList[sidMap[refKey]!!], nodeList[eidMap[refKey]!!])
    lateinit var nodeList: List<Node>
    val sidMap: MutableMap<RefKey, Int> = HashMap(2000)
    val eidMap: MutableMap<RefKey, Int> = HashMap(2000)
    val nodeMap = IdentityHashMap<Node, Int>(10_000)
  }


  /****************************************************************************/
  /* Obtains a list of all nodes, along with maps relating verse sids and eids
     to positions in that list. */

  private fun getNodesAndIndices (startNode: Node): NodesAndIndices
  {
    val res = NodesAndIndices()

    fun addToMaps (ix: Int)
    {
      val node = res.nodeList[ix]
      res.nodeMap[node] = ix
      if (m_FileProtocol.tagName_verse() != Dom.getNodeName(node)) return
      if (m_FileProtocol.attrName_verseSid() in node)
        res.sidMap[m_FileProtocol.getSidAsRefKey(node)] = ix
      else
        res.eidMap[m_FileProtocol.getEidAsRefKey(node)] = ix
    }

    res.nodeList = Dom.getAllNodesBelow(startNode)
    res.nodeList.indices.forEach { addToMaps(it) }

    return res
  }


  /****************************************************************************/
  /* Move a list of nodes.

     At the start of the list, things are easy.  Because of the ordering of the
     overall node list, we know that we can't possibly pick up any nodes prior
     to the sid -- if the sid is the child of something below chapter level,
     we'll already have passed that 'something', and so there is no danger of it
     being included.

     At the end of the list, things are not so easy.  We will have called this
     method only because the eid is neither the sibling of the sid, nor the
     direct child of a chapter node.  It follows, therefore, that it must be the
     child of something else, and this 'something else' will come before it in
     the ordered list of nodes.  Because it comes earlier, it will definitely
     have been included in the list.  But when we clone it, we'll end up with
     its children in the list.  We need to make sure we get rid of any children
     which fall after the final eid.

     There are similar complications when removing from the source those
     things which are actually moving. */

  private fun processMovePartAVerses (sidIx: Int, eidIx: Int, moveGroup: MoveGroup, containerName: String): Node
  {
    /**************************************************************************/
    val doc = m_NodesAndIndices.nodeList[eidIx].ownerDocument



    /**************************************************************************/
    /* Returns a list comprising a node and all of its ancestors up to, but
       not including, a node named stopAtTagName.  It is ordered with the node
       first, then its parent, and so on. */

    fun getAncestorList (node: Node, stopAtTagName: String): List<Node>
    {
      val eidAncestorList = mutableListOf<Node>()
      var x = m_NodesAndIndices.nodeList[eidIx]
      while (stopAtTagName != Dom.getNodeName(x))
      {
        eidAncestorList.add(x)
        x = x.parentNode
      }

     return eidAncestorList
    }



    /**************************************************************************/
    /* Step 1. m_NodesAndIndices.nodeList.subList(sidIx, eidIx + 1) gives us
       the nodes which definitely have to feature in the Move, and it is
       convenient to mark all of them so we can identify them later. */

    val nodesDefinitelyToBeIncluded = m_NodesAndIndices.nodeList.subList(sidIx, eidIx + 1)
    nodesDefinitelyToBeIncluded.forEach { it["_X_"] = "y" } // Flag the nodes we definitely want.



    /**************************************************************************/
    /* Step 2. Because of the way in which the overall node list is ordered, if
       the final eid is below any other nodes (other than the chapter itself)
       the list will automatically include any ancestor nodes of the eid,
       along with any children of those nodes which precede the eid.  That's
       fine.  However, it's possible that the first sid is also under some
       ancestor chain, and we need to consider taking some of that as well.

       More specifically, we need to move from the sid upwards through any
       below-chapter ancestors of the sid until we hit a node which is a
       sibling of any the eid or of any of its ancestors.

       We can then include in the details to be copied that ancestor node. */

    val eidAncestorList = getAncestorList(m_NodesAndIndices.nodeList[eidIx], "chapter")
    var startNode = m_NodesAndIndices.nodeList[sidIx].parentNode
    var endNode: Node?
    while (true)
    {
      endNode = eidAncestorList.firstOrNull { Dom.isSiblingOf(startNode, it) }
      if (null != endNode) break
      startNode = startNode.parentNode
    }

    assert(null != endNode) // It always will be non-null.  This saves me having to indicate it's non-null in what follows.



    /**************************************************************************/
    /* Step 3. We can now copy everything in the range startNode to endNode to
       a container, which we will use to hold the material which is to be
       inserted into the new document. */

    val container = Dom.createNode(doc,"<$containerName/>")
    container["start"] = moveGroup.rows.first().standardRefAsRefKey.toString()
    container["end"]   = moveGroup.rows.last() .standardRefAsRefKey.toString()
    val nodesToPutInContainer = m_NodesAndIndices.nodeList.subList(m_NodesAndIndices.nodeMap[startNode]!!, m_NodesAndIndices.nodeMap[endNode]!! + 1)
    Dom.addChildren(container, Dom.cloneNodes(Dom.pruneToTopLevelOnly(nodesToPutInContainer)))



    /**************************************************************************/
    /* Step 4.  Tidy up the container.

       The container may contain nodes which come _after_ the eid because we may
       have copied an ancestor of the eid, which will have brought additional
       children with it.

       It may also contain nodes before the first sid, because we may have
       included _its_ ancestors.

       We want to retain only the nodes identified by the move itself, all of
       which have been marked with _X_ (and _only_ those will have been so
       marked).

       We can therefore prune things to the list which we need by removing
       all nodes _not_ marked _X_ -- except for nodes which are ancestors of
       sid.

       Then finally we can remove the _X_ from all nodes. */

    val sidRef = m_NodesAndIndices.nodeList[sidIx][m_FileProtocol.attrName_verseSid()]!!
    val sidNodeInContainer = container.findNodeByAttributeValue(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid(), sidRef)!!
    val sidAncestorList = getAncestorList(sidNodeInContainer, containerName).toSet()

    container.getAllNodesBelow()
      .filter { "_X_" !in it && it !in sidAncestorList }
      .forEach { try { Dom.deleteNode(it) } catch (_:Exception) {} }

    container.getAllNodesBelow().forEach { it -= "_X_"}



    /**************************************************************************/
    /* Step 5.  Change the sids and eids to use the new values. */

    val refKeyMappings = moveGroup.rows.associateBy { it.sourceRefAsRefKey }
    container.findNodesByName(m_FileProtocol.tagName_verse()).forEach {
      if (m_FileProtocol.attrName_verseEid() in it)
      {
        val sourceRefKey = m_FileProtocol.getEidAsRefKey(it)
        it[m_FileProtocol.attrName_verseEid()] = m_FileProtocol.refToString(refKeyMappings[sourceRefKey]!!.standardRefAsRefKey)
      }
      else
      {
        val sourceRefKey = m_FileProtocol.getSidAsRefKey(it)
        val row = refKeyMappings[sourceRefKey]!!
        it[m_FileProtocol.attrName_verseSid()] = m_FileProtocol.refToString(row.standardRefAsRefKey)
        PA_ReversificationUtilities.addFootnote(it, row, wordsAlreadyInSupposedlyEmptyVerse = 999) // I assume the verse will already have content, and 999 is as good a number as any to indicate this.
      }
    }



    /**************************************************************************/
    /* Step 6.  May be useful to add some comments at the start and end of the
       move block. */

    Dom.insertNodeBefore(container.firstChild, Dom.createCommentNode(doc, "+++++ $containerName Start +++++"))
    container.appendChild(Dom.createCommentNode(doc, "+++++ $containerName End +++++"))



    /**************************************************************************/
    /* Step 7.  We've got the new material sorted.  We now need to excise from
       the original source anything which has been moved to the new block.
       Fortunately this is much simpler.  We simply delete anything marked
       _X_, and if that leaves a parent empty, we remove that too. */

    nodesDefinitelyToBeIncluded.forEach {
      try { Dom.deleteNode(it) } catch (_: Exception) {}
    }



    /**************************************************************************/
    /* Phew! */

    PA_ReversificationHandler_ConversionTime.saveMoveDetails(moveGroup.rows.first().standardRef.getB(), container)
    return container
}


  /****************************************************************************/
  /* This takes a collection of nodes and ...

     - Revises the sid and eid references of each node referenced by the
       MoveGroup.

     - Removes from the collection any nodes which are children of other nodes
       in the collection.

     - Creates a container to hold these remaining nodes.

     - Removes these nodes from the source document, deleting recursively
       upwards if removing the node has left empty parents.

     - Adds the nodes to the container.

     - Saves the container for use in part B processing. */

  private fun moveVersesToContainer (nodes: List<Node>, moveGroup: MoveGroup): Node
  {
    moveGroup.rows.forEach { renumberInSitu(it) }

    val sourceNodes = Dom.pruneToTopLevelOnly(nodes)  // We don't want both children and their parents in the list.

    val container = Dom.createNode(nodes[0].ownerDocument, "<verseContainer/>")
    container["start"] = moveGroup.rows.first().standardRefAsRefKey.toString(); container["end"] = moveGroup.rows.last().standardRefAsRefKey.toString()

    sourceNodes.forEach { Dom.deleteNodeRecursivelyUpwards(it) } // Remove the source nodes from the document.
    Dom.addChildren(container, sourceNodes)

    PA_ReversificationHandler_ConversionTime.saveMoveDetails(Ref.getB(moveGroup.rows.first().standardRefAsRefKey), container)
    return container
  }


  /****************************************************************************/
  /* It is convenient to group together adjacent rows all of which involve
     Move actions, and which effectively involve moving an entire contiguous
     block of text from one place to another. */

  class MoveGroup (theRows: List<ReversificationDataRow>)
  {
    var isEntireChapter: Boolean = false
    val rows = theRows
  }
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* This handles part B of Moves.  In part A we removed verses from their
   original context and renumbered them, and then stored them pending having
   everything else sorted out, so that we knew where to put things.  Now it's
   time to reinsert the verses in their new locations. */

internal class PA_ReversificationHandler_ConversionTimePerBookMovePartB (private val dataCollection: X_DataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun processRootNode (rootNode: Node, containers: List<Node>)
  {
    /**************************************************************************/
    m_RootNode = rootNode



    /**************************************************************************/
    containers.forEach {
      val insertionPoint = if ("Chapter" in Dom.getNodeName(it))
        m_ChapterSidMap.ceilingEntry(it["end"]!!.toLong()).value
      else
        m_VerseSidMap.ceilingEntry(it["end"]!!.toLong()).value

        Dom.insertNodesBefore(insertionPoint, Dom.cloneNodes(rootNode.ownerDocument, Dom.getChildren(it), deep = true))  // Clone to target doc, because with cross-boundary moves, the container may not be in the right document.
        Dom.deleteNode(it) // Don't need the container any more.
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private lateinit var m_RootNode: Node
  private val m_ChapterSidMap: NavigableMap<RefKey, Node> by lazy { PA_ReversificationUtilities.makeChapterSidMap(m_RootNode) }
  private val m_VerseSidMap: NavigableMap<RefKey, Node> by lazy { PA_ReversificationUtilities.makeVerseSidMap(m_RootNode) }
}
