package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleBookAndFileMapperEnhancedUsx
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.bibledetails.BibleStructure
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.getExtendedNodeName
import org.stepbible.textconverter.support.ref.*
import org.stepbible.textconverter.support.shared.SharedData
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.support.usx.Usx
import org.stepbible.textconverter.C_CollapseSubverses
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.io.PrintWriter
import java.nio.file.Paths
import java.util.*


/******************************************************************************/
/**
 * Handles reversification.
 *
 * Not all Bibles are split up into verses in the same way.  This is a problem
 * both from the point of view of interlinear display and from the point of
 * view of interacting with other tools.
 *
 * If you have a given piece of Greek translated into v1 of one Bible and v2
 * of another, you can't simply display v1 of the one against v1 of the other,
 * because you would be lining up two different things.
 *
 * And you can't necessarily link v1 of one of the texts to the section of a
 * commentary dealing with v1, because chances are that the commentary has
 * assumed that v1 translates the same portion of the Greek as does v1 in
 * KJVA, and this may not be the case.
 *
 * The purpose of the present class is to reorganise the verse structure of
 * the Bible being worked upon so that it matches up with NRSVA (a lot of
 * commentaries are based upon KJVA, but NRSV, aside from being more modern,
 * uses a virtually identical versification scheme.
 *
 * The processing is driven by a very extensive dataset which is read directly
 * from the web.  This may require verses to be renumbered in situ, to be moved
 * elsewhere (even to different books in some cases), to be merged, etc, etc.
 * And whether changes are applied to the text or not, footnotes may be added
 * to indicate what would happen in other Bibles.
 *
 * One word of warning.  When DIB drew up the reversification data, he imagined
 * a situation in which the target book started off empty, and was then
 * progressively populated, using the reversification data where this provided
 * a given verse, and copying the verse from the raw text if it did not.
 *
 * This has proved not really to be practical, because verses are almost
 * always intimately associated with other markup, and starting ex nihilo
 * would require us somehow to reproduce this infrastructure.
 *
 * My approach, therefore, is to start with a fully populated text, and then use
 * the reversification data to determine what changes I need to make to it.
 * This, too, is complicated, as will be apparent from the amount of code in this
 * class -- but I believe less so than the alternative.
 *
 * The main issue, however, is the disconnect between what DIB anticipated and
 * what I have actually done, which can complicate discussions because each
 * party is bringing different assumptions to the table.
 *
 * The nitty-gritty of the processing is discussed in in-code comments below,
 * but in outline, it takes files from the EnhancedUsx folder and writes them
 * back there.
 *
 * Note that there are some potential issues which may have to be broached at
 * some stage -- USX permits (and by implication, encourages) markup which runs
 * across verse boundaries, and in those cases where I have to move verses
 * from one place to another, it complicates the task of extracting the text,
 * and also leaves the question of what should happen at the place from which
 * it has been removed.
 *
 * @author ARA "Jamie" Jamieson
 */

object TextConverterProcessorReversification: TextConverterProcessorBase ()
{
  /****************************************************************************/
  override fun banner (): String
  {
    return "Reversifying"
  }


  /****************************************************************************/
  override fun pre (): Boolean
  {
      return true
  }


  /****************************************************************************/
  override fun runMe (): Boolean
  {
      return "none" != ConfigData["stepReversificationType"]!!.lowercase()
  }


