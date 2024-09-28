/******************************************************************************/
package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleAnatomy
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleStructure
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory


/******************************************************************************/
/**
 * Performs conversion-time reversification.  This entails making potentially
 * significant changes to the text so as to force it to conform to the NRSVA
 * structure.
 *
 * <span class='important'>At the time of writing, we are not anticipating that
 * this form of reversification will actually be used, since on copyright texts
 * the licence conditions almost certainly preclude the kinds of changes we may
 * need to make.  The code here mostly dates back a long way and has not been
 * used recently.  The chances of it actually working, therefore, are probably
 * remote.</span>
 *
 *
 *
 *
 *
 * ## Functionality
 *
 * The processing here may create 'empty' verses to fill in any holes in the
 * versification scheme where the reversification data requires it.  It may
 * renumber verses in situ, reorder them, move them to different locations
 * within the chapter, to different chapters, or to different books (in some
 * cases even books which do not exist in the source text).  And it may add
 * footnotes to give more information about the verse structure.
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
  /* General notes:

      The assessment of whether a row applies or not is determined statically
      -- ie we look at the text as it stands before any of the processing here
      is applied, rather than checking it incrementally as changes are applied.

      The main complication here are those rows which entail moving stuff
      elsewhere -- you cannot simply go ahead and action them as you encounter
      them, because it may be that they are targetting verses which already
      exist, but which are, themselves, going to be moved in due course, and
      you don't want to overwrite them before you've had a chance to move them.
   */

  /****************************************************************************/
  override fun process (dataCollection: X_DataCollection)
  {
    TODO("HERE BE DRAGONS!  Conversion-time reversification: This code hasn't been used for ages, and we may well no longer want it anyway.  I have retained it just in case, but it is so far out of data that it will almost certainly require significant rework.")
    super.process(dataCollection)
    aggregateData()
    doIt()
    markFootnoteTargets()
    val callout = MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!).get()
    addFootnotes("Reversification Note"){ _ -> callout }
    m_DataCollection.reloadBibleStructureFromRootNodes(false) // We've almost certainly invalidated the structure information by now.
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Data aggregation                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Carries out the various forms of data aggregation required by this
     flavour of reversification.  This is in addition to that already carried
     out by PA_ReversificationHandler. */

  private fun aggregateData ()
  {
    aggregateBooks()
    aggregateBooksInvolvedInMoveActions()
    aggregateMoveGroups()
    markSpecialistMoves()
    recordBookMappings()
  }


  /****************************************************************************/
  /* Generates lists containing:

     a) All books mentioned in the selected reversification rows.
     b) All source books.
     c) All standard books.
     Etc.
  */

  private fun aggregateBooks ()
  {
    val allBooks:     MutableSet<Int> = HashSet()
    val bookMappings: MutableSet<String> = HashSet()
    val sourceBooks:  MutableSet<Int> = HashSet()
    val targetBooks:  MutableSet<Int> = HashSet()

    getSelectedRows().forEach {
      allBooks.add(it.sourceRef.getB())
      allBooks.add(it.standardRef.getB())
      sourceBooks.add(it.sourceRef.getB())
      targetBooks.add(it.standardRef.getB())
      bookMappings.add(BibleBookNamesUsx.numberToAbbreviatedName(it.sourceRef.getB()) + "." + BibleBookNamesUsx.numberToAbbreviatedName(it.standardRef.getB()))
    }

    m_AllBooks      = allBooks   .sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
    m_SourceBooks   = sourceBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
    m_StandardBooks = targetBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
  }


  /****************************************************************************/
  /* Generates lists containing:

     a) All books involved in Move actions.
     b) All source books.
     c) All standard books.
  */

  private fun aggregateBooksInvolvedInMoveActions ()
  {
    val allBooks:    MutableSet<Int> = HashSet()
    val sourceBooks: MutableSet<Int> = HashSet()
    val targetBooks: MutableSet<Int> = HashSet()

    getSelectedRows().filter { it.action.isMove }
      .forEach { allBooks.add(it.sourceRef.getB()); allBooks.add(it.standardRef.getB()); sourceBooks.add(it.sourceRef.getB()); targetBooks.add(it.standardRef.getB()) }

    m_AllBooksInvolvedInMoveActions      = allBooks   .sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
    m_SourceBooksInvolvedInMoveActions   = sourceBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
    m_StandardBooksInvolvedInMoveActions = targetBooks.sorted().map { BibleBookNamesUsx.numberToAbbreviatedName(it) }
  }


  /****************************************************************************/
  /* Collects together consecutive Move rows which can be actioned as a block.

     Note that where dealing with subverses, groups do not extend across the
     owning verse boundary.

     The aim here is to identify groups of consecutive rows where ...

     * All the source elements are verses, or all are subverses.

     * All of the standard elements are verses, or all are subverses.

     * The source elements increment by one verse if they are verses, or by one
       subverse if they are subverses.

     * Ditto for standard elements.

     The reason for this rather fiddly method is that when processing Moves, it
     is desirable to move entire blocks if possible, rather than move one verse
     at a time.  This way, cross-verse-boundary markup is less of an issue --
     cross-boundary markup would be an issue only if it runs across the boundary
     of the entire block, rather than being an issue for each individual verse.
  */

  private fun aggregateMoveGroups ()
  {
    /**************************************************************************/
    val rows = getSelectedRows().filter { it.action.isMove }
    if (rows.isEmpty()) return



    /**************************************************************************/
    val sortedRows = rows.sortedWith(::cfForMoveGroups)
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

      m_MoveGroups.add(ReversificationMoveGroup(sortedRows.subList(ixLow, ixHigh)))
      ixLow = ixHigh
    }
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
  /* Marks groups which involve an entire chapter, or which target a different
     book. */

  private fun markSpecialistMoves ()
  {
    fun process (grp: ReversificationMoveGroup)
    {
      if (grp.rows.first().sourceRef.hasS()) return
      if (grp.rows.first().standardRef.hasS()) return

      if (1 != grp.rows.first().sourceRef.getV()) return
      if (1 != grp.rows.first().standardRef.getV()) return

      if (m_BibleStructure!!.getLastVerseNo(grp.rows.first().sourceRef) != grp.rows.last().sourceRef.getV()) return
      if (BibleStructure.makeOsis2modNrsvxSchemeInstance(m_DataCollection.getBibleStructure()).getLastVerseNo(grp.rows.first().standardRef) != grp.rows.last().standardRef.getV()) return

      grp.isEntireChapter = true
    }

    m_MoveGroups.forEach { process(it) }
    m_MoveGroups.forEach { it.crossBook = it.rows.first().sourceRef.getB() != it.rows.first().standardRef.getB() }
  }


  /****************************************************************************/
  /* Generates a set containing source/standard pairs detailing which source
     books map to which standard books. */

  private fun recordBookMappings ()
  {
    fun addMapping (row: ReversificationDataRow)
    {
      val sourceBook   = BibleBookNamesUsx.numberToAbbreviatedName(row.sourceRef  .getB())
      val standardBook = BibleBookNamesUsx.numberToAbbreviatedName(row.standardRef.getB())
      m_BookMappings.add("$sourceBook.$standardBook")
    }

    getSelectedRows().forEach { addMapping(it) }
  }


  /****************************************************************************/
  /* It is convenient to group together adjacent rows all of which involve
     Move actions, and which effectively involve moving an entire contiguous
     block of text from one place to another. */

  class ReversificationMoveGroup (theRows: List<ReversificationDataRow>)
  {
    fun getSourceBookAbbreviatedName (): String { return BibleBookNamesUsx.numberToAbbreviatedName(rows[0].sourceRef.getB()) }
    fun getStandardBookAbbreviatedName () = BibleBookNamesUsx.numberToAbbreviatedName(rows[0].standardRef.getB())

    fun getSourceChapterRefAsString   () = rows.first().sourceRef  .toString("bc")
    fun getStandardChapterRefAsString () = rows.first().standardRef.toString("bc")

    var crossBook = false
    var isEntireChapter: Boolean = false
    val rows = theRows
    val sourceRange = RefRange(theRows.first().sourceRef, theRows.last().sourceRef)
    val standardRange = RefRange(theRows.first().standardRef, theRows.last().standardRef)
  }


  /****************************************************************************/
  fun getBookMappings (): Set<String> { return m_BookMappings } // String of the form abc.xyz, indicating there is a mapping from book abc to xyz.
  private fun getAbbreviatedNamesOfAllBooksSubjectToReversificationProcessing (): List<String> { return m_AllBooks } // All book names subject to reversification processing.
  fun getAllMoveGroups (): List<ReversificationMoveGroup> { return m_MoveGroups }
  fun getMoveGroupsWithBookAsSource (bookNo: Int): List<ReversificationMoveGroup> = m_MoveGroups.filter { bookNo == it.sourceRange.getLowAsRef().getB() }
  fun getNonMoveRowsWithBookAsStandard (bookNo: Int): List<ReversificationDataRow> = getSelectedRows().filter { it.isMove && bookNo == it.standardRef.getB() }
  private fun getSourceBooksInvolvedInReversificationMoveActionsAbbreviatedNames (): List<String> { return m_SourceBooksInvolvedInMoveActions }
  private fun getStandardBooksInvolvedInReversificationMoveActionsAbbreviatedNames (): List<String> { return m_StandardBooksInvolvedInMoveActions }


  /****************************************************************************/
  private val m_MoveGroups: MutableList<ReversificationMoveGroup> = ArrayList()

  private var m_BookMappings: MutableSet<String> = HashSet()
  private lateinit var m_AllBooks: List<String>
  private lateinit var m_SourceBooks: List<String>
  private lateinit var m_StandardBooks: List<String>

  private lateinit var m_AllBooksInvolvedInMoveActions: List<String>
  private lateinit var m_SourceBooksInvolvedInMoveActions: List<String>
  private lateinit var m_StandardBooksInvolvedInMoveActions: List<String>





  /****************************************************************************/
  /* This may seem to duplicate the Z_DataCollection facilities to some extent.
     The code here was written before I came up with the idea for
     Z_DataCollection, and I didn't want to disturb the code here too much.

     A pretty high proportion of the reversification actions are quite
     straightforward to handle.  With the exception of moves and actions
     involving canonical titles, all we are doing is _possibly_ adding
     a footnote to a verse, _possibly_ creating a verse if it does not already
     exist, and _possibly_ complaining if required to do so.

     Moves I handle by running over the _source_ books and moving the verses
     out into a store to be used later.  This store (which contains the
     verses in full post-reversification form, ie with annotation etc) I hold
     in the BookDetails for the target book, and then pick up right at the end
     of the processing.

     Why do this?  The problem otherwise is duplication.  We might have v3
     already in existence in a target book and be moving something else to
     become v3.  Of course this _could_ represent a problem with the
     reversification data, because clearly we'd be losing data if we stored
     a new v3 over the top of the old one.  But more likely there is some
     other reversification action which is going to renumber the old v3,
     thus leaving a hole to be occupied by the move target.  From a logical
     perspective, this is fine.  From an implementational perspective, it
     would be complicated -- but the approach I have adopted avoids the
     complication, because the new verses are not placed into their new
     location until all other versification actions for the target book are
     complete.

     There are some potential complicating factors.  For example, moves
     may be complicated if markup runs across verse boundaries in the
     source book.  I do what I can to avoid this upstream of reversification.
     If that doesn't work, though, the situation may well not be amenable to
     any very neat solution.

     And one other thing to be taken into account is the fact that in some cases
     reversification may move material into entirely new books.  To give
     uniformity later, I check for this up-front and create the books in empty
     form if necessary before I do anything else.
  */

  private fun doIt ()
  {
    initialise()
    getSelectedRows().filter { it.sourceIsPsalmTitle } .forEach(::processCanonicalTitles_turnSourceIntoVerseZero)

    getSourceBooksInvolvedInReversificationMoveActionsAbbreviatedNames().map { BibleBookNamesUsx.abbreviatedNameToNumber(it) }.forEach { processMovePart1(it) }
    getAbbreviatedNamesOfAllBooksSubjectToReversificationProcessing().map { BibleBookNamesUsx.abbreviatedNameToNumber(it) }.forEach { processNonMove(it, "renumber") }
    getStandardBooksInvolvedInReversificationMoveActionsAbbreviatedNames().map { BibleBookNamesUsx.abbreviatedNameToNumber(it) }.forEach { processMovePart2(it) }
    getAbbreviatedNamesOfAllBooksSubjectToReversificationProcessing().map { BibleBookNamesUsx.abbreviatedNameToNumber(it) }.forEach { processNonMove(it, "") }
    getAbbreviatedNamesOfAllBooksSubjectToReversificationProcessing().map { BibleBookNamesUsx.abbreviatedNameToNumber(it) }.forEach { terminate(it) }

    processCanonicalTitles_turnVerseZerosIntoCanonicalTitle()
    m_BookDetails.clear() // Free up the memory used in this processing.
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                     Initialisation and termination                     **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Basic initialisation -- not associated with any single book. */

  private fun initialise ()
  {
    /**************************************************************************/
    /* Record the fact that we're applying conversion time reversification. */

    IssueAndInformationRecorder.setConversionTimeReversification()



    /**************************************************************************/
    /* Some reversification statements may move verses to different books.  In
       some cases, we expect these books already to exist; in others, we expect
       them _not_ to exist.  Check that these expectations are met. */

    checkExistenceCriteriaForCrossBookMappings(getBookMappings())



    /**************************************************************************/
    /* Create the basic information we're going to need for each book which is
       subject to reversification. */

    getAbbreviatedNamesOfAllBooksSubjectToReversificationProcessing()
      .map { BibleBookNamesUsx.abbreviatedNameToNumber(it) } // USX really _is_ intended here -- the reversification data operates with USX names.
      .forEach { BookDetails(it) } // Create BookDetails entry and carry out any pre-processing.
  }


  /****************************************************************************/
  /* Tidies up a single book. */

  private fun terminate (bookNo: Int)
  {
    /**************************************************************************/
    val bookDetails = m_BookDetails[bookNo]!!
    deleteEmptyChapters(bookDetails.m_RootNode)



    /**************************************************************************/
    /* Particularly with LXX-based texts, we may have book LJE, which may be
       left empty by reversification, because all of its content is moved to
       BAR.  I allow here that this may be true of other books too.  If a book
       no longer contains any chapters, then the book should be excluded from
       further processing. */

    if (null == Dom.findNodeByName(bookDetails.m_RootNode, m_FileProtocol.tagName_chapter(), false))
    {
      m_BookDetails.remove(bookNo)
      m_DataCollection.removeBook(bookNo)
      return
    }



    /**************************************************************************/
    /* This is basically a backstop introduced to tidy up empty verses where
       I've got the processing wrong.  I don't know what happened to make this
       necessary, nor whether it is still needed, but I don't think there's any
       harm in retaining it, and possibly some benefit. */

    deleteVersesWhichWereEmptyInRawTextButWhichHaveBeenOverwritten(bookDetails.m_RootNode)



    /**************************************************************************/
    /* Reversification may have renumbered verses which were the target of
       cross-references, and we may want to update the cross-references in the
       light of this. */

    doCrossReferenceMappings(bookDetails.m_RootNode)
  }


  /****************************************************************************/
  /* If we have cross-book moves, there are some books which we target which
     _must_ already exist, and some which must _not_. */

  private fun checkExistenceCriteriaForCrossBookMappings (crossBookMappings: Set<String>)
  {
    fun dontWantTarget (from: String, to: String, message: String)
    {
      if (!crossBookMappings.contains("$from.$to")) return
      if (m_BibleStructure!!.bookExists(to)) throw StepExceptionWithStackTraceAbandonRun(message)
    }

    fun doWantTarget (from: String, to: String, message: String)
    {
      if (!crossBookMappings.contains("$from.$to")) return
      if (!m_BibleStructure!!.bookExists(to)) throw StepExceptionWithStackTraceAbandonRun(message)
    }

    dontWantTarget("dan", "bel", "Need to move text from Dan to Bel, but Bel already exists")
    dontWantTarget("dan", "s3y", "Need to move text from Dan to S3Y, but S3Y already exists")
    dontWantTarget("dan", "sus", "Need to move text from Dan to Sus, but Sus already exists")
    doWantTarget  ("lje", "bar", "Need to move text from LJE to Bar, but Bar does not exist")
    dontWantTarget("2ch", "man", "Need to move text from 2Ch to Man, but Man already exists")
    dontWantTarget("psa", "ps2", "Need to move text from Psa to Ps2, but Ps2 already exists")
  }


  /****************************************************************************/
  /* When I move stuff, I delete the nodes being moved, and I work recursively
     up the tree deleting any ancestor node which has now lost all canonical
     content.  Unfortunately where the effect of this _should_ be to remove a
     chapter entirely because all of its contents have been moved, this
     doesn't actually work, because presently all chapters contain a dummy
     verse to act as backstop.  If, therefore we have a chapter whose only
     contained verse is the backstop, I need to remove the chapter.

     Note that possibly this might be better done later in the processing, just
     in case any empty chapters are lying around as a result of other
     processing -- TBD. */

  private fun deleteEmptyChapters (rootNode: Node)
  {
    fun deleteIfEmpty (chapterNode: Node)
    {
      val firstVerse = Dom.findNodeByName(chapterNode, m_FileProtocol.tagName_verse(), false)
      if (null == firstVerse || RefBase.C_BackstopVerseNumber == m_FileProtocol.readRef(firstVerse[m_FileProtocol.attrName_verseSid()]!!).getV())
        Dom.deleteNode(chapterNode)
    }

    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach { deleteIfEmpty(it) }
  }


  /****************************************************************************/
  /* In at least one text I have seen verses which had been left empty in the
     raw text, but which had been overwritten by reversification.  Except that
     they hadn't exactly been overwritten -- we had both the original empty
     verse and the one created by reversification.

     I probably ought to kill off the original empty verse at the time I create
     the new one, but since I have a feeling I may create new verses in several
     different places in the code, it's probably better to gather everything
     together here.
   */

  private fun deleteVersesWhichWereEmptyInRawTextButWhichHaveBeenOverwritten (rootNode: Node)
  {
    /**************************************************************************/
    data class Thunk (var sidIx: Int, var eidIx: Int, var empty: Boolean)



    /**************************************************************************/
    /* Identify duplicate sids. */

    val allNodes = Dom.getAllNodesBelow(rootNode)
    val duplicateDetails: TreeMap<String, MutableList<Thunk>> = TreeMap(String.CASE_INSENSITIVE_ORDER)
    var activeList: MutableList<Thunk>? = null
    fun collectDuplicates (ix: Int)
    {
      val node = allNodes[ix]

      if (null != activeList &&                                            // We're within a verse.
          Dom.isTextNode(node) && !Dom.isWhitespace((node)) &&             // This is a non-blank text node.
          !m_FileProtocol.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(node))  // It's canonical.
        activeList!!.last().empty = false                                  // Flag the fact that this verse contains canonical text.

      else if (m_FileProtocol.tagName_verse() == Dom.getNodeName(node))
      {
        if (Dom.hasAttribute(node, m_FileProtocol.attrName_verseEid()))
        {
          activeList!!.last().eidIx = ix  // Record the index for this node.
          activeList = null               // Flag the fact that we're no longer within a verse.
        }
        else // sid
        {
          val sid = Dom.getAttribute(node, m_FileProtocol.attrName_verseSid())!!
          activeList = duplicateDetails[sid]
          if (null == activeList)
          {
            activeList = mutableListOf()
            duplicateDetails[sid] = activeList!!
          }

          activeList!!.add(Thunk(ix, -1, true))
        }
      }
    }

    allNodes.indices.forEach { collectDuplicates(it) }



    /**************************************************************************/
    /* Check entries and delete blank entries where we have a blank one for the
       same verse reference.  We should never have more than two thunks in
       a list, and where we have two, one should be empty and one not. */

    fun deleteEmpty (thunk: Thunk)
    {
      for (ix in thunk.sidIx .. thunk.eidIx)
        Dom.deleteNode(allNodes[ix])
    }

    fun validateAndDeleteEmpty (sid: String, thunks: List<Thunk>)
    {
      if (1 == thunks.size)
        return

      if (thunks.size > 2)
      {
        Logger.error("More than two verses with sid $sid")
        return
      }

      if (thunks[0].empty == thunks[1].empty)
      {
        Logger.error("Two empty verses or two non-empty verses with sid $sid")
        return
      }

      if (thunks[0].empty)
        deleteEmpty(thunks[0])
      else
        deleteEmpty(thunks[1])
    }

    duplicateDetails.forEach { validateAndDeleteEmpty(it.key, it.value) }
  }


  /****************************************************************************/
  /* If the document contains any cross-references, we need to update them if
     renumbering has invalidated them. */

  private fun doCrossReferenceMappings (rootNode: Node)
  {
    //val mappings = ReversificationData.getReferenceMappings() as MutableMap<RefKey, RefKey>
    //if (C_CollapseSubverses) mappings.keys.forEach { if (Ref.hasS(mappings[it]!!)) mappings[it] = Ref.clearS(mappings[it]!!) }
    //m_CrossReferenceChecker.process(rootNode, mappings)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Processing                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                  Move                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* I guess this is probably the most complicated action.  Let's get a few
     preliminaries out of the way while I remember ...

     - I did at one stage wonder what to do if we had headings which 'belonged'
       with certain verses (assuming a heading _can_ belong to a verse -- I
       suspect they belong to a number of consecutive verses rather than a
       single verse).  To move the heading, or not to move it?  This was
       resolved in a discussion with DIB on 17-Mar-2023.  He says not to do
       anything -- in his opinion verses which are being moved are unlikely
       to have headers anyway.  More specifically, if I am moving a verse
       or a block of consecutive verses and they happen to have a heading
       immediately before them, I shall leave it where it is.  If I am
       moving a block which happens to contain an embedded heading, then
       it will go with the block, because it's easier to do that.

     - I have always been plagued by doubts about any special handling for
       psalm titles.  In this latest implementation, there is no need to
       regard them as special for the purposes of move processing.  Earlier
       processing will have converted them to pseudo verses (verse zero, in
       fact), and in this guise they behave the same way as any other verse.

     - Sometimes we are turning verses or subverses in the original into
       subverses into the target book.  Subverses are somewhat of a
       complication.  I _believe_ that Crosswire osis22mod cannot cope with them,
       that therefore they should be collapsed where that is being used.  But I
       believe that our own version of osis2mod _can_ cope, and so when using
       that they should be retained.  I defer the decision to later here:
       reversifiction will leave things as subverses, and something further
       down the food chain will decide whether they should be collapsed.


     The main complication with move processing is the fact that we may be
     targeting existing verses.  For example, we may be moving v3 to v23,
     but v23 may already exist.  Of course if that situation persisted, we'd
     have a problem, because we'd be losing information.  But normally there
     would be another reversification row which would get v23 out of the way,
     in which case we just need to try to make sure that it has had a chance to
     do that before we overwrite it.

     There is a further issue here, though.  At one stage, we were contemplating
     both moving the verses to their new location _and_ leaving them in their
     original location as well.  The idea was that this would let us reversify
     the text whilst at the same time camouflaging to some extent the fact that
     we had done so, thus making the text more acceptable to people who were
     familiar with its canonical form.  In fact, this was never really destined
     to be entirely successful, and since the introduction of our own version
     of osis2mod is unnecessary.  However, it did influence the processing (and
     significantly complicated it), and I haven't felt able to revert to the
     earlier slightly simpler processing.  You can at least console yourself
     with the thought that it was only _slightly_ simpler ...

     The approach I originally adopted here (ie before this idea of retaining some
     source verses in situ) was to run over all of the books which act
     as _sources_ for moves, shifting to a temporary storage area the data to be
     moved (and in so doing, deleting it from the source document).  I then did
     all of the non-move-related processing on each book; and then finally
     picked up any move data from the temporary storage areas.

     This approach also had the benefit that the renumbering involved in moves
     can be handled in exactly the same way as RenumberInSitu -- ie I can use
     common code.

     The revised approach is very similar, except that I don't always delete
     the source verses.  I'm just hoping that their continued existence does
     not break anything which was working previously.

     Which all makes it sound a piece of cake.  Unfortunately if that's what it
     is, it's probably still a cake of _soap_ -- ie distinctly unpalatable.

     There may be a few occasions upon which we are moving entire chapters, and
     these are, indeed, easy, because I assume that markup will never cross a
     chapter boundary, and I can therefore simply take the entire chapter in
     one go.

     And I suppose equally there mat be a few occasions where the sid of the
     first verse we are moving and the eid of the last one are siblings, so
     that again we can simply pick things up without consideration of cross-
     boundary markup.  However, these are special cases of the more general
     situation where, for all of my attempts earlier in the processing to
     avoid cross-boundary markup, we do at least need to consider its
     implications.

     We need, therefore, to consider the data available to us.  In particular,
     we get a list of nodes, ordered such that the parent of a given group
     appears before its children.

     Let's think in terms of moving _groups_ of consecutive verses as being
     the more general case.  If we're moving a single verse, that's just a
     group of one, and indeed because verse tags are milestones, we're still
     dealing effectively with a block of contiguous nodes.

     Now because of the manner in which things are ordered, we can be confident
     that things which appear after the sid of the first verse do genuinely
     belong to the block.  This would not be the case if we were to include
     the parent of that sid, because that might bring with it children which
     precede the sid.  But since we run through the list of nodes and start
     accumulating only when we hit the sid, we will already have passed its
     parent by then.

     So far so easy.  Unfortunately the conditions at the other end are
     appreciably more difficult.

     We _may_ be lucky, in that the eid may be a direct child of the chapter, in
     which case I think we're ok because the chapter isn't going to start within
     the span of the block we're looking at, so I can simply take everything I
     encounter in the ordered list from sid of first verse to eid of last
     inclusive.

     But then again, we may _not_ be lucky -- the eid reside under some other
     node, and that node may have children outside of the block.  And since
     we the ordering means we will have encountered the parent before we hit
     the sid, the parent will have brought with it all of its children; but
     we don't want those which fall outside of the eid.

     To address this, I being by checking whether the final eid is indeed a
     child of something below the chapter node.  If it is, I create two
     copies of itself.  One contains all of the children prior to the eid, and
     one all of the children after it.  I then replace the original parent with
     new-pre-parent ... eid ... new-post-parent.  If this still leaves the eid
     as not the direct child of the chapter, I repeat the process.  And so on.

     Having established the list of nodes of potential interest, I now convert
     this to a list of top-level nodes only (ie I remove from my original list
     any nodes which have parents in the node).  The result is the list of nodes
     to be deleted from the source document and (in cloned form) to be inserted
     into the target document.  (Or perhaps not in cloned form: move processing
     doesn't always target a new document: sometimes -- indeed in most cases --
     the same document is acting both as source and target.)

     The nodes are deleted from the source document recursively upwards, in the
     sense that if a node which was previously a parent of one or more of these
     nodes is now left without children, the parent is deleted ... and so on.
  */

  /****************************************************************************/
  private fun processMovePart1 (bookNo: Int)
  {
    val bookThunk = m_BookDetails[bookNo]!!
    val structure = getNodesAndIndices(bookThunk.m_RootNode)
    getMoveGroupsWithBookAsSource(bookNo).forEach { processMovePart1(it, bookThunk, structure) }
  }


  /****************************************************************************/
  /* Right ... time for a little careful consideration.

     A move group may consist of a single move action, or of a number of related
     ones, all from the same chapter in the original, and all aimed at the
     same chapter in the target.  Indeed, it may entail moving an entire
     chapter -- something which the group header will tell us.

     If it's an entire chapter, things are easy, because I assume that there
     is no issue over cross-boundary markup -- all I have to do is pick up
     the entire chapter, update the sids and eids in the verses and the
     chapter itself, and then prepare to bung it elsewhere.
   */

  private fun processMovePart1 (moveGroup: ReversificationMoveGroup, sourceBookDetails: BookDetails, structure: NodesAndIndices)
  {
    /**************************************************************************/
    //Dbg.d(moveGroup.rows)
    //Dbg.d(1648 == moveGroup.rows[0].rowNumber)



    /**************************************************************************/
    val sidIx = structure.sidMap[moveGroup.rows.first().sourceRefAsRefKey]!!
    val eidIx = structure.eidMap[moveGroup.rows.last().sourceRefAsRefKey]!!
    val sidNode = structure.nodeList[sidIx]
    val eidNode = structure.nodeList[eidIx]

    if (sidNode.isSiblingOf(eidNode) || m_FileProtocol.tagName_chapter() == Dom.getNodeName(eidNode.parentNode))
      movePart1BlockEasy(structure.nodeList.subList(sidIx, eidIx + 1), moveGroup, sourceBookDetails)
    else
      movePart1BlockDifficult(structure, sidIx, eidIx, moveGroup, sourceBookDetails)
 }


  /****************************************************************************/
  /* This is the easier of the two possibilities.  I'm not 100% sure it's
     actually worth splitting out, or whether I might as well include it within
     the 'difficult' processing, but anyway.  This present method covers two
     cases :-

     - The case where sid and eid are siblings.

     - The case where the eid is an immediate child of the chapter.

     These are easy because there is no possibility of us encountering a parent
     of the eid while running from sid to eid, and therefore no need to worry
     that there may be any nodes belonging to that parent which fall after the
     eid and therefore need to be excluded. */

  private fun movePart1BlockEasy (nodes: List<Node>, moveGroup: ReversificationMoveGroup, sourceBookDetails: BookDetails)
  {
    val sourceNodes = Dom.pruneToTopLevelOnly(nodes)                              // We don't want both children and their parents in the list.
    sourceNodes.forEach { Dom.deleteNodeRecursivelyUpwards(it) }

    val container = makeMoveContainer(sourceNodes, sourceBookDetails, moveGroup)  // It's convenient to have everything under a single node temporarily.
    processSimpleRows(container, moveGroup.rows, "renumber")               // Do the processing needed to revise the sids and eids.
    //Dom.printTree(container)
  }


  /****************************************************************************/
  /* This is the difficult situation ...

     At the start, things are easy.  Because of the ordering, we know that we
     can't possibly pick up any nodes prior to the sid -- if the sid is the
     child of something below chapter level, we'll already have passed that
     'something', and so there is no danger of it being included.

     At the end, things are not so easy.  We will have called this method only
     because the eid is neither the sibling of the sid, nor the direct child of
     a chapter node.  It follows, therefore, that it must be the child of
     something else, and if we work through the node list sequentially from
     sid to eid, we _would_ hit that parent node -- and if we included it in
     the list of things to be processed, we'd end up with that node and all
     its children, perhaps including things after the eid.

     And this also works up the tree -- possibly the parent of the eid is
     itself the child of something other than the chapter, and so we have
     similar issues.

     There is a further complication here, in that although I'm going to
     be applying changes to the source structure, I don't want those
     changes to invalidate the structural information I have already
     acquired, since collecting it is computationally expensive.

     Here's the approach, then ...

     1. To the parent of the eid I add an attribute giving the ordinal
        number of the eid within the child list of the parent.  And I
        repeat this working all the way up the chain.  I also keep a
        list of the nodes to which I have done this for later use.

     2. I clone the whole lot, temporarily ignoring the possibility that
        this may give me some nodes I don't want.

     3. From this cloned collection, I work back up the eid tree, deleting
        nodes which come after the eid or its ancestors.

     4. I reduce the list to top level only.

     5. I apply the renumbering changes to the list.

     *** This completes work on the data for the target. ***

     6. For step 2, we had an over-extended list of nodes.  However, we
        can safely delete from this list anything which is not a parent
        of the final eid, and we know which things they are because we
        added _childPos to them.

     7. And then finally, we can delete from the ancestor chain any
        nodes which will already have appeared within the list of
        those being moved.
   */

  private fun movePart1BlockDifficult (sourceStructure: NodesAndIndices, sidIx: Int, eidIx: Int, moveGroup: ReversificationMoveGroup, sourceBookDetails: BookDetails)
  {
    /**************************************************************************/
    /* Step 1 above.  Flag ancestors under which the eid sits. */

    var node = sourceStructure.nodeList[eidIx]
    while (true)
    {
      val parent = node.parentNode
      if ("chapter" == Dom.getNodeName(parent)) break
      NodeMarker.setReversificationChildPos(parent, Dom.getChildNumber(node).toString())
      node = parent
    }



    /**************************************************************************/
    /* Step 2.  Clone everything for use in the target document. */

    val overExtendedSourceList = sourceStructure.nodeList.subList(sidIx, eidIx + 1)
    val container = makeMoveContainer(overExtendedSourceList, sourceBookDetails, moveGroup)



    /**************************************************************************/
    /* Step 3.  Delete the younger siblings of any ancestors of eid. */

    fun deleteYoungerSiblings (node: Node): Boolean
    {
      val parent = node.parentNode
      if (!NodeMarker.hasReversificationChildPos(parent)) return false
      val ix = NodeMarker.getReversificationChildPos(parent)!!.toInt()
      NodeMarker.deleteReversificationChildPos(parent)
      val siblings = Dom.getChildren(node.parentNode)
      siblings.subList(ix + 1, siblings.size).forEach { Dom.deleteNode(it) } // No need to delete recursively upwards, because the parent is guaranteed still to have at least one child.
      return true
    }

    node = Dom.findNodesByAttributeName(container, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid()).last()
    while (deleteYoungerSiblings(node))
      node = node.parentNode



    /**************************************************************************/
    /* Step 4.  Reduce the list of nodes to top level ones only. */

    let {
      val prunedChildren = Dom.pruneToTopLevelOnly(Dom.getChildren(container))
      Dom.deleteChildren(container)
      Dom.addChildren(container, prunedChildren)
    }



    /**************************************************************************/
    /* Step 5.  Apply the renumbering in preparation for use later. */

    processSimpleRows(container, moveGroup.rows, "renumber")



    /**************************************************************************/
    /* End of work on data for use in target.  Now deal with source deletion. */
    /**************************************************************************/



    /**************************************************************************/
    /* Step 6.  Take over original over-extended list, and remove anything
       which is not a parent of the final eid (ie anything not flagged with
       _childPos. */

    overExtendedSourceList.filter { !NodeMarker.hasReversificationChildPos(it) } .forEach { Dom.deleteNodeRecursivelyUpwards(it) }



    /**************************************************************************/
    /* Step 7. Delete the younger siblings of the eid node, along with the eid
       node itself. */

    fun deleteOlderSiblings (node: Node): Boolean
    {
      val parent = node.parentNode
      if (!NodeMarker.hasReversificationChildPos(parent)) return false
      val ix = NodeMarker.getReversificationChildPos(parent)!!.toInt()
      NodeMarker.deleteReversificationChildPos(parent)
      val siblings = Dom.getChildren(node.parentNode)
      if (!Dom.hasNonWhitespaceChildren(siblings[ix])) Dom.deleteNode(siblings[ix])
      siblings.subList(0, ix).forEach { Dom.deleteNode(it) }
      Dom.deleteNodeRecursivelyUpwards(parent)
      return true
    }

    node = sourceStructure.nodeList[eidIx]
    while (deleteOlderSiblings(node))
      node = node.parentNode



    /**************************************************************************/
    /* Whew! */
}


  /****************************************************************************/
  // !!! Why isn't this called any more ???

  private fun movePart1EntireChapter (moveGroup: ReversificationMoveGroup, bookDetails: BookDetails)
  {
    /**************************************************************************/
    /* Find the chapter node in the source document and remove it. */

    val chapterNode = Dom.findNodeByAttributeValue(bookDetails.m_RootNode, m_FileProtocol.tagName_chapter(), m_FileProtocol.attrName_chapterSid(), moveGroup.getSourceChapterRefAsString())!!
    Dom.deleteNode(chapterNode)



    /**************************************************************************/
    /* Change the sid of the chapter and then update all of the contained nodes
       to have their new sids / eids. */

    changeId(chapterNode, m_FileProtocol.attrName_chapterSid(), moveGroup.getStandardChapterRefAsString())
    processSimpleRows(chapterNode, moveGroup.rows, "renumber")



    /**************************************************************************/
    /* Create the structure which is used in part 2 processing, and store it in
       the correct place. */

    makeMoveContainer(chapterNode, bookDetails, moveGroup)
  }


  /****************************************************************************/
  private fun processMovePart2 (bookNo: Int)
  {
    val bookDetails = m_BookDetails[bookNo]!!

    fun doIt (container: Node)
    {
      val lastEid = m_FileProtocol.readRef(NodeMarker.getReversificationLastEid(container)!!)
      val insertionPoint = getInsertionPoint(bookDetails.m_RootNode, lastEid)
      Dom.insertNodesBefore(insertionPoint, Dom.getChildren(container))
    }


    bookDetails.m_MoveDataForInsertionIntoTarget.forEach { doIt(it) }
  }


  /****************************************************************************/
  /* When moving things, it is convenient to bung everything under a temporary
     node, which we then get rid of again later. */

  private fun makeMoveContainer (node: Node, sourceBookDetails: BookDetails, moveGroup: ReversificationMoveGroup): Node
  {
    return makeMoveContainer(listOf(node), sourceBookDetails, moveGroup)
  }


  /****************************************************************************/
  /* When moving things, it is convenient to bung everything under a temporary
     node, which we then get rid of again later. */

  private fun makeMoveContainer (nodes: List<Node>, sourceBookDetails: BookDetails, moveGroup: ReversificationMoveGroup): Node
  {
    val targetBookDetails = if (moveGroup.crossBook) m_BookDetails[BibleBookNamesUsx.nameToNumber(moveGroup.getStandardBookAbbreviatedName())]!! else sourceBookDetails
    val targetDoc = targetBookDetails.m_RootNode.ownerDocument
    val clonedNodes = Dom.cloneNodes(targetDoc, nodes, true)
    val container = Dom.createNode(targetDoc, "<_X_container/>")
    Dom.addChildren(container, clonedNodes)
    targetBookDetails.m_MoveDataForInsertionIntoTarget.add(container)
    NodeMarker.setReversificationLastEid(container, moveGroup.rows.last().standardRef.toString()) // Useful when working out where to insert the block.
    return container
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Non-move                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Just one comment on why I use getNonMoveRowsWithBookAs STANDARD below.
     We are dealing here with non-move statements.  This includes Renumbers
     which aren't shifting stuff to a new book, and non-renumbers. */

  private fun processNonMove (bookNo: Int, selector: String)
  {
    val bookThunk = m_BookDetails[bookNo]!!
    //Dbg.outputDom(bookThunk.m_Document, "a")
    val rows = getNonMoveRowsWithBookAsStandard(bookNo)
    processSimpleRows(bookThunk.m_RootNode, rows, selector)
  }


  /****************************************************************************/
  private fun processSimpleRows (rootNode: Node, rows: List<ReversificationDataRow>, selector: String)
  {
     val sidMap: MutableMap<RefKey, Node> = HashMap(2000)
     val eidMap: MutableMap<RefKey, Node> = HashMap(2000)

     Dom.findNodesByName(rootNode, m_FileProtocol.tagName_verse(), false).forEach {
       if (m_FileProtocol.attrName_verseSid() in it)
         sidMap[getSelectedIdAsRefKey(it, m_FileProtocol.attrName_verseSid())] = it
       else
         eidMap[getSelectedIdAsRefKey(it, m_FileProtocol.attrName_verseEid())] = it
     }

     fun isRenumber (row: ReversificationDataRow) = Action.RenumberVerse == row.action || Action.RenumberTitle == row.action

     if ("renumber" == selector)
       rows.filter { isRenumber(it)  } .forEach { processSimpleRowRenumber  (rootNode.ownerDocument, it, sidMap, eidMap) }
     else
       rows.filter { !isRenumber(it) } .forEach { processSimpleRowNonRenumber(rootNode.ownerDocument, it, sidMap, eidMap) }

     //if (0 != m_Dbg)
     //  Dbg.d(Dom.findNodeByAttributeValue(rootNode.ownerDocument, m_FileProtocol.tagName_verse(), "_X_originalId", "1KI 22:46").toString())
  }


  /****************************************************************************/
  /* The 'simple' in the name of this function is intended to suggest that
     we're doing something which doesn't require a Move.  In fact Move rows
     also go through this processing, because once we've created the cloned
     nodes, they all have to be renumbered in just the same way as non-move
     nodes.

     So, the purpose of this is to handle things like RenumberInSitu, where
     we want to locate a given node and then change its details.
   */

  private fun processSimpleRowNonRenumber (document: Document, row: ReversificationDataRow, sidMap: Map<RefKey, Node>, eidMap: Map<RefKey, Node>)
  {
    /**************************************************************************/
    var sidNode = sidMap[row.standardRefAsRefKey]



    /**************************************************************************/
    /* If the node we are dealing with does not exist, create it if possible,
       or else flag an error and move on. */

    if (null == sidNode)
      sidNode = m_EmptyVerseHandler.createEmptyVerseForReversification(getInsertionPoint(document, row.standardRef), row.standardRef.toRefKey()).first
  }


  /****************************************************************************/
  /* The 'simple' in the name of this function is intended to suggest that
     we're doing something which doesn't require a Move.  In fact Move rows
     also go through this processing, because once we've created the cloned
     nodes, they all have to be renumbered in just the same way as non-move
     nodes.

     So, the purpose of this is to handle things like RenumberInSitu, where
     we want to locate a given node and then change its details.
   */

  private fun processSimpleRowRenumber (document: Document, row: ReversificationDataRow, sidMap: Map<RefKey, Node>, eidMap: Map<RefKey, Node>)
  {
    /**************************************************************************/
    //if (row.rowNumber == 2603) m_Dbg = 1
    //Dbg.d(1 == m_Dbg)



    /**************************************************************************/
    val sidNode = sidMap[row.sourceRefAsRefKey]
    val eidNode = eidMap[row.sourceRefAsRefKey]
    val newId = row.standardRef.toString()
    changeId(sidNode!!, m_FileProtocol.attrName_verseSid(), newId)
    changeId(eidNode!!, m_FileProtocol.attrName_verseEid(), newId)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Canonical titles                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /*
     Canonical titles appear predominantly within Psalms.  There are a few
     elsewhere -- for example in Hab 3 -- but only Psalms is of interest here,
     because only the titles in Psalms are subject to reversification.

     Not all psalms have canonical titles.  Of those that do, most have titles
     at the start of the chapter, but a few have a canonical title at the end
     (instead of or as well as one at the start).  The processing here is
     concerned only with canonical titles at the _start_ of the chapter.

     A psalm is regarded here as already having a canonical title ...

     - Not if it has a para:d or title:psalm heading: not all texts will do so.

     - Not if according to NRSVA we would _expect_ this psalm to have a
       canonical title.

     - But if it has any canonical text prior to the first verse sid.


     This fact is really not particularly of interest here, since the rule
     processing will already have worked out which reversification rows apply,
     and if one of those rows involves a source title, we can be confident that
     the text does indeed have something which constitutes a title.

     The processing may simply add footnotes to existing titles; it may move
     existing titles to different chapters; or it may turn v1 (or in some cases
     v1 and v2) into a canonical title.

     At the time the processing here is invoked, the text will not contain any
     markup for canonical headings: even if the reversification data requires
     us merely to keep an 'existing' heading in place, we still need to mark
     it up appropriately.

     The reversification data does indeed contain 'Title' in either the source
     or standard refs (or both).  However, I realised a while back that if I
     change that to refer instead to verse zero, all of the normal Renumber /
     Move processing could be applied, and by the time we reach here, the
     reversification data will have been changed to reflect that intuition.

     All we need to here, therefore, is turn any existing canonical title
     material (available from the BibleStructure class) into verse zero,
     run the normal Renumber and Move processing, and then convert anything
     now marked as verse zero into title tags. */


  /****************************************************************************/
  /* By turning existing titles into verse 0, ordinary Move and Renumber
     processing can be applied.  So here I look up to find out what nodes
     form the title, and then insert a sid for verse zero before them, and an
     eid for verse zero after them, also marking sid and eid with attributes
     which will make it easy to find them. */

  private fun processCanonicalTitles_turnSourceIntoVerseZero (row: ReversificationDataRow)
  {
    processCanonicalTitles_turnIntoVerseZero(m_DataCollection.getBibleStructure().getNodeListForCanonicalTitle(row.sourceRefAsRefKey)!!, row.sourceRefAsRefKey)
  }


  /****************************************************************************/
  /* Turns a collection of nodes into verse zero. */

  private fun processCanonicalTitles_turnIntoVerseZero (nodesInTitle: List<Node>, chapterRefKey: RefKey)
  {
    val sid = m_FileProtocol.makeVerseSidNode(nodesInTitle[0].ownerDocument, Pair(Ref.setV(chapterRefKey, 0), null))
    val eid = m_FileProtocol.makeVerseEidNode(nodesInTitle[0].ownerDocument, Pair(Ref.setV(chapterRefKey, 0), null))

    m_DataCollection.getRootNode(BibleAnatomy.C_BookNumberForPsa)!!.getAllNodesBelow().forEach {
      if (nodesInTitle[0] === it)
        Dom.insertNodeBefore(it, sid)
      else if (nodesInTitle.last() === it)
      {
        Dom.insertNodeAfter(it, eid)
        return
      }
    }
  }


  /****************************************************************************/
  /* Turns verse zero into a canonical title tag. */

  private fun processCanonicalTitles_turnVerseZerosIntoCanonicalTitle ()
  {
    /**************************************************************************/
    fun processChapter (chapterNode: Node)
    {
      val verseZeroNodes = m_DataCollection.getBibleStructure().getNodeListForCanonicalTitle(m_FileProtocol.getSidAsRefKey(chapterNode)) ?: return
      val sid = verseZeroNodes[0].previousSibling
      val eid = verseZeroNodes.last().nextSibling
      val titleNode = m_FileProtocol.makeCanonicalTitleNode(sid.ownerDocument)
      Dom.deleteNodes(verseZeroNodes)
      Dom.addChildren(titleNode, verseZeroNodes)
      Dom.insertNodeBefore(sid, titleNode)
      Dom.deleteNode(sid)
      Dom.deleteNode(eid)
    }



    /**************************************************************************/
    val rootNode = m_DataCollection.getRootNode(BibleAnatomy.C_BookNumberForPsa) ?: return
    rootNode.findNodesByName(m_FileProtocol.tagName_chapter()).forEach { processChapter(it) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Miscellaneous                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private class NodesAndIndices
  {
    lateinit var nodeList: List<Node>
    val sidMap: MutableMap<RefKey, Int> = HashMap(2000)
    val eidMap: MutableMap<RefKey, Int> = HashMap(2000)
  }


  /****************************************************************************/
  private fun changeId (node: Node, sel: String, newVal: String)
  {
    NodeMarker.setOriginalId(node, node[sel]!!)
    node[sel] = newVal
  }


  /****************************************************************************/
  private fun error (refKey: RefKey, msg: String)
  {
    Logger.error(refKey, msg)
  }


  /****************************************************************************/
  /* Finds the sid before which a new verse can be inserted.  There will always
     _be_ one because we have a dummy verse at the end of each chapter, and
     the new verse is guaranteed to fit before that if nowhere else. */

  private fun getInsertionPoint (rootNode: Node, sidRef: Ref): Node
  {
    val sidRefKey = sidRef.toRefKey()
    val chapterRef = m_FileProtocol.refToString(sidRef.toRefKey_bc())
    var chapterNode = Dom.findNodeByAttributeValue(rootNode, m_FileProtocol.tagName_chapter(), m_FileProtocol.attrName_chapterSid(), chapterRef)

    if (null == chapterNode) // If we're having to create a new chapter, chapterNode will presently be null.
    {
      val bookNode = Dom.findNodeByName(rootNode, m_FileProtocol.tagName_book(), false)!!
      chapterNode = Dom.createNode(rootNode.ownerDocument, "<${m_FileProtocol.tagName_chapter()} ${m_FileProtocol.attrName_chapterSid()}='$chapterRef'"); NodeMarker.setGeneratedReason(chapterNode, "chapterCreatedByReversification")
      val verseSidNode = Dom.createNode(rootNode.ownerDocument, "<${m_FileProtocol.tagName_verse()} ${m_FileProtocol.attrName_verseSid()}='$chapterRef:${RefBase.C_BackstopVerseNumber}'/>")
      val verseEidNode = Dom.createNode(rootNode.ownerDocument, "<${m_FileProtocol.tagName_verse()} ${m_FileProtocol.attrName_verseEid()}='$chapterRef:${RefBase.C_BackstopVerseNumber}'/>")
      chapterNode.appendChild(verseSidNode)
      chapterNode.appendChild(verseEidNode)
      bookNode.appendChild(chapterNode)
    }

    val sids = Dom.findNodesByAttributeName(chapterNode, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid())
    return sids.find { m_FileProtocol.readRef(it[m_FileProtocol.attrName_verseSid()]!!).toRefKey() > sidRefKey }!!
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
      if (m_FileProtocol.tagName_verse() != Dom.getNodeName(node)) return
      if (m_FileProtocol.attrName_verseSid() in node)
        res.sidMap[getSelectedIdAsRefKey(node, m_FileProtocol.attrName_verseSid())] = ix
      else
        res.eidMap[getSelectedIdAsRefKey(node, m_FileProtocol.attrName_verseEid())] = ix
    }

    res.nodeList = Dom.getAllNodesBelow(startNode)
    res.nodeList.indices.forEach { addToMaps(it) }

    return res
  }


  /****************************************************************************/
  private fun getSelectedIdAsRef (node: Node, sel: String) = m_FileProtocol.readRef(node[sel]!!)
  private fun getSelectedIdAsRefKey (node: Node, sel: String) = getSelectedIdAsRef(node, sel).toRefKey()
  private fun warning (refKey: RefKey, msg: String) = Logger.warning(refKey, msg)





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Indicates whether footnotes should be attached to source or standard ref.

     This is probably going to be 'standard' throughout, but having this method
     gives us the chance to be slightly more flexible. */

  private fun markFootnoteTargets ()
  {
    getSelectedRows().forEach { it.attachFootnoteTo = ReversificationDataRow.AttachFootnoteTo.Standard }
  }


  /****************************************************************************/
  /* There is special processing here for Psalms, where it is useful to treat
     canonical titles in a special way.  In fact a canonical title may appear
     also in Hab 3, but apparently is never likely to be subject to
     reversification processing. */

  private class BookDetails (bookNumber: Int)
  {
    /**************************************************************************/
    val m_BookNumber = bookNumber
    lateinit var m_RootNode: Node
    var m_MoveDataForInsertionIntoTarget: MutableList<Node> = ArrayList()



    /**************************************************************************/
    init
    {
      if (null == m_DataCollection.getRootNode(m_BookNumber))
      {
        if (m_FileProtocol === Osis_FileProtocol)
          Osis_BookAndChapterCreator.makeBook(m_DataCollection, m_BookNumber)
        else
          Osis_BookAndChapterCreator.makeBook(m_DataCollection, m_BookNumber)
      }

      m_BookDetails[m_BookNumber] = this
    }
  } // class BookDetails

  private val m_BookDetails: MutableMap<Int, BookDetails> = HashMap(150)
  private lateinit var m_BookAndChapterCreator: X_BookAndChapterCreator
} // PA_ReversificationHandler_ConversionTime


/******************************************************************************/
private interface X_BookAndChapterCreator
{
  fun makeBook (dataCollection: X_DataCollection, bookNo: Int)
  fun makeChapter (rootNode: Node, bookAbbreviation: String, chapterNo: Int)
}


/******************************************************************************/
private object Osis_BookAndChapterCreator: X_BookAndChapterCreator
{
  /****************************************************************************/
  /* I assume here that we have a single document covering the whole of the
     OSIS, and need to insert into it.  The node I create here isn't actually
     valid OSIS.  Elsewhere I have converted div:book into <book>, and this
     latter is what I create. */

  override fun makeBook (dataCollection: X_DataCollection, bookNo: Int)
  {
    val bookName = BibleBookNamesOsis.numberToAbbreviatedName(bookNo)
    val rootNode = dataCollection.getDocument().createNode("<book canonical='false' osisID='$bookName' type='book'/>")

    dataCollection.setRootNode(bookNo, rootNode) // Insert into data structure.

    val insertAfterBookNo = dataCollection.findPredecessorBook(bookNo) // Insert into document.
    val insertAfterNode = dataCollection.getDocument().findNodesByName("book").find { insertAfterBookNo == BibleBookNamesOsis.abbreviatedNameToNumber(it["osisId"]!!) }
    Dom.insertNodeAfter(insertAfterNode!!, rootNode)
  }


  /****************************************************************************/
  override fun makeChapter (rootNode: Node, bookAbbreviation: String, chapterNo: Int)
  {
    val chapterNode = Dom.createNode(rootNode.ownerDocument, "<chapter osisID='$bookAbbreviation $chapterNo'>")
    rootNode.appendChild(chapterNode)

    val verseId = "$bookAbbreviation $chapterNo:${RefBase.C_BackstopVerseNumber}"

    var verseNode = Dom.createNode(rootNode.ownerDocument, "<verse osisID='$verseId' sID='$verseId'/>"); NodeMarker.setDummy(verseNode)
    chapterNode.appendChild(verseNode)

    verseNode = Dom.createNode(rootNode.ownerDocument, "<verse eID='verseId'/>"); NodeMarker.setDummy(verseNode)
    chapterNode.appendChild(verseNode)
  }
}





/******************************************************************************/
class Usx_BookAndChapterCreator: X_BookAndChapterCreator
{
  /****************************************************************************/
  override fun makeBook (dataCollection: X_DataCollection, bookNo: Int)
  {
    val bookName = BibleBookNamesUsx.numberToAbbreviatedName(bookNo)

    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()

    val doc = builder.newDocument()
    val documentRoot = doc.createElement("<usx version='3.0'>")
    val bookRoot = Dom.createNode(doc, "<book code='$bookName'/>")
    documentRoot.appendChild(bookRoot)
    for (i in 1 .. BibleStructure.makeOsis2modNrsvxSchemeInstance(dataCollection.getBibleStructure()).getLastChapterNo(bookName)) makeChapter(bookRoot, bookName, i)

    dataCollection.addFromDoc(doc)
  }


  /****************************************************************************/
  override fun makeChapter (rootNode: Node, bookAbbreviation: String, chapterNo: Int)
  {
    val chapterNode = Dom.createNode(rootNode.ownerDocument, "<chapter sid='$bookAbbreviation $chapterNo' _X_revAction='generatedChapter'>")
    rootNode.appendChild(chapterNode)

    var verseNode = Dom.createNode(rootNode.ownerDocument, "<verse sid='$bookAbbreviation $chapterNo:${RefBase.C_BackstopVerseNumber}'/>"); NodeMarker.setDummy(verseNode)
    chapterNode.appendChild(verseNode)

    verseNode = Dom.createNode(rootNode.ownerDocument, "<verse eid='$bookAbbreviation $chapterNo:${RefBase.C_BackstopVerseNumber}'/>"); NodeMarker.setDummy(verseNode)
    chapterNode.appendChild(verseNode)
  }
}




///******************************************************************************/
///**
// * Handles footnotes for verses affected by reversification and
// * samification.
// *
// * @author ARA "Jamie" Jamieson
// */
//
//class PA_ReversificationFootnoteHandler (caller: PA_ReversificationHandler_ConversionTime)
// {
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                                Public                                  **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  /**
//   * This method has changed a lot over time.
//   *
//   * The fundamental aim was to have it create the content required for a
//   * reversification footnote; and in particular, that footnote would use, as
//   * callout, something which gave information about the verse involved in the
//   * reversification activity.
//   *
//   * It is probably a little invidious to say exactly what happens here now,
//   * because it seems to change quite regularly.
//   *
//   * We may or may not have a footnote, depending upon the type of
//   * reversification run (conversion-time or runtime) and whether we are
//   * creating a basic or an academic module.
//   *
//   * We may or may not take verse details from the NoteMarker details in the
//   * reversification row and embed them in the output (standard) verse, normally
//   * to indicate what served as the source verse.
//   *
//   * One thing we *don't* do any more, though, is to use information from the
//   * NoteMarker details as the callout for the reversification footnote.  There
//   * is no point in doing so, because STEPBible always renders the callout as a
//   * down-arrow, regardless of what we request here.
//   *
//   * @param sidNode The sid node of the verse to which the footnote is to be
//   *   added.
//   *
//   * @param row: The reversification row which gives the salient details.
//   *
//   * @param reversificationType C(onversionTime) or R(untime)
//   *
//   */
//
//  fun addFootnoteAndSourceVerseDetailsToVerse (sidNode: Node, row: ReversificationDataRow, reversificationType: Char)
//  {
//    /**************************************************************************/
//    val reversificationType = 'C' // Runtime.
//    val reversificationNoteType = ConfigData["stepReversificationNoteType"]?.first()?.uppercaseChar() ?: 'B' // A(cademic) or B(asic).
//    val footnoteNodes = m_Caller.makeFootnote(sidNode.ownerDocument, row, reversificationType, reversificationNoteType) as MutableList<Node> ?: return
//    if (footnoteNodes.isNotEmpty())
//      IssueAndInformationRecorder.addGeneratedFootnote(Ref.rd(row.sourceRefAsRefKey).toString() + " (ReversificationFootnote)")
//
//
//
//    /**************************************************************************/
//    /* Check if we need the text which will typically be superscripted and
//       bracketed. */
//
//    val alternativeRefCollection = row.calloutDetails.alternativeRefCollection
//    if (null != alternativeRefCollection)
//    {
//      val basicContent = if (row.calloutDetails.alternativeRefCollectionHasEmbeddedPlusSign)
//        alternativeRefCollection.getLowAsRef().toString("a") + TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferenceEmbeddedPlusSign") + alternativeRefCollection.getHighAsRef().toString("a")
//      else if (row.calloutDetails.alternativeRefCollectionHasPrefixPlusSign)
//        TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferencePrefixPlusSign") + alternativeRefCollection.toString("a")
//      else
//        alternativeRefCollection.toString("a")
//
//      val textNode = Dom.createTextNode(sidNode.ownerDocument, TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", basicContent))
//      val containerNode = Dom.createNode(sidNode.ownerDocument, "<_X_reversificationCalloutAlternativeRefCollection/>")
//      containerNode.appendChild(textNode)
//      footnoteNodes.add(containerNode)
//    }
//
//
//
//    /**************************************************************************/
//    /* Add the source verse details.  I do this in two bites.  I always add
//       a stylised comment giving the source verse details.  */
//
//    val sourceRefCollection = row.calloutDetails.sourceVerseCollection
//    if (null != sourceRefCollection)
//    {
//      val sourceRefCollectionAsPossiblyAbbreviatedString = sourceRefCollection.toStringUsx()
//      val sidRefLow = RefCollection.rdOsis(sidNode["sID"]!!).getLowAsRef()
//      val sourceRefLow = RefCollection.rdUsx(sourceRefCollectionAsPossiblyAbbreviatedString, sidRefLow, "v").getLowAsRef()
//      if (sidRefLow != sourceRefLow)
//        Logger.error(sidRefLow.toRefKey(), "altVerse error (reversification data gives $sourceRefCollectionAsPossiblyAbbreviatedString).")
//
//      val basicContent = sourceRefCollection.toString("a")
//      val altVerseNumber = TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", basicContent)
//      val textNode = Dom.createTextNode(sidNode.ownerDocument, altVerseNumber)
//      val containerNode = Dom.createNode(sidNode.ownerDocument, "<_X_reversificationCalloutSourceRefCollection/>")
//      containerNode.appendChild(textNode)
//      footnoteNodes.add(containerNode)
//    }
//
//
//
//    /**************************************************************************/
//    footnoteNodes.reversed().forEach { Dom.insertNodeAfter(sidNode, it) }
//  }
//
//
//
//
//
//  /****************************************************************************/
//  /****************************************************************************/
//  /**                                                                        **/
//  /**                                Private                                 **/
//  /**                                                                        **/
//  /****************************************************************************/
//  /****************************************************************************/
//
//  /****************************************************************************/
//  private val C_ReversificationNotesLevel_None = 999
//  private val C_ReversificationNotesLevel_Basic = 0
//  private val C_ReversificationNotesLevel_Academic = 1
//  private var m_ReversificationNotesLevel = C_ReversificationNotesLevel_None
//
//
//  /****************************************************************************/
//  private fun getReversificationNotesLevel ()
//  {
//     when (ConfigData["stepReversificationFootnoteLevel"]!!.lowercase())
//     {
//       "basic" ->    m_ReversificationNotesLevel = C_ReversificationNotesLevel_Basic
//       "academic" -> m_ReversificationNotesLevel = C_ReversificationNotesLevel_Academic
//     }
//  }
//
//
//  /****************************************************************************/
//  /* Used where we want to have a standard footnote callout on reversified
//     verses, rather than use the callout defined in the reversification data. */
//
//  private var m_FootnoteCalloutGenerator: MarkerHandler =
//    MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!)
//
//  private val m_Caller: PA_ReversificationHandler_ConversionTime
//
//  init {
//    getReversificationNotesLevel() // So we know what kinds of footnotes are needed,
//    m_Caller = caller
//  }
//
//
//}
//
//
//
//
//
//
//
//
//
//
//
//
//
//
////  /****************************************************************************/
////  override fun makeChapter (rootNode: Node, bookAbbreviation: String, chapterNo: Int)
////  {
////    val chapterNode = Dom.createNode(rootNode.ownerDocument, "<chapter osisID='$bookAbbreviation $chapterNo'>")
////    rootNode.appendChild(chapterNode)
////
////    val verseId = "$bookAbbreviation $chapterNo:${RefBase.C_BackstopVerseNumber}"
////
////    var verseNode = Dom.createNode(rootNode.ownerDocument, "<verse osisID='$verseId' sID='$verseId'/>"); NodeMarker.setDummy(verseNode)
////    chapterNode.appendChild(verseNode)
////
////    verseNode = Dom.createNode(rootNode.ownerDocument, "<verse eID='verseId'/>"); NodeMarker.setDummy(verseNode)
////    chapterNode.appendChild(verseNode)
////  }
////}
//
//
//
//
//
/////******************************************************************************/
////class PROBABLY_NOT_WORKING_Usx_ConversiontimeReversificationHandler: PROBABLY_NOT_WORKING_PA_ConversionTimeReversification()
////{
////  /****************************************************************************/
////  override fun makeBook (bookNo: Int)
////  {
////    val bookName = BibleBookNamesUsx.numberToAbbreviatedName(bookNo)
////
////    val factory = DocumentBuilderFactory.newInstance()
////    val builder = factory.newDocumentBuilder()
////
////    val doc = builder.newDocument()
////    val documentRoot = doc.createElement("<usx version='3.0'>")
////    val bookRoot = Dom.createNode(doc, "<book code='$bookName'/>")
////    documentRoot.appendChild(bookRoot)
////    for (i in 1 .. BibleStructure.makeOsis2modNrsvxSchemeInstance(m_BibleStructure!!).getLastChapterNo(bookName)) makeChapter(bookRoot, bookName, i)
////
////    m_DataCollection.addFromDoc(doc)
////  }
////
////
////  /****************************************************************************/
////  fun makeChapter (rootNode: Node, bookAbbreviation: String, chapterNo: Int)
////  {
////    val chapterNode = Dom.createNode(rootNode.ownerDocument, "<chapter sid='$bookAbbreviation $chapterNo' _X_revAction='generatedChapter'>")
////    rootNode.appendChild(chapterNode)
////
////    var verseNode = Dom.createNode(rootNode.ownerDocument, "<verse sid='$bookAbbreviation $chapterNo:${RefBase.C_BackstopVerseNumber}'/>"); NodeMarker.setDummy(verseNode)
////    chapterNode.appendChild(verseNode)
////
////    verseNode = Dom.createNode(rootNode.ownerDocument, "<verse eid='$bookAbbreviation $chapterNo:${RefBase.C_BackstopVerseNumber}'/>"); NodeMarker.setDummy(verseNode)
////    chapterNode.appendChild(verseNode)
////  }
//
//
///******************************************************************************/
///* Code from the most recent previous version.   The first portion -- headed
//   'Data aggregation' -- contains code needed to pick up Moves and to
//   accumulate them into collections such that the collection can be moved en
//   masse, rather than moving verses one at a time.  Something along these lines
//   will be needed if we ever decide to reinstate conversion-time
//   restructuring. */
//
//
//
//
//

//    dataRow.processingFlags = dataRow.processingFlags.or(
//      when (dataRow.action)
//      {
//        "emptyverse"     -> C_CreateIfNecessary
//        "ifabsent"       -> C_CreateIfNecessary
//        "ifempty"        -> 0
//        "keepverse"      -> getAllBiblesComplaintFlag(dataRow)
//        "mergedverse"    -> C_CreateIfNecessary
//        "missingverse"   -> C_CreateIfNecessary
//        "psalmtitle"     -> C_ComplainIfStandardRefDidNotExist
//        "renumbertitle"  -> C_ComplainIfStandardRefExisted.or(C_StandardIsPsalmTitle).or(if ("title" in getField("SourceRef", dataRow).lowercase()) C_SourceIsPsalmTitle else 0).or(if ("title" in getField("StandardRef", dataRow).lowercase()) C_StandardIsPsalmTitle else 0)
//        "renumberverse"  -> C_ComplainIfStandardRefExisted.or(C_Renumber)
//        "renumberverse*" -> C_ComplainIfStandardRefExisted.or(C_Renumber).or(C_Move)
//        else             -> 0
//    })
//
//
//  /****************************************************************************/
//  /* This is revolting; I can only assume that the need for it became apparent
//     late in the day, when it would have been too difficult to rejig the
//     reversification data to handle it properly.
//
//     Lots of rows are marked 'AllBibles' in the SourceType field, and most of
//     these are marked KeepVerse.  Normally KeepVerse retains an existing verse
//     and complains if the verse does not already exist.
//
//     However, on AllBibles rows, KeepVerse is allowed to create verses if they
//     don't already exist -- except that in a further twist, it has to issue a
//     warning in some cases (but not all) if it has to create the verse.
//
//     And just to make life thoroughly awful, the way I am required to
//     distinguish these cases has to be based upon the contents of the FootnoteA
//     column -- certain things there imply that a warning is needed, while others
//     do not.  (The problem being here that this field is free-form text, so
//     sooner or later it is going to change, sure as eggs is eggs, and I shan't
//     realise that's an issue.) */
//
//  private val m_NoteAOptionsText = listOf("At the end of this verse some manuscripts add information such as where this letter was written",
//                                          "In some Bibles this verse is followed by the contents of Man.1 (Prayer of Manasseh)",
//                                          "In some Bibles this verse starts on a different word")
//
//  private fun getAllBiblesComplaintFlag (row: ReversificationDataRow): Int
//  {
//    val noteA = getField("Reversification Note", row)
//    return if (m_NoteAOptionsText.contains(noteA)) C_ComplainIfStandardRefDidNotExist else 0
//  }
//
//
//
//
//
