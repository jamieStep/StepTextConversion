package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Canonicalises canonical titles, if that duplication of terms isn't too
 * confusing.
 *
 * This has nothing to do with reversification (the present processing should
 * be applied regardless, whereas reversification is applied only when strictly
 * necessary).  The sole reason for having this processing is that I've seen
 * canonical titles marked up in various ways, and this complicates matters and
 * sometimes doesn't get rendered well.
 *
 * I've seen the following:
 *
 * - <title>...</title> <v1>...
 *
 * - <title><v1>...</title> <v2>
 *
 * - <title><v1>...<title>... more of v1 ...<v2>
 *
 * - Missing canonical title, followed by v1 containing stuff which should form
 *   the title.  (This one, though, would have to be addressed by
 *   reversification.)
 *
 *
 * The first of these is the only one which really works.  The second and third
 * are rendered wrongly, and the third is also a problem from the point of view
 * of avoiding cross-boundary markup.
 *
 * To address these, I change the <title> into an italic span-type node,
 * preceded and / or followed by an empty para.  Span -type is not a problem
 * from the point of view of cross-boundary markup, because you can split
 * span-type markup so that it ends before the boundary and resumes after it.
 *
 * I also retain a (now empty) title para so that reversification can find it.
 *
 * @author ARA "Jamie" Jamieson
 */

class SE_CanonicalHeadingsHandler (dataCollection: X_DataCollection): SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun process (rootNode: Node)
  {
    Dbg.reportProgress("Handling canonical headings for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    rootNode.findNodesByName(m_FileProtocol.tagName_chapter()).forEach(::processCanonicalTitles)
  }


  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Works out whether we have a canonical title at all, and if so whether it
     lies at the start of the chapter, at the end, or both.  Then arranges for
     the title to be processed. */

  private fun processCanonicalTitles (chapterNode: Node)
  {
    val allNodes = chapterNode.getAllNodes()
    val canonicalTitles = allNodes.filter { m_FileProtocol.isCanonicalTitleNode(it) }

    when (canonicalTitles.size)
    {
      0 -> return

      1 ->
      {
        var hadVerse = false
        for (i in allNodes.indices)
        {
          if (allNodes[i] == canonicalTitles[0]) break
          hadVerse = m_FileProtocol.tagName_verse() == Dom.getNodeName(allNodes[i])
          if (hadVerse) break
        }

        processCanonicalTitle(canonicalTitles[0], if (hadVerse) 'B' else 'A')
      }

      2 ->
      {
        processCanonicalTitle(canonicalTitles[0], 'A')
        processCanonicalTitle(canonicalTitles[1], 'B')
      }
    }
  }


  /****************************************************************************/
  /* Difficult to explain ...

     I have seen at least one text in which we have:

       <title type='psalm><verse 1>Some text</title>Some more text.

    This is a problem a) in terms of avoiding cross-boundary markup, and b)
    in that the 'obvious' solution (of moving the <verse> tag to before the
    <title> isn't great, because you then end up with the verse marker on a
    line by itself.

    So, I'm trying the following:

    - I insert a <p> before or after the <title>.
    - I replace the <title> by <hi type=<italic'>
    - I insert an empty <title> so that reversification knows we had one.

    This gives us the text on a line by itself in italics which, so far as I
    can see, is how canonical headings are rendered at present anyway.

    Of course, the verse is still encapsulated, but it's now in a span-type
    rather than a div-type, and that's easily handled when sorting out
    cross-boundary markup, because you can split span-type markup across a
    verse boundary.

    IMPORTANT: The previous version of this seemed to do something rather
    more complicated, particularly in support of NIV2011.  I couldn't make
    out the detail, and have chosen to go for this simpler option, but I
    guess it may be necessary to revert to the earlier version. */

  private fun processCanonicalTitle (titleNode: Node, paraBeforeOrAfter: Char)
  {
    /**************************************************************************/
    //val dbg = Dbg.dCont(titleNode.textContent, "als er vor seinem Sohn Absalom")
    //if (dbg) Dbg.d(titleNode.ownerDocument)



    /**************************************************************************/
    /* Insert an empty para _after_ the title node at the start of the chapter,
       or _before_ it at the end. */

    val para = m_FileProtocol.makePlainVanillaParaNode(titleNode.ownerDocument)
    if ('A' == paraBeforeOrAfter)
      Dom.insertNodeAfter(titleNode, para)
    else
      Dom.insertNodeAfter(titleNode, para)



    /**************************************************************************/
    /* Reversification needs to know whether we had a title (or it does if the
       title is at the _start_ of the chapter).  To this end, I insert an
       empty title node prior to the genuine article. */

    if ('A' == paraBeforeOrAfter)
    {
      val emptyTitle = Dom.cloneNode(titleNode.ownerDocument, titleNode, deep = false)
      Dom.insertNodeBefore(titleNode, emptyTitle)
      NodeMarker.setDeleteMe(emptyTitle) // Flag the fact that this verse needs to go before we generate the module.
    }



    /**************************************************************************/
    /* Convert the title node into an italic node. */

    Dom.deleteAllAttributes(titleNode)
    if (X_FileProtocol.ProtocolType.OSIS == m_FileProtocol.getProtocolType())
    {
      Dom.setNodeName(titleNode, "hi")
      titleNode["type"] = "italic"
    }
    else
    {
      Dom.setNodeName(titleNode, "char")
      titleNode["style"] = "it"
    }
  }
}









  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Old code                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

//  /****************************************************************************/
//  /* We have seen at least one text (NIV2011) in which we have a canonical
//     title (para:d) at the start of a chapter, which para:d contains v1.
//     osis2mod can't cope with this -- it outputs the verse number in the wrong
//     place.
//
//     The solution below may be somewhat half-hearted, in that it addresses the
//     particular arrangement we have seen, but there may, I suspect, be others
//     it can't cope with ...
//
//     I _was_ going to turn the para:d into char:it and have done with it.
//     Unfortunately, osis2mod doesn't like that either -- the verse number still
//     ends up in the wrong place: just a _different_ wrong place.
//
//     So, in the end, I have encapsulated every child of the para:d individually
//     within char:it (which hopefully means I don't need to worry about the
//     precise placement of verse:sid and verse:eid).  I have then turned the
//     para:d into _X_contentOnly.  And finally I have bunged a para:p at the
//     end of it to ensure this revised stuff, which I want to _look_ like a
//     title, comes out on its own line.
//
//     (That description is cast in terms of USX, but the processing below
//     should work with OSIS too.) */
//
//  private fun processCanonicalTitlesContainingVerses (rootNode: Node)
//  {
//    getCanonicalTitleNodes(rootNode)
//      .filter { null != Dom.findNodeByName(it, m_FileProtocol.tagName_verse(), false) }
//      .forEach { amendCanonicalTitle(it, 'A') }
//  }
//
//
//  /****************************************************************************/
//  /* We have seen at least one text (NIV2011) in which we have a verse at the
//     end of a chapter which contains a canonical title (para:d).  osis2mod
//     can't cope with this -- it outputs the verse number in the wrong place.
//
//     The processing below is probably too fiddly to describe here in detail, and
//     even then may well not cope with arrangements which I have not yet seen.
//
//     However, for NIV2011 at least, it seems to do the job. */
//
//  private fun processVersesContainingCanonicalTitles (rootNode: Node)
//  {
//    /**************************************************************************/
//    fun processParaD (paraD: Node, followingNodes: List<Node>)
//    {
//      amendCanonicalTitle(paraD, 'B')
//      IssueAndInformationRecorder.setReformattedTrailingCanonicalTitles()
//    }
//
//
//    /**************************************************************************/
//    fun processChapter (chapter: Node)
//    {
//      val allNodes = Dom.getNodesInTree(chapter)
//      var sidIx = -1
//      for (i in allNodes.indices)
//      {
//        val node = allNodes[i]
//        if (m_FileProtocol.tagName_verse() == Dom.getNodeName(node))
//          sidIx = if (m_FileProtocol.attrName_verseSid() in node) i else -1
//        else if (m_FileProtocol.isCanonicalTitleNode(node))
//        {
//          if (-1 != sidIx)
//            processParaD(node, allNodes.subList(i + 1, allNodes.size))
//        }
//      }
//    }
//
//
//
//    /**************************************************************************/
//    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach { processChapter(it) }
//  }
//
//
//  /****************************************************************************/
//  private fun amendCanonicalTitle (titleNode: Node, paraBeforeOrAfter: Char)
//  {
//     val children = Dom.getChildren(titleNode)
//     children.filter { val x = Dom.getNodeName(it); x != m_FileProtocol.tagName_verse() && x != m_FileProtocol.tagName_note()}
//             .forEach {
//               val italicsNode = m_FileProtocol.makeItalicsNode(titleNode.ownerDocument)
//               Dom.insertNodeBefore(it, italicsNode)
//               Dom.deleteNode(it)
//               italicsNode.appendChild(it)
//             }
//
//
//     val para = m_FileProtocol.makePlainVanillaParaNode(titleNode.ownerDocument)
//
//     if ('A' == paraBeforeOrAfter)
//       Dom.insertNodeAfter(titleNode, para)
//     else
//       Dom.insertNodeBefore(titleNode, para)
//  }