  /****************************************************************************/
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("reversificationType", 1, "None / Basic / Academic (append '?' to Basic or Academic to have the converter decide whether to reversify", listOf("None", "Basic", "Academic", "Basic?", "Academic?"), "None", false)
  }


  /****************************************************************************/
  override fun process (): Boolean
  {
    control()
    return true
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Control                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* This is going to be a pretty memory intensive implementation, because I
     have decided that the only way to simplify the code is to keep all files
     affected by reversification in memory simultaneously.

     There is therefore one BookDetails class for each file, which holds the
     document structure itself, along with a number of other things which
     we'll come to shortly.

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
  /****************************************************************************/

  /****************************************************************************/
  /* There is special processing here for Psalms, where it is useful to treat
     canonical titles in a special way.  In fact a canonical title may appear
     also in Hab 3, but apparently is never likely to be subject to
     reversification processing. */

  private class BookDetails (bookName: String)
  {
    val m_AbbreviatedName: String
    val m_BookNumber: Int
    var m_Document: Document
    val m_FilePath: String
    var m_MoveDataForInsertionIntoTarget: MutableList<Node> = ArrayList()

    fun finalise ()
    {
       when (m_BookNumber)
       {
         BibleBookNamesUsx.C_BookNo_Psa, BibleBookNamesUsx.C_BookNo_Hab -> canonicalTitlesPost(m_Document)
       }

       Dom.outputDomAsXml(m_Document, m_FilePath, null)
    }

    init
    {
      m_AbbreviatedName = bookName
      m_BookNumber = BibleBookNamesUsx.abbreviatedNameToNumber(m_AbbreviatedName)
      m_FilePath = BibleBookAndFileMapperEnhancedUsx.getFilePathForBook(bookName) ?: makeBook(bookName)
      m_Document = Dom.getDocument(m_FilePath)
      when (m_BookNumber)
      {
        BibleBookNamesUsx.C_BookNo_Psa, BibleBookNamesUsx.C_BookNo_Hab -> canonicalTitlesPre(m_Document)
      }

      m_BookDetails[m_AbbreviatedName] = this
    }
  }

  private val m_BookDetails: MutableMap<String, BookDetails> = HashMap(150)


  /****************************************************************************/
  private fun control ()
  {
    initialise()
    ReversificationData.getSourceBooksInvolvedInMoveActionsAbbreviatedNames()  .forEach { processMovePart1(it) }
    ReversificationData.getAllBookNumbersAbbreviatedNames()                    .forEach { processNonMove(it, "renumber") }
    ReversificationData.getStandardBooksInvolvedInMoveActionsAbbreviatedNames().forEach { processMovePart2(it) }
    ReversificationData.getAllBookNumbersAbbreviatedNames()                    .forEach { processNonMove(it, "") }
    insertMoveOriginals()
    ReversificationData.getAllBookNumbersAbbreviatedNames()                    .forEach { terminate(it) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                     Initialisation and termination                     **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun initialise ()
  {
     SharedData.SpecialFeatures.addSpecialFeature("Reversification type", ConfigData["stepReversificationType"]!!)
     m_FootnoteCalloutGenerator = MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!)
     ReversificationData.process()
     getReversificationNotesLevel()
     checkExistenceCriteriaForCrossBookMappings(ReversificationData.getBookMappings())
     ReversificationData.getAllBookNumbersAbbreviatedNames().forEach { BookDetails(it) } // Create BookDetails entry and carry out any pre-processing.
  }


  /****************************************************************************/
  private fun terminate (bookAbbreviation: String)
  {
    /**************************************************************************/
    val bookDetails = m_BookDetails[bookAbbreviation]!!
    deleteEmptyChapters(bookDetails.m_Document)



    /**************************************************************************/
    /* Particularly with LXX-based texts, we may have book LJE, which may be
       left empty by reversification, because all of its content is moved to
       BAR.  I allow here that this may be true of other books too.  If a book
       no longer contains any chapters, then the book should be excluded from
       further processing. */

    if (null == Dom.findNodeByName(bookDetails.m_Document, "_X_chapter"))
    {
      File(bookDetails.m_FilePath).delete()
      m_BookDetails.remove(bookAbbreviation)
      return
    }



    /**************************************************************************/
    deleteVersesWhichWereEmptyInRawTextButWhichHaveBeenOverwritten(bookDetails.m_Document)
    doCrossReferenceMappings(bookDetails.m_Document)
    bookDetails.finalise()
  }


  /****************************************************************************/
  /* If we have cross-book moves, there are some books which we target which
     _must_ already exist, and some which must _not_. */

  private fun checkExistenceCriteriaForCrossBookMappings (crossBookMappings: Set<String>)
  {
    fun dontWantTarget (from: String, to: String, message: String)
    {
      if (!crossBookMappings.contains("$from.$to")) return
      if (BibleStructure.UsxUnderConstructionInstance().bookExists(to)) throw StepException(message)
    }

    fun doWantTarget (from: String, to: String, message: String)
    {
      if (!crossBookMappings.contains("$from.$to")) return
      if (!BibleStructure.UsxUnderConstructionInstance().bookExists(to)) throw StepException(message)
    }

    dontWantTarget("dan", "bel", "Need to move text from Dan to Bel, but Bel already exists")
    dontWantTarget("dan", "s3y", "Need to move text from Dan to S3Y, but S3Y already exists")
    dontWantTarget("dan", "sus", "Need to move text from Dan to Sus, but Sus already exists")
    doWantTarget  ("lje", "bar", "Need to move text from LJE to Bar, but Bar does not exist")
    dontWantTarget("2ch", "man", "Need to move text from 2Ch to Man, but Man already exists")
    dontWantTarget("psa", "ps2", "Need to move text from Psa to Ps2, but Ps2 already exists")
  }


  /****************************************************************************/
  /* Used where we want to have a standard footnote callout on reversified
     verses, rather than use the callout defined in the reversification data. */

  private lateinit var m_FootnoteCalloutGenerator: MarkerHandler


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

  private fun deleteEmptyChapters (document: Document)
  {
    fun deleteIfEmpty (chapterNode: Node)
    {
      val firstVerse = Dom.findNodeByName(chapterNode, "verse", false)
      if (null == firstVerse || RefBase.C_BackstopVerseNumber == Ref.rdUsx(Dom.getAttribute(firstVerse, "sid")!!).getV())
        Dom.deleteNode(chapterNode)
    }

    Dom.findNodesByName(document, "_X_chapter").forEach { deleteIfEmpty(it) }
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

  private fun deleteVersesWhichWereEmptyInRawTextButWhichHaveBeenOverwritten (document: Document)
  {
    /**************************************************************************/
    data class Thunk (var sidIx: Int, var eidIx: Int, var empty: Boolean)



    /**************************************************************************/
    /* Identify duplicate sids. */

    val allNodes = Dom.collectNodesInTree(document)
    val duplicateDetails: TreeMap<String, MutableList<Thunk>> = TreeMap(String.CASE_INSENSITIVE_ORDER)
    var activeList: MutableList<Thunk>? = null
    fun collectDuplicates (ix: Int)
    {
      val node = allNodes[ix]

      if (null != activeList &&                                            // We're within a verse.
          Dom.isTextNode(node) && !Dom.isWhitespace((node)) &&             // This is a non-blank text node.
          !Usx.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(node))  // It's canonical.
        activeList!!.last().empty = false                                  // Flag the fact that this verse contains canonical text.

      else if ("verse" == Dom.getNodeName(node))
      {
        if (Dom.hasAttribute(node, "eid"))
        {
          activeList!!.last().eidIx = ix  // Record the index for this node.
          activeList = null               // Flag the fact that we're no longer within a verse.
        }
        else // sid
        {
          val sid = Dom.getAttribute(node, "sid")!!
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

  private fun doCrossReferenceMappings (document: Document)
  {
    val mappings = ReversificationData.getReferenceMappings() as MutableMap<RefKey, RefKey>
    if (C_CollapseSubverses) mappings.keys.forEach { if (Ref.hasS(mappings[it]!!)) mappings[it] = Ref.clearS(mappings[it]!!) }
    CrossReferenceProcessor.updateCrossReferences(document, mappings)
  }


  /****************************************************************************/
  private const val C_ReversificationNotesLevel_None = 999
  private const val C_ReversificationNotesLevel_Basic = 0
  private const val C_ReversificationNotesLevel_Academic = 1
  private var m_ReversificationNotesLevel = C_ReversificationNotesLevel_None

  private fun getReversificationNotesLevel ()
  {
     when (ConfigData["stepReversificationType"])
     {
       "basic" ->    m_ReversificationNotesLevel = C_ReversificationNotesLevel_Basic
       "academic" -> m_ReversificationNotesLevel = C_ReversificationNotesLevel_Academic
     }
  }



  /****************************************************************************/
  private fun makeBook (bookAbbreviation: String): String
  {
    val bookName = bookAbbreviation.uppercase()
    val filePath = Paths.get(StandardFileLocations.getEnhancedUsxFolderPath(), "generatedFile_$bookName.usx").toString()
    BibleBookAndFileMapperEnhancedUsx.addBookDetails(filePath, bookName)
    File(filePath).printWriter().use { out ->
      out.println("<?xml version='1.0' encoding='UTF-8'?>")
      out.println("<_X_usx version='3.0'>")
      out.println("  <_X_book code='#'/>".replace("#", bookName))
      for (i in 1 .. BibleStructure.NrsvxInstance().getLastChapterNo(bookName)) makeChapter(out, bookName, i)
      out.println("</_X_usx>")
    }

    return filePath
  }


  /****************************************************************************/
  private fun makeChapter (out: PrintWriter, bookName: String, chapterNo: Int)
  {
    out.println("    <_X_chapter sid='$bookName $chapterNo' _X_revAction='generatedChapter'>")
    out.println("      <verse sid='$bookName $chapterNo:${RefBase.C_BackstopVerseNumber}' _TEMP_dummy='y' />")
    out.println("      <verse eid='$bookName $chapterNo:${RefBase.C_BackstopVerseNumber}' _TEMP_dummy='y' />")
    out.println("    </_X_chapter>")
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
       subverses into the target book.  At the time of writing we have
       decided we never want subverses there, so the subverses need to be
       collapsed down to their owning verse.  I do this elsewhere as part of
       the tidying up process, so in the context of the move processing there
       is no problem in creating subverses if that is what is requested.


     The main complication with move processing is the fact that we may be
     targeting existing verses.  For example, we may be moving v3 to v23,
     but v23 may already exist.  Of course if that situation persisted, we'd
     have a problem, because we'd be losing information.  But normally there
     would be another reversification row which would get v23 out of the way,
     in which case the main issue is just trying to make sure that it has had
     a chance to do that before we overwrite it.

     There is a further issue here, though, because we are experimenting with
     the notion of leaving the source verses for MOVEs in place.  Or rather,
     leaving in place ths source verses for _some_ MOVEs.  The reason for doing
     this is that MOVEs have a fairly dramatic effect upon the structure of the
     text, and in many cases the licence conditions applied to a text may not
     give us the leeway to make those kinds of changes.  Of course this may
     leave us with a text which is no longer NRSVA-compliant, but for the
     perhaps of this experiment we will be using a version of osis2mod and
     JSword which have been modified to cope with this.

     Of course, it will remain the case that we cannot retain the original
     source if something else also targets it, or information will be lost,
     so I have had to beef up the validation to confirm that we're ok in
     that respect.

     Whether the original text is left in its original location or removed
     from it is controlled by a flag setting in the reversification rows.
     This is set in ReversificationData.  At the time of writing, it is set
     automatically by examining what a given reversification statement is
     doing, although it looks as though we may need to be able to go beyond
     this at some point -- that it may not be possible to detect
     automatically all of the places where original verses should be retained
     in situ, and that we may need some way of specifying individual cases
     manually.

     The approach I originally adopted here (ie before this idea of retaining some
     source verses in situ) was to run over all of the books which act
     as _sources_ for moves, shifting to a temporary storage area the data to be
     moved (and in so doing, deleting it from the source document).  I then do
     all of the non-move-related processing on each book; and then finally pick
     up any move data from the temporary storage areas.

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
     avoid cross-boundary, we do at least need to consider its implications.

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
  private fun processMovePart1 (bookAbbreviation: String)
  {
    val bookThunk = m_BookDetails[bookAbbreviation]!!
    val structure = getNodesAndIndices(bookThunk.m_Document)
    ReversificationData.getMoveGroupsWithBookAsSource(bookAbbreviation).forEach { processMovePart1(it, bookThunk, structure) }
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
    moveGroup.rows.forEach { SharedData.VersesAmendedByReversification[it.sourceRefAsRefKey] = it.standardRefAsRefKey }

    val sidIx = structure.sidMap[moveGroup.rows.first().sourceRefAsRefKey]!!
    val eidIx = structure.eidMap[moveGroup.rows.last().sourceRefAsRefKey]!!
    val sidNode = structure.nodeList[sidIx]
    val eidNode = structure.nodeList[eidIx]

    if (Dom.isSiblingOf(sidNode, eidNode) || "_X_chapter" == Dom.getNodeName(eidNode.parentNode))
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
     eid and therefore need to be excluded.

     Note 1: This statement potentially removes the source nodes and any
     superstructure rendered empty by their removal.  Originally it was
     unconditional.  At present, as part of the experiment referred to above,
     I do this only if the reversification row does not indicate that the
     source rows are to be retained.  I make the assumption that since all of
     the MOVEs in a group are related, I need look only at the first row in the
     group to make that call.  If we want to revert to the earlier behaviour of
     _always_ removing the source code, all that is necessary is to remove the
     'if' and make the forEach statement unconditional.
   */

  private fun movePart1BlockEasy (nodes: List<Node>, moveGroup: ReversificationMoveGroup, sourceBookDetails: BookDetails)
  {
    val retainSourceNodesInSitu = moveGroup.rows[0].retainOriginalTextInSitu()
    val sourceNodes = Dom.pruneToTopLevelOnly(nodes)                              // We don't want both children and their parents in the list.

    if (retainSourceNodesInSitu)                                                  // See note 1 above.
      sourceNodes
      .filter { "verse" == Dom.getNodeName(it) }
      .forEach { Dom.setAttribute(it, "_X_doNotValidate", "reversificationMoveRetainedOriginalSource") }
    else
      sourceNodes.forEach { Dom.deleteNodeRecursivelyUpwards(it) }                // See note 1 above.

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
        added _TEMP_childPos to them.

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
      if ("X_chapter" == Dom.getNodeName(parent)) break
      Dom.setAttribute(parent, "_TEMP_childPos", Dom.getChildNumber(node).toString())
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
      if (!Dom.hasAttribute(parent, "_TEMP_childPos")) return false
      val ix = Dom.getAttribute(parent, "_TEMP_childPos")!!.toInt()
      Dom.deleteAttribute(parent, "_TEMP_childPos")
      val siblings = Dom.getChildren(node.parentNode)
      siblings.subList(ix + 1, siblings.size).forEach { Dom.deleteNode(it) } // No need to delete recursively upwards, because the parent is guaranteed still to have at least one child.
      return true
    }

    node = Dom.findNodesByAttributeName(container, "verse", "eid").last()
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
       _TEMP_childPos. */

    overExtendedSourceList.filter { !Dom.hasAttribute(it, "_TEMP_childPos") } .forEach { Dom.deleteNodeRecursivelyUpwards(it) }



    /**************************************************************************/
    /* Step 7. Delete the younger siblings of the eid node, along with the eid
       node itself. */

    fun deleteOlderSiblings (node: Node): Boolean
    {
      val parent = node.parentNode
      if (!Dom.hasAttribute(parent, "_TEMP_childPos")) return false
      val ix = Dom.getAttribute(parent, "_TEMP_childPos")!!.toInt()
      Dom.deleteAttribute(parent, "_TEMP_childPos")
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
  private fun movePart1EntireChapter (moveGroup: ReversificationMoveGroup, bookDetails: BookDetails)
  {
    /**************************************************************************/
    /* Find the chapter node in the source document and remove it. */

    val chapterNode = Dom.findNodeByAttributeValue(bookDetails.m_Document, "_X_chapter", "sid", moveGroup.getSourceChapterRefAsString())!!
    Dom.deleteNode(chapterNode)



    /**************************************************************************/
    /* Change the sid of the chapter and then update all of the contained nodes
       to have their new sids / eids. */

    changeId(chapterNode, "sid", moveGroup.getStandardChapterRefAsString())
    processSimpleRows(chapterNode, moveGroup.rows, "renumber")



    /**************************************************************************/
    /* Create the structure which is used in part 2 processing, and store it in
       the correct place. */

    makeMoveContainer(chapterNode, bookDetails, moveGroup)
  }


  /****************************************************************************/
  private fun processMovePart2 (bookAbbreviation: String)
  {
    val bookDetails = m_BookDetails[bookAbbreviation]!!

    fun doIt (container: Node)
    {
      val lastEid = Ref.rdUsx(Dom.getAttribute(container, "_TEMP_lastEid")!!)
      val insertionPoint = getInsertionPoint(bookDetails.m_Document, lastEid)
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
    val targetBookDetails = if (moveGroup.crossBook) m_BookDetails[moveGroup.getStandardBookAbbreviatedName()]!! else sourceBookDetails
    val targetDoc = targetBookDetails.m_Document
    val clonedNodes = Dom.cloneNodes(targetDoc, nodes, true)
    val container = Dom.createNode(targetDoc, "<_TEMP_container/>")
    Dom.addChildren(container, clonedNodes)
    targetBookDetails.m_MoveDataForInsertionIntoTarget.add(container)
    Dom.setAttribute(container,"_TEMP_lastEid", moveGroup.rows.last().standardRef.toString()) // Useful when working out where to insert the block.
    cloneMoveNodesIfNecessary(nodes, moveGroup)
    return container
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Original text for Move                         **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private class SavedMoveDetailsOriginal
  {
    constructor (container: Node, moveGroup: ReversificationMoveGroup)
    {
      m_Container = container
      m_FirstSourceRefKey = moveGroup.rows[0].sourceRefAsRefKey
      m_LastSourceRefKey = moveGroup.rows.last().sourceRefAsRefKey
      m_FirstStandardRefKey = moveGroup.rows[0].standardRefAsRefKey
      m_LastStandardRefKey = moveGroup.rows.last().standardRefAsRefKey
    }

    override fun toString (): String
    {
      return "SavedMoveDetailsOriginal: ${Ref.rd(m_FirstSourceRefKey)} / ${Ref.rd(m_LastSourceRefKey)}"
    }

    var m_Container: Node
    var m_FirstSourceRefKey: Long
    var m_LastSourceRefKey: Long
    var m_FirstStandardRefKey: Long
    var m_LastStandardRefKey: Long
  }

  private val m_MoveOriginals: MutableList<SavedMoveDetailsOriginal> = ArrayList()



  /****************************************************************************/
  /* Moves generally are a bit of a problem.  Unless we move the text, we end
     up with something not aligned with NRSV(A); if we _do_ move the text, we
     end up with something which looks wrong from the user perspective (and
     more significantly, from the perspective of copyright holders).  At the
     time of writing we are investigating a couple of ways of addressing
     this.  One of them lies entirely beyond my control, but the other -- the
     one addressed here -- can be handled purely within the converter.

     So whatever we do, we are going to carry out the Move.  But with the
     processing here, we also retain a copy of the text which is being moved,
     and we insert this again later (albeit now as a perhaps very long
     series of pseudo subverses of the verse which falls immediately before
     the Move'd text).  This means that we can have the original text still
     visible at the original position (albeit not in the form of verses, and
     therefore not accessible via searching etc)' _and_ at the Move target
     location, which is where STEP needs it in order for its added value
     features to work.
  */

  /****************************************************************************/
  /* This does nothing except on cross-chapter Moves.  On these, it stores up
     a modified copy of the source nodes, for use later. */

  private fun cloneMoveNodesIfNecessary (nodes: List<Node>, moveGroup: ReversificationMoveGroup)
  {
    /**************************************************************************/
    /* Do nothing unless this is a cross-chapter Move. */

    val firstRow = moveGroup.rows[0]; if (firstRow.sourceRef.toRefKey_bc() == firstRow.standardRef.toRefKey_bc()) return



    /**************************************************************************/
    /* Create a copy of the nodes which are being moved. */

    val sourceDoc = nodes[0].ownerDocument
    val clonedNodes = Dom.cloneNodes(sourceDoc, nodes, true).toMutableList()



    /**************************************************************************/
    /* Turn verse sids into _TEMP_verse sids, and make a note of any additional
       information we may require later.  Then get rid of all eids -- we don't
       need them later, and they may confuse things. */

    var moveGroupIx = 0
    clonedNodes.filter { "verse" == Dom.getNodeName(it) && Dom.hasAttribute(it, "sid") }. forEach { Dom.setNodeName(it, "_TEMP_verse"); Dom.setAttribute(it, "_X_standardRefKey", moveGroup.rows[moveGroupIx++].standardRefAsRefKey.toString()) }
    clonedNodes.removeIf { "verse" == Dom.getNodeName(it) }



    /**************************************************************************/
    /* Package the cloned data and store it for later use. */

    val container = Dom.createNode(sourceDoc, "<_X_reversificationMoveOriginalText/>")
    Dom.addChildren(container, clonedNodes)
    val savedDetails = SavedMoveDetailsOriginal(container, moveGroup)
    m_MoveOriginals.add(savedDetails)
  }


  /****************************************************************************/
  /* Orders the saved details, and amalgamates adjacent elements where
     appropriate.  In other words, if we have a block which has moved, say
     vv 1-10 to some other chapter, and another block which has moved vv 11-20
     (perhaps to somewhere entirely different) we want to amalgamate the
     cloned source data for these two blocks, so we can output it en masse. */

  private fun consolidateMoveOriginals ()
  {
    m_MoveOriginals.sortBy { it.m_FirstSourceRefKey }
    for (i in m_MoveOriginals.size - 2 downTo 0 step 1)
    {
      val nextStart = m_MoveOriginals[i + 1].m_FirstSourceRefKey
      val thisEnd = m_MoveOriginals[i].m_LastSourceRefKey
      if (!BibleStructure.UsxUnderConstructionInstance().isAdjacent(thisEnd, nextStart)) continue

      val childNodes = m_MoveOriginals[i + 1].m_Container.childNodes
      Dom.addChildren(m_MoveOriginals[i].m_Container, childNodes)
      m_MoveOriginals[i].m_LastSourceRefKey = m_MoveOriginals[i + 1].m_LastSourceRefKey
      m_MoveOriginals[i].m_LastStandardRefKey = m_MoveOriginals[i + 1].m_LastStandardRefKey

      m_MoveOriginals.removeAt(i + 1)
    }
  }


  /****************************************************************************/
  /* This controls the process of reinserting the cloned source material.
     We start off by working in turn through each book which has such material,
     building up, for that book, a map relating the eid refKeys to the eid
     nodes.  We then use that map to carry out the actual reinsertion. */

  private fun insertMoveOriginals ()
  {
    val eidMap: MutableMap<Int, NavigableMap<RefKey, Node>> = HashMap()

    fun getEids (refKey: RefKey)
    {
      val bookNo = Ref.getB(refKey)
      if (null != eidMap[bookNo]) return
      val document = m_BookDetails[BibleBookNamesUsx.numberToAbbreviatedName(bookNo)]!!.m_Document
      var eids = Dom.findNodesByAttributeName(document, "verse", "eid")
      eids = eids.subList(0, eids.size - 1) // Don't want to insert stuff in the dummy verse at the end of the chapter.
      val map: NavigableMap<RefKey, Node> = TreeMap()
      eids.forEach { map[RefCollection.rdUsx(Dom.getAttribute(it, "eid")!!).getFirstAsRefKey()] = it }
      eidMap[bookNo] = map
   }

    consolidateMoveOriginals() // Amalgamate adjacent cloned source blocks.
    m_MoveOriginals.forEach { getEids(it.m_FirstSourceRefKey) }
    m_MoveOriginals.forEach { insertMoveOriginals(it, eidMap[Ref.getB(it.m_FirstSourceRefKey)]!!) }
  }


  /****************************************************************************/
  /* This does the actual reinsertion.  'details' is a structure which
     describes a single block of cloned source material which is to be
     reinserted, and eids maps refKeys to verse:eid nodes in the book which is
     covered by this particular block. */

  private fun insertMoveOriginals (details: SavedMoveDetailsOriginal, eids: NavigableMap<RefKey, Node>)
  {
    return
//
//
//
//    /**************************************************************************/
//    /* details.m_FirstSourceRefKey gives us the starting sid for the block which
//       we are inserting.  We want to append this material to the end of the
//       closest verse prior to that sid. */
//
//    val insertBefore = eids.floorEntry(details.m_FirstSourceRefKey).value
//    val document = insertBefore.ownerDocument
//
//
//
//    /**************************************************************************/
//    /* We now know where material is to be inserted; we now need to run over
//       all of the _TEMP_verse nodes in that material, transforming it into the
//       form required, and then insert it. */
//
//    val owningRef = Ref.rdUsx(Dom.getAttribute(insertBefore, "eid")!!).toRefKey()
//
//    fun replaceVerseNode (verseNode: Node)
//    {
//      /************************************************************************/
//      val ref = Ref.rdUsx(Dom.getAttribute(verseNode, "sid")!!)
//
//
//      /************************************************************************/
//      /* If the verse is the first in its chapter, we need to insert a pretend
//         chapter header before it. */
//
//      if (1 == ref.getV())
//      {
//        val headerNode = Dom.createNode(document, "<para style='ms'/>")
//        val rc = RefCollection(ref)
//        headerNode.appendChild(Dom.createTextNode(document, stringFormat("%RefV<b+c>", rc)))
//        Dom.insertNodeBefore(verseNode,Dom.createNode(document, "<para style='p'/>"))
//        Dom.insertNodeBefore(verseNode, headerNode)
//      }
//
//
//
//      /************************************************************************/
//      /* Create a footnote to record details of the location where the verse is
//         duplicated. */
//
//      val standardRef = Ref.rd(Dom.getAttribute(verseNode, "_X_standardRefKey")!!.toLong())
//      val footnoteText =Translations.stringFormatWithLookup("V_reversification_toDetailsForUseWithRetainedSourceDataForBlockMove", RefCollection(standardRef))
//      val footnoteNode = MiscellaneousUtils.makeFootnote(document, owningRef, footnoteText, null)
//
//
//
//      /************************************************************************/
//      /* From the verse tag generate into a suitably formatted piece of text
//         which we simply include in the canonical text, along with the
//         footnote.  Then update the DOM. */
//
//      ref.clearB(); ref.clearC()
//      val textNode = Dom.createTextNode(document, " " + Translations.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", ref))
//      val containerNode = Dom.createNode(document, "<_X_reversificationCalloutAlternativeRefCollection/>")
//      containerNode.appendChild(textNode)
//      containerNode.appendChild(footnoteNode)
//      Dom.insertNodeAfter(verseNode, containerNode)
//      Dom.deleteNode(verseNode)
//    }
//
//
//
//    /**************************************************************************/
//    /* Process all of the _TEMP_verses. */
//
//    Dom.findNodesByName(details.m_Container, "_TEMP_verse", false).forEach { replaceVerseNode(it) }
//
//
//
//    /**************************************************************************/
//    /* Insert a textual marker at top and bottom of the cloned source nodes to
//       give a bit more information about what is going on. */
//
//    val standardRange = RefCollection()
//    if (details.m_FirstStandardRefKey == details.m_LastSourceRefKey)
//      standardRange.add(Ref.rd(details.m_FirstStandardRefKey))
//    else
//      standardRange.add(RefRange(Ref.rd(details.m_FirstStandardRefKey), Ref.rd(details.m_LastStandardRefKey)))
//
//    val headerText = Translations.stringFormatWithLookup("V_reversification_xxxOriginalSourceBlockHeader", standardRange)
//    val trailerText = Translations.stringFormatWithLookup("V_reversification_xxxOriginalSourceBlockTrailer", standardRange)
//    val headerNode = Dom.createTextNode(document, headerText)
//    val trailerNode = Dom.createTextNode(document, trailerText)
//
//    Dom.insertAsFirstChild(details.m_Container, Dom.createNode(document, "<para style='p'/>"))
//    Dom.insertAsFirstChild(details.m_Container, headerNode)
//    Dom.insertAsFirstChild(details.m_Container, Dom.createNode(document, "<para style='p'/>"))
//
//    Dom.insertAsLastChild(details.m_Container, Dom.createNode(document, "<para style='p'/>"))
//    Dom.insertAsLastChild (details.m_Container, trailerNode)
//
//    Dom.insertAsLastChild(details.m_Container, Dom.createNode(document, "<para style='p'/>"))
//    Dom.deleteNode(details.m_Container)
//    Dom.insertNodeBefore(insertBefore, details.m_Container)
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Non-move                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Just one comment on why I use getNonMoveRowsWithBookAsSTANDARD below.
     We are dealing here with non-move statements.  This includes Renumbers
     which aren't shifting stuff to a new book, and non-renumbers.
   */
  private fun processNonMove (bookAbbreviation: String, selector: String)
  {
    val bookThunk = m_BookDetails[bookAbbreviation]!!
    //Dbg.outputDom(bookThunk.m_Document, "a")
    val rows = ReversificationData.getNonMoveRowsWithBookAsStandard(bookAbbreviation)
    processSimpleRows(bookThunk.m_Document.documentElement, rows, selector)
  }


  /****************************************************************************/
  private fun processSimpleRows (rootNode: Node, rows: List<ReversificationDataRow>, selector: String)
  {
     val sidMap: MutableMap<RefKey, Node> = HashMap(2000)
     val eidMap: MutableMap<RefKey, Node> = HashMap(2000)

     Dom.findNodesByName(rootNode, "verse", false).forEach {
       if (Dom.hasAttribute(it, "sid"))
         sidMap[getSelectedIdAsRefKey(it, "sid")] = it
       else
         eidMap[getSelectedIdAsRefKey(it, "eid")] = it
     }

     if ("renumber" == selector)
       rows.filter { it.action.contains("renumber")  } .forEach { processSimpleRowRenumber  (rootNode.ownerDocument, it, sidMap, eidMap) }
     else
       rows.filter { !it.action.contains("renumber") } .forEach { processSimpleRowNonRenumber(rootNode.ownerDocument, it, sidMap, eidMap) }

     //if (0 != m_Dbg)
     //  Dbg.d(Dom.findNodeByAttributeValue(rootNode.ownerDocument, "verse", "_X_originalId", "1KI 22:46").toString())
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
    //val dbg = row.rowNumber == 902
    //Dbg.d(row.rowNumber == 902)
    //Dbg.d(row.rowNumber == 906)
    //Dbg.d(row.toString())
    //if (dbg) Dbg.d(document, "a")



    /**************************************************************************/
    var sidNode = sidMap[row.standardRefAsRefKey]
    var altered = false



    /**************************************************************************/
    /* If the node we are dealing with does not exist, create it if possible,
       or else flag an error and move on. */

    if (null == sidNode)
    {
      if (0 == row.processingFlags.and(ReversificationData.C_CreateIfNecessary))
      {
        error(row.standardRefAsRefKey, "Verse not found: $row")
        return
      }

      if (0 != row.processingFlags.and(ReversificationData.C_ComplainIfDidNotExist))
        warning(row.standardRefAsRefKey, "Verse not found and had to create it: $row")

      sidNode = EmptyVerseHandler.createEmptyVerseForReversification(getInsertionPoint(document, row.standardRef), row.standardRef.toString()).first
      altered = true
    }



    /**************************************************************************/
    addFootnoteAndSourceVerseDetailsToVerse(sidNode, row)



    /**************************************************************************/
    if (altered)
      SharedData.VersesAmendedByReversification[row.sourceRefAsRefKey] = row.standardRefAsRefKey
    else
      SharedData.VersesToWhichReversificationSimplyAddedNotes[row.sourceRefAsRefKey] = row.standardRefAsRefKey
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
    changeId(sidNode!!, "sid", newId)
    changeId(eidNode!!, "eid", newId)
    addFootnoteAndSourceVerseDetailsToVerse(sidNode, row)



    /**************************************************************************/
    SharedData.VersesAmendedByReversification[row.sourceRefAsRefKey] = row.standardRefAsRefKey
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

     In Psalms, most titles appear at the start of the chapter, although in a
     few cases there are canonical titles at the end as well.  Only the ones
     at the start are of interest here, and these can be recognised because
     earlier processing will have added an attribute _X_location='start'.

     Regrettably there are a lot of different cases which we need to cater for.

     The simplest is where the canonical title is overtly marked as such in the
     raw data (using para:d), and is positioned prior to verse 1.
     Reversification may add annotation to the title in such cases, but it does
     nothing beyond this.

     There is a variant of this which I have seen in a few texts, where the
     canonical title is marked, but is actually positioned _within_ v1 (forming
     just a part of it).  I don't believe the reversification data overtly
     caters for this.  The processing here moves the title so that it falls just
     before v1, but does not annotate it.

     (Presumably there may be other similar variants where the title forms part
     of v1 but is _not_ marked as such; these cannot be handled at all.)

     And then there are the various cases identified by the reversification data
     where we have to do more than just annotate the title -- we may have to move
     the title to a different book and / or create it either from v1 of the raw
     text or jointly from v1 and v2.

     In what I hope is a stunningly brilliant insight, and not just a completely
     stupid idea, it occurred to me that we might temporarily turn existing
     titles into an ordinary verse (or not _quite_ an ordinary one -- I give it
     a special verse number to make sure it can't be confused with anything
     else).  At the time of writing, the special verse number I use for this is
     499.

     Turning it into a verse means that all of the normal processing for verses
     can be applied to it -- annotating, renumbering, etc; whilst the fact that
     it is recognisable means I can turn it back into a canonical title at the
     end of the processing here.

     All of this does rely upon certain changes to the reversification data,
     which are deal with by ReversificationData.  Where the sourceRef contains
     the word 'title', I change it so that it is a reference to v499 of the
     chapter.  And where the standardRef contains 'title', I alter it, either
     just to v0 if the sourceRef is also v0, or else to subverse n of v0, where
     n is the verse number of the sourceRef.  This latter is mainly to cater for
     the situation where the canonical title is made up of v1+v2 of the source:
     I can create v0 as being made up of two subverses, and then amalgamate
     them. */

  /****************************************************************************/
  /* Undoes the sneaky changes used to make psalm titles amenable to 'standard'
     processing. */

  private fun canonicalTitlesPost (document: Document)
  {
    //Dbg.d(document)
    Dom.findNodesByName(document, "_X_chapter"). forEach { canonicalTitlesPostDoChapter(it) }
    //Dbg.d(document)
  }


  /****************************************************************************/
  /* Where titles were _created_ (as opposed to just being carried through from
     the source), they will unfortunately be near the _end_ of the chapter, and
     need to be moved to the beginning.  In addition, they may be made up of
     more than one input verse, in which case the elements need to be
     amalgamated. */

  private fun canonicalTitlesPostDoChapter (chapterNode: Node)
  {
    /**************************************************************************/
    //val dbg = Dbg.dCont(Dom.toString(chapterNode), "51")



    /**************************************************************************/
    val C_Marker = RefBase.C_TitlePseudoVerseNumber.toString()
    val allNodes = Dom.collectNodesInTree(chapterNode)
    val firstSpecialVerseSidIx = allNodes.indexOfFirst { "verse" == Dom.getNodeName(it) && Dom.hasAttribute(it, "sid") && C_Marker in Dom.getAttribute(it, "sid")!! }
    if (-1 == firstSpecialVerseSidIx) return



    /**************************************************************************/
    /* If the original text had a canonical title which has merely been carried
       forward, we'll have a temporary verse marked _TEMP_wasCanonicalTitle
       which contains it.  If this is the case, we simply need to restore it
       to its former glory.  (There will only be one such verse, and it will be
       the first verse in the chapter.) */

    val sidNode = allNodes[firstSpecialVerseSidIx]
    if (Dom.hasAttribute(sidNode, "_TEMP_wasCanonicalTitle"))
    {
      val eid = allNodes.find { "verse" == Dom.getNodeName(it) && Dom.hasAttribute(it, "eid") }!!
      Dom.deleteNode(eid)

      val footnoteNode = sidNode.nextSibling
      val headingNode = sidNode.nextSibling.nextSibling
      Dom.deleteNode(footnoteNode)
      Dom.insertAsFirstChild(headingNode, footnoteNode)
      Dom.deleteNode(sidNode)
      return
    }



    /**************************************************************************/
    /* The more complicated case.  The special verse(s) were generated rather
       than being carried over.  They will currently lie at the _end_ of the
       chapter, and there may be either one or two of them.  If there are two,
       they need to be amalgamated into one, and then the whole thing needs to
       be turned into a canonical title and placed at the front of the chapter.
       And one other thing -- any footnotes within these verses will have a
       char:fr which points to v499, and we need them instead to point to the
       chapter. */

    val chapterNodeSid = Dom.getAttribute(chapterNode, "sid")
    val ownerDocument = chapterNode.ownerDocument
    val lastSpecialVerseEidIx = allNodes.indexOfLast { "verse" == Dom.getNodeName(it) && Dom.hasAttribute(it, "eid") && C_Marker in Dom.getAttribute(it, "eid")!! }

    val topLevelNodes = Dom.pruneToTopLevelOnly(allNodes.subList(firstSpecialVerseSidIx, lastSpecialVerseEidIx + 1))
    topLevelNodes.forEach { Dom.deleteNode(it) } // No need to delete recursively up the tree -- these won't be under anything relevant.

    val canonicalTitle = Dom.createNode(ownerDocument, "<para style='d' _X_revAction='generated' _X_location='start'/>")
    val insertBefore = Dom.findNodeByName(chapterNode, "verse", false)
    Dom.insertNodeBefore(insertBefore!!, canonicalTitle)
    Dom.addChildren(canonicalTitle, topLevelNodes)
    Dom.findNodesByAttributeName(canonicalTitle, "verse", "eid").forEach { Dom.deleteNode(it) }
    Dom.findNodesByName(canonicalTitle, "verse", false).forEach { val x = Dom.createTextNode(ownerDocument, " "); Dom.insertNodeBefore(it, x); Dom.deleteNode(it)}
    Dom.deleteNode(canonicalTitle.firstChild) // That's replaced each verse:sid by a space to act as a separator, and then got rid of the first one.

    Dom.findNodesByAttributeValue(canonicalTitle, "char", "style", "fr").forEach {
      val owner = it.firstChild.textContent
      if (C_Marker in owner)
      {
        Dom.deleteNode(it.firstChild)
        val revisedOwner = Dom.createTextNode(ownerDocument, chapterNodeSid!!)
        it.appendChild(revisedOwner)
      }
    }
  }


  /****************************************************************************/
  /* Changes needed before we can apply reversification. */

  private fun canonicalTitlesPre (document: Document)
  {
    canonicalTitlesPreMoveEmbeddedTitles(document)
    canonicalTitlesPreConvertTitlesToSpecialVerses(document)
  }


  /****************************************************************************/
  /* Encapsulates all canonical headings (para:d) in a sid / eid pair for verse
     0.  This makes canonical headings look like verses, which means I can press
     general processing into use for handling psalm titles. */

  private fun canonicalTitlesPreConvertTitlesToSpecialVerses (document: Document)
  {
    fun encapsulate (heading: Node)
    {
      val chapter = Dom.findAncestorByNodeName(heading, "_X_chapter")!!
      val id = Dom.getAttribute(chapter, "sid") + RefBase.C_TitlePseudoVerseNumber.toString()
      val sidNode = Dom.createNode(document, "<verse sid='$id' _TEMP_wasCanonicalTitle='y'/>")
      val eidNode = Dom.createNode(document, "<verse eid='$id' _TEMP_wasCanonicalTitle='y'/>")
      Dom.insertNodeBefore(heading, sidNode)
      Dom.insertNodeAfter(heading, eidNode)
    }

    Dom.findNodesByAttributeValue(document, "para", "style", "d")
      .filter { "start" == Dom.getAttribute(it, "_X_location") }
      .forEach { encapsulate(it) }
  }


  /****************************************************************************/
  /* If we have para:d's positioned after the v1 sid, reposition them before
     it. */

  private fun canonicalTitlesPreMoveEmbeddedTitles (document: Document)
  {
    var sid: Node? = null

    fun process (node: Node)
    {
      if ("verse" == Dom.getNodeName(node))
      {
        sid = if (Dom.hasAttribute(node, "sid") && 1 == Ref.rdUsx(Dom.getAttribute(node, "sid")!!).getV())
          node
        else
          null
      }
      else if ("para:d" == getExtendedNodeName(node) && null != sid)
      {
        Dom.deleteNode(node)
        Dom.insertNodeBefore(sid!!, node)
        sid = null
      }
    }

    Dom.collectNodesInTree(document).forEach { process(it)}
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
  /* This method has changed a lot over time.

     The fundamental aim was to have it create the content required for a
     reversification footnote; and in particular, that footnote would use, as
     callout, something which gave information about the verse involved in the
     reversification activity.

     A subsequent change to the STEP renderer, however, meant that regardless of
     what callout I requested here, the callout was actually shown as a down-
     arrow, which meant that the information contained within the callout was no
     longer visible.

     In view of this, we took to including the text of the callout actually as
     part of the canonical text.

     This has grown in complexity over time.  The reversification data may now
     call for a) The footnote itself; b) some text which will typically end
     up in brackets and superscripted or subscripted; and c) another piece
     of text which will typically end up in boldface.  Not all of these will
     necessarily be present in all cases.
  */

  private fun addFootnoteAndSourceVerseDetailsToVerse (sidNode: Node, row: ReversificationDataRow)
  {
    /**************************************************************************/
    /* We only want the footnote if we are applying an appropriate level of
       reversification. */

    val wantFootnote: Boolean = m_ReversificationNotesLevel >= row.footnoteLevel
    val calloutDetails = row.calloutDetails
    val document = sidNode.ownerDocument
    val res = Dom.createNode(document, "<_X_reversificationCalloutData/>")



    /**************************************************************************/
    /* This is the 'pukka' callout -- ie the piece of text which you click on in
       order to reveal the footnote.  Originally it was expected to be taken
       from the NoteMarker text in the reversification data.  However, we then
       found that STEP always rendered it as a down-arrow; and latterly DIB has
       decided he wants it that way even if STEP is fixed to display the actual
       callout text we request.  I therefore need to generate a down-arrow here,
       which is what the callout generator gives me. */

     val callout: String = m_FootnoteCalloutGenerator.get()



    /**************************************************************************/
    /* Insert the footnote itself. */

    if (wantFootnote)
    {
      var text  = ReversificationData.getFootnoteA(row)
      val textB = ReversificationData.getFootnoteB(row)
      if (textB.isNotEmpty()) text += " $textB"
      text = text.replace("S3y", "S3Y") // DIB prefers this.
      val ancientVersions = if (m_ReversificationNotesLevel > C_ReversificationNotesLevel_Basic) ReversificationData.getAncientVersions(row) else null
      if (null != ancientVersions) text += " $ancientVersions"
      val noteNode = MiscellaneousUtils.makeFootnote(document, row.standardRefAsRefKey, text, callout)
      res.appendChild(noteNode)
      res.appendChild(Dom.createTextNode(document, " "))



      /************************************************************************/
      /* Bit of rather yucky special case processing.  I have been asked to
         force certain footnotes to the start of the owning verse, even if their
         natural position would be later.  I flag such notes here with a special
         attribute and then move them later. */

      if (row.isInNoTestsSection)
        Dom.setAttribute(noteNode, "_TEMP_moveNoteToStartOfVerse", "y")



      /************************************************************************/
      /* Check if we need the text which will typically be superscripted and
         bracketed. */

      val alternativeRefCollection = calloutDetails.alternativeRefCollection
      if (null != alternativeRefCollection)
      {
        val basicContent = if (calloutDetails.alternativeRefCollectionHasEmbeddedPlusSign)
            alternativeRefCollection.getLowAsRef().toString("a") + Translations.stringFormatWithLookup("V_reversification_alternativeReferenceEmbeddedPlusSign") + alternativeRefCollection.getHighAsRef().toString("a")
          else if (calloutDetails.alternativeRefCollectionHasPrefixPlusSign)
            Translations.stringFormatWithLookup("V_reversification_alternativeReferencePrefixPlusSign") + alternativeRefCollection.toString("a")
          else
            alternativeRefCollection.toString("a")

        val textNode = Dom.createTextNode(document, Translations.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", basicContent))
        val containerNode = Dom.createNode(document, "<_X_reversificationCalloutAlternativeRefCollection/>")
        containerNode.appendChild(textNode)
        res.appendChild(containerNode)
      }
    } // if (wantFootnote)



    /**************************************************************************/
    /* Add the source verse details. */

    val sourceRefCollection = calloutDetails.sourceVerseCollection
    if (null != sourceRefCollection)
    {
      //if (res.hasChildNodes())
      //  res.appendChild(Dom.createTextNode(document, " "))

      val basicContent = sourceRefCollection.toString("a")
      val textNode = Dom.createTextNode(document, Translations.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", basicContent))
      val containerNode = Dom.createNode(document, "<_X_reversificationCalloutSourceRefCollection/>")
      containerNode.appendChild(textNode)
      res.appendChild(containerNode)
    }



    /**************************************************************************/
    Dom.insertNodeAfter(sidNode, res)
  }


  /****************************************************************************/
  private fun changeId (node: Node, sel: String, newVal: String)
  {
    Dom.setAttribute(node, "_X_originalId", Dom.getAttribute(node, sel)!!)
    Dom.setAttribute(node, sel, newVal)
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

  private fun getInsertionPoint (document: Document, sidRef: Ref): Node
  {
    val sidRefKey = sidRef.toRefKey()
    val chapterRef = sidRef.toString("bc")
    var chapterNode = Dom.findNodeByAttributeValue(document, "_X_chapter", "sid", chapterRef)

    if (null == chapterNode) // If we're having to create a new chapter, chapterNode will presently be null.
    {
      val bookNode = Dom.findNodeByName(document, "_X_book")!!
      chapterNode = Dom.createNode(document, "<_X_chapter sid='$chapterRef' _X_generatedReason='chapterCreatedByReversification'/>")
      val verseSidNode = Dom.createNode(document, "<verse _TEMP_dummy='y' sid='$chapterRef:${RefBase.C_BackstopVerseNumber}'/>")
      val verseEidNode = Dom.createNode(document, "<verse _TEMP_dummy='y' eid='$chapterRef:${RefBase.C_BackstopVerseNumber}'/>")
      chapterNode.appendChild(verseSidNode)
      chapterNode.appendChild(verseEidNode)
      bookNode.appendChild(chapterNode)
    }

    val sids = Dom.findNodesByAttributeName(chapterNode, "verse", "sid")
    return sids.find { Ref.rd(Dom.getAttribute(it, "sid")!!).toRefKey() > sidRefKey }!!
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
      if ("verse" != Dom.getNodeName(node)) return
      if (Dom.hasAttribute(node, "sid"))
        res.sidMap[getSelectedIdAsRefKey(node, "sid")] = ix
      else
        res.eidMap[getSelectedIdAsRefKey(node, "eid")] = ix
    }

    res.nodeList = Dom.collectNodesInTree(startNode)
    res.nodeList.indices.forEach { addToMaps(it) }

    return res
  }


  /****************************************************************************/
  private fun getSelectedIdAsRef (node: Node, sel: String): Ref
  {
    return Ref.rdUsx(Dom.getAttribute(node, sel)!!)
  }


  /****************************************************************************/
  private fun getSelectedIdAsRefKey (node: Node, sel: String): RefKey
  {
    return getSelectedIdAsRef(node, sel).toRefKey()
  }


  /****************************************************************************/
  private fun warning (refKey: RefKey, msg: String)
  {
    Logger.warning(refKey, msg)
  }


  /****************************************************************************/
  private var m_Dbg = 0
}