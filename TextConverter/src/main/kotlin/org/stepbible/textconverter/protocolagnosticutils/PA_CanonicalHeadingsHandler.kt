package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleStructure
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceAbandonRun
import org.stepbible.textconverter.protocolagnosticutils.PA_Utils.deleteLeadingAndTrailingWhitespace
import org.stepbible.textconverter.protocolagnosticutils.PA_Utils.isExtendedWhitespace
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Canonical titles are awkward, because the different versification traditions
 * handle them differently, and also different translators mark them up in
 * different ways.
 *
 * The purpose of the present class is to make changes as necessary in order
 * to ensure both that they end up in a form which reversification processing
 * can handle and -- ultimately -- obviously that they end up looking correct.
 *
 * I won't go into further detail here because the processing currently is still
 * subject to change as we come across new texts which do things in novel ways.
 * The detail appears within the head-of-method comments below.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_CanonicalHeadingsHandler: PA(), ObjectInterface
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
  * Processes all canonical headings.  These appear in some texts in Psalms
  * and (I think) Habakkuk, but the processing here runs over all text looking
  * for them.
  *
  * This present class tidies up the representation side of things, partly for
  * the sake of consistency as an end in its own right, and partly so that
  * reversification processing has something uniform to work with.
  *
  * @param dataCollection
  */

  fun process (dataCollection: X_DataCollection)
  {
    //Dbg.d(dataCollection)
    extractCommonInformation(dataCollection)
    Rpt.reportWithContinuation(level = 1, "Handling canonical headings ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach { rootNode ->
            Rpt.reportBookAsContinuation(m_FileProtocol.getBookAbbreviation(rootNode))
            asyncable { PA_CanonicalHeadingsHandlerPerBook(m_FileProtocol, dataCollection.getBibleStructure()).processRootNode(rootNode) }
          } // forEach
        } // run
      } // Parallel
    } // report
  } // fun
}




/******************************************************************************/
private class PA_CanonicalHeadingsHandlerPerBook (val m_FileProtocol: X_FileProtocol, val m_BibleStructure: BibleStructure)
{
  /****************************************************************************/
  fun processRootNode (rootNode: Node)
  {
    rootNode.findNodesByName("chapter").forEach(::processChapter)
  }


  /****************************************************************************/
  private fun processChapter (chapterNode: Node)
  {
    /**************************************************************************/
    /* If we have nodes which should be treated as a canonical title but which
       are not currently contained within a canonical title node, remedy
       that. */

    val nodesToBeUsedAsCanonicalTitle = m_BibleStructure.getNodeListForCanonicalTitle(m_FileProtocol.getSidAsRefKey(chapterNode))
    if (null != nodesToBeUsedAsCanonicalTitle && null == nodesToBeUsedAsCanonicalTitle.firstOrNull { m_FileProtocol.isCanonicalTitleNode(it) } )
    {
      val titleNode = m_FileProtocol.makeCanonicalTitleNode(chapterNode.ownerDocument)
      Dom.insertNodeBefore(nodesToBeUsedAsCanonicalTitle[0], titleNode)
      Dom.deleteNodes(nodesToBeUsedAsCanonicalTitle)
      Dom.addChildren(titleNode, nodesToBeUsedAsCanonicalTitle)
    }



    /**************************************************************************/
    /* This gives a Pair containing two lists, the first containing all of the
       canonical title details which appear at the start of chapters, and the
       second giving those which appear at the end.  Just to be clear, we are
       looking specifically for things actually marked para:d (in USX) or
       psalm:title (in OSIS); elsewhere canonical titles are taken as any
       canonical text prior to v1, regardless of how that text was marked,
       but here we are concerned with stuff already specifically marked up. */

    val titleNodes = identifyCanonicalTitleLocations(chapterNode.getAllNodesBelow())

    if (titleNodes.first.isNotEmpty())
      processTitlesAtStartOfChapter(titleNodes.first)

    if (titleNodes.second.isNotEmpty())
      processTitlesAtEndOfChapter(titleNodes.second)
  }


  /****************************************************************************/
  /* Marks canonical titles which appear near the start of chapters, since
     these are the only ones we need to work with.  I make the assumption that
     once I hit chapter C_VerseCountThreshold, we're past the start of the
     chapter and therefore need not do anything else. */

  private fun identifyCanonicalTitleLocations (allNodesInChapter: List<Node>): Pair<List<Node>, List<Node>>
  {
    /**************************************************************************/
    val C_VerseCountThreshold = 3
    val titlesAtStart: MutableList<Node> = mutableListOf()
    val titlesAtEnd  : MutableList<Node> = mutableListOf()
    var verseCount = 0



    /**************************************************************************/
    allNodesInChapter.forEach {
      if (m_FileProtocol.tagName_verse() == Dom.getNodeName(it) && m_FileProtocol.attrName_verseSid() in it)
        ++verseCount
      else if (m_FileProtocol.isCanonicalTitleNode(it))
      {
        if (verseCount >= C_VerseCountThreshold)
          titlesAtEnd.add(it)
        else
          titlesAtStart.add(it)
      }
    }



    /**************************************************************************/
    return Pair(titlesAtStart, titlesAtEnd)
  }


  /****************************************************************************/
  /* At the end of the chapter I hope for the best, and simply turn the
     title node into hi:italic. */

  private fun processTitlesAtEndOfChapter (titleNodesInChapter: List<Node>)
  {
    /**************************************************************************/
    val titleNode = titleNodesInChapter[0]
    deleteLeadingAndTrailingWhitespace(titleNode)
    Dom.setNodeName(titleNode, "hi")
    Dom.deleteAllAttributes(titleNode)
    titleNode["type"] = "italic"
    Dom.insertNodeBefore(titleNode, Dom.createNode(titleNode.ownerDocument, "<l level='1'/>")) // Add vertical whitespace before title.
    IssueAndInformationRecorder.setReformattedTrailingCanonicalTitles()



    /**************************************************************************/
    /* This is a hack, and one which doubtless will come back to bite me at some
       point.  The processing here kinda assumes that either the title will
       contain no verse tags, or else that it will contain a single sid / eid
       pair.  I have been dealing with one text -- lin_MNB -- where this is
       indeed the case in the input USX, but by the time we get as far as here,
       the eid has been moved out of the title tag (while the sid remains
       inside it).  I'm therefore going to move any adjacent eid into the
       title. */



    val verseTags = titleNode.findNodesByName("verse")
    when (verseTags.size)
    {
      0 -> return

      1 ->
      {
        val titleSibling = titleNode.nextSibling
        if (null != titleSibling && m_FileProtocol.tagName_verse() == Dom.getNodeName(titleSibling) && m_FileProtocol.attrName_verseEid() in titleSibling)
        {
          Dom.deleteNode(verseTags[0])
          Dom.insertNodeBefore(titleNode, verseTags[0])
          //Dbg.d(titleNode.ownerDocument)
        }
        else
          throw StepExceptionWithoutStackTraceAbandonRun("processTitlesAtEndOfChapter: (A) Invalid number of contained verse tags: ${verseTags.size}.")
      }

      2 ->
      {
        Dom.deleteNode(verseTags[0])
        Dom.insertNodeBefore(titleNode, verseTags[0])
        Dom.deleteNode(verseTags[1])
        Dom.insertNodeAfter(titleNode, verseTags[1])
      }

      else -> throw StepExceptionWithoutStackTraceAbandonRun("processTitlesAtEndOfChapter: (B) Invalid number of contained verse tags: ${verseTags.size}.")
    }
  }


  /****************************************************************************/
  /* This is somewhat tentative.  Also it is being driven by experience, and
     at the time of writing, experience is rather limited -- beyond the fact
     that every single text seems to do something different.

     There are all sorts of other things which will have to be taken into
     account at some point -- the needs of reversification / samification;
     texts which follow other versification schemes; alternative forms of
     markup; etc.  However we have to start somewhere.  Obviously I am hoping
     that this one revised approach will cater for everything, but that
     remains to be seen.

     At present, therefore, I am addressing three different situations within
     GerHFA, and hoping that in fact this will cover all of them:

     At Ps 3 we have (slightly simplified):

       <para style="d">
           <verse number="1" style="v"/>Ein Lied ...
       </para>

    In other words, the title is given by v1, and in the USX v1 is within the
    title tag.



    At Ps 11 we have:

       <para style="d">
         <verse number="1" style="v"/>Von David.
       </para>
       <para style="b"/>
       <para style="q1">Bei dem Herrn ...</para>
       <para style="q2">»Du musst ins Gebirge fliehen! ...</para>

    Here the title covers just the first two words in v1.  The remainder of
    v1 as per the USX is what should actually appear in v1.



    At Ps 51 we have:

       <para style="d">
         <verse number="1" style="v"/>Ein Lied von David.</para>
       <para style="d">
         <verse number="2" style="v"/>Er schrieb es ...</para>

    Here the title covers the first _two_ verses.

    Experience suggests that we cannot use para:d (title:psalm) here: the best
    we can do (in OSIS form) is to convert the tag to hi:italic.  This looks
    roughly similar to psalm:title, except for a slight difference in font size,
    and the use of a serif, rather than a sans serif, font.

    In fact there is a further issue, in that hi:italic cannot contain verse
    tags, so we also need to reorder things so that the hi:italic falls between
    sid and eid, rather than containing them.

    Apart from that, it's just a matter of taking care of vertical line-
    spacing (psalm:title is set off automatically from the rest of the text
    with a newline, but hi:italic is not)

    This may be called with a single title node, or with two (eg in Ps 51,
    where the header appears as the first _two_ verses in some texts, and
    in some cases the markup gives each as a separate header).

    Uh-oh.  FreBDS gives us yet another variant:

    <para style="d">
        <verse number="1" style="v" sid="PSA 18:1"/>Au chef de chœur, ...<verse eid="PSA 18:1"/>
        <verse number="2" style="v" sid="PSA 18:2"/>Il dit ceci</para>
    ... <verse eid="PSA 18:2"/>

    So here we have the whole of v1 and part of v2 contained within a single
    header tag.

  */

  private fun processTitlesAtStartOfChapter (titleNodesInChapter: List<Node>)
  {
    /**************************************************************************/
//    if ("12" in Dom.toString(titleNodesInChapter[0].parentNode) || titleNodesInChapter.size > 1)
//      Dbg.d(titleNodesInChapter[0].ownerDocument)



    /**************************************************************************/
    /* Delete vertical whitespace surrounding each title node.  We have to do
       this as a separate step.  Otherwise, if the present call is handling
       _two_ title:psalms (as may be the case, eg, in Ps 51, where the heading
       sometimes appears as the first two verses), when we process the first
       tag, we'll add vertical whitespace after it, and when we process the
       second we'll delete that vertical whitespace. */

    titleNodesInChapter.forEach { titleNode ->
      while (null != titleNode.previousSibling && isExtendedWhitespace(titleNode.previousSibling))
        Dom.deleteNode(titleNode.previousSibling) // Delete whitespace before title.

      while (null != titleNode.nextSibling && isExtendedWhitespace(titleNode.nextSibling))
        Dom.deleteNode(titleNode.nextSibling) // Delete whitespace after title.
    }



    /**************************************************************************/
    titleNodesInChapter.forEach(::deleteLeadingAndTrailingWhitespace)



    /**************************************************************************/
    /* Turn the header into hi:italic, and add some vertical whitespace. */

    fun rejigTitleNode (titleNode: Node)
    {
      Dom.insertNodeAfter(titleNode, Dom.createNode(titleNode.ownerDocument, "<l level='1'/>")) // Add vertical whitespace after title.
      Dom.setNodeName(titleNode, "hi")
      Dom.deleteAllAttributes(titleNode)
      titleNode["type"] = "italic"
    }



    /**************************************************************************/
    fun firstPartOfVerseOneIsTitle (titleNode: Node, sid: Node)
    {
      Dom.deleteNode(sid)
      Dom.insertNodeBefore(titleNode, sid)
      rejigTitleNode(titleNode)
    }



    /**************************************************************************/
    fun verseOneAndFirstPartOfVerseTwoIsTitle (titleNode: Node, sidA: Node, eidA: Node, sidB: Node)
    {
      val children = Dom.getChildren(titleNode)

      val ixEidA = children.indexOf(eidA)
      val ixSidB = children.indexOf(sidB)

      val contentOfV1 = children.subList(1, ixEidA) // I assume here that sidA is the first node, and therefore don't check for it.  I want only the
      val betweenList = children.subList(ixEidA + 1, ixSidB)
      val contentOfHeaderPortionOfV2 = children.subList(ixSidB + 1, 1 + children.indexOf(titleNode.lastChild))

      val firstContainer  = Dom.createNode(titleNode.ownerDocument, "<hi type='italic'/>")
      val secondContainer = Dom.createNode(titleNode.ownerDocument, "<hi type='italic'/>")

      contentOfV1.forEach                 { Dom.deleteNode(it); firstContainer .appendChild(it) }
      contentOfHeaderPortionOfV2.forEach  { Dom.deleteNode(it); secondContainer.appendChild(it) }
      betweenList.forEach                 { Dom.deleteNode(it) }

      Dom.insertNodeBefore(titleNode, sidA)
      Dom.insertNodeBefore(titleNode, firstContainer)
      Dom.insertNodeBefore(titleNode, eidA)
      Dom.insertNodeAfter(eidA, Dom.createNode(titleNode.ownerDocument, "<l level='1'/>"))

      Dom.insertNodeAfter(titleNode, secondContainer)
      Dom.insertNodeAfter(titleNode, sidB)

      Dom.deleteNode(titleNode)

      Dom.insertNodesAfter(eidA, betweenList)
    }


    /**************************************************************************/
    /* This is almost identical to verseOneAndFirstPartOfVerseTwoIsTitle, except
       for the two lines marked with asterisks. */

    fun verseOneAndTwoAndTitleCoincide (titleNode: Node, sidA: Node, eidA: Node, sidB: Node, eidB: Node)
   {
      val children = Dom.getChildren(titleNode)

      val ixEidA = children.indexOf(eidA)
      val ixSidB = children.indexOf(sidB)

      val contentOfV1 = children.subList(1, ixEidA) // I assume here that sidA is the first node, and therefore don't check for it.  I want only the
      val betweenList = children.subList(ixEidA + 1, ixSidB)
      val contentOfHeaderPortionOfV2 = children.subList(ixSidB + 1, 1 + children.indexOf(eidB)) // ***

      val firstContainer = Dom.createNode(titleNode.ownerDocument, "<hi type='italic'/>")
      val secondContainer = Dom.createNode(titleNode.ownerDocument, "<hi type='italic'/>")

      contentOfV1.forEach                 { Dom.deleteNode(it); firstContainer .appendChild(it) }
      contentOfHeaderPortionOfV2.forEach  { Dom.deleteNode(it); secondContainer.appendChild(it) }
      betweenList.forEach                 { Dom.deleteNode(it) }

      Dom.insertNodeBefore(titleNode, sidA)
      Dom.insertNodeBefore(titleNode, firstContainer)
      Dom.insertNodeBefore(titleNode, eidA)
      Dom.insertNodeAfter(eidA, Dom.createNode(titleNode.ownerDocument, "<l level='1'/>"))

      Dom.insertNodeAfter(titleNode, eidB) // ***
      Dom.insertNodeAfter(titleNode, secondContainer)
      Dom.insertNodeAfter(titleNode, sidB)
      Dom.insertNodeAfter(eidB, Dom.createNode(titleNode.ownerDocument, "<l level='1'/>"))

      Dom.deleteNode(titleNode)

      Dom.insertNodesAfter(eidA, betweenList)
    }




    /**************************************************************************/
    /* The verse equates to the entire content of the header.  In this case, we
       just move the sid and eid outside the title, and then change the header
       to be hi:italic. */

    fun verseOneAndTitleCoincide (titleNode: Node, sid: Node, eid: Node)
    {
      Dom.deleteNode(sid)
      Dom.insertNodeBefore(titleNode, sid)
      Dom.deleteNode(eid)
      Dom.insertNodeAfter(titleNode, eid)
      rejigTitleNode(titleNode)
    }



    /**************************************************************************/
    /* I do make certain assumptions here about the structure being vaguely
       plausible.  Assuming it is, then ...

       - We'll have _one_ verse tag (a sid) inside the title if the title
         comprises some, but not all, of v1.

       - We'll have _two_ verse tags (the sid and eid for v1) inside the title
         if the title corresponds to the whole of v1.

       - We'll have _three_ verse tags (the sid and eid for v1 and the sid for
         v2) if the title contains the whole of v1 and part of v2.

       - We'll have _four_ verse tags (the sid and eid for each of v1 and v2) if
         the title contains the whole of v1 and v2.
     */

//    if ("12" in Dom.toString(titleNodesInChapter[0].parentNode) || titleNodesInChapter.size > 1)
//      Dbg.d(titleNodesInChapter[0].ownerDocument)

    titleNodesInChapter.forEach {
      val verseTagsInTitleNode = it.findNodesByName("verse")
      when (verseTagsInTitleNode.size)
      {
        0 -> { }
        1 -> firstPartOfVerseOneIsTitle(it, verseTagsInTitleNode[0])
        2 -> verseOneAndTitleCoincide(it, verseTagsInTitleNode[0], verseTagsInTitleNode[1])
        3 -> verseOneAndFirstPartOfVerseTwoIsTitle(it, verseTagsInTitleNode[0], verseTagsInTitleNode[1], verseTagsInTitleNode[2])
        4 -> verseOneAndTwoAndTitleCoincide(it, verseTagsInTitleNode[0], verseTagsInTitleNode[1], verseTagsInTitleNode[2], verseTagsInTitleNode[3])
        else ->
        {
          Dbg.d(titleNodesInChapter[0].ownerDocument)
          throw StepExceptionWithStackTraceAbandonRun("processCanonicalHeaders: Bad verse tag count: ${verseTagsInTitleNode.size} at ${Dom.getAncestorNamed(titleNodesInChapter[0], "chapter")!!}.")
        }
      }
    }
  }
}
