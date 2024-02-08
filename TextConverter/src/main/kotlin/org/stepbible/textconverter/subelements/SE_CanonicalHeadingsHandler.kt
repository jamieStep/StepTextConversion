package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.contains
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Canonicalises canonical titles, if that duplication of terms isn't too
 * confusing.
 *
 * This has nothing to do with reversification (the present processing should
 * be applied regardless, whereas reversification is applied only when strictly
 * necessary).  The sole reason for having this processing is that I've seen at
 * least one text (NIV2011) which seemed to have done some slightly odd things
 * with some of the canonical headings.
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
    deleteVerseMarkersEmbeddedWithinCanonicalHeadings(rootNode)
    processCanonicalTitlesContainingVerses(rootNode)
    processVersesContainingCanonicalTitles(rootNode)
  }


  /****************************************************************************/
  /* In some texts (at the time of writing notably Hab 3 in NIV2011), we may
     have verse markers within canonical headings.  It seems that this is
     commonly handled eg on BibleGateway by _not_ retaining the verse marker
     within the heading, and then having the text start at v2 after the
     heading.  I therefore need to delete verse markers from within
     headings.

     Note that this will have knock-on effects: v1 will no longer be
     accessible, because effectively it will not exist. */

  private fun deleteVerseMarkersEmbeddedWithinCanonicalHeadings (rootNode: Node)
  {
    fun process (heading: Node) = Dom.findNodesByName(heading, m_FileProtocol.tagName_verse(), false).forEach { Dom.deleteNode(it) }
    getCanonicalTitleNodes(rootNode).forEach { process(it) }
  }


  /****************************************************************************/
  private fun getCanonicalTitleNodes (rootNode: Node) = Dom.getNodesInTree(rootNode).filter { m_FileProtocol.isCanonicalTitleNode(it) }


  /****************************************************************************/
  /* We have seen at least one text (NIV2011) in which we have a canonical
     title (para:d) at the start of a chapter, which para:d contains v1.
     osis2mod can't cope with this -- it outputs the verse number in the wrong
     place.

     The solution below may be somewhat half-hearted, in that it addresses the
     particular arrangement we have seen, but there may, I suspect, be others
     it can't cope with ...

     I _was_ going to turn the para:d into char:it and have done with it.
     Unfortunately, osis2mod doesn't like that either -- the verse number still
     ends up in the wrong place: just a _different_ wrong place.

     So, in the end, I have encapsulated every child of the para:d individually
     within char:it (which hopefully means I don't need to worry about the
     precise placement of verse:sid and verse:eid).  I have then turned the
     para:d into _X_contentOnly.  And finally I have bunged a para:p at the
     end of it to ensure this revised stuff, which I want to _look_ like a
     title, comes out on its own line.

     (That description is cast in terms of USX, but the processing below
     should work with OSIS too.) */

  private fun processCanonicalTitlesContainingVerses (rootNode: Node)
  {
    getCanonicalTitleNodes(rootNode)
      .filter { null != Dom.findNodeByName(it, m_FileProtocol.tagName_verse(), false) }
      .forEach { amendCanonicalTitle(it, 'A') }
  }


  /****************************************************************************/
  /* We have seen at least one text (NIV2011) in which we have a verse at the
     end of a chapter which contains a canonical title (para:d).  osis2mod
     can't cope with this -- it outputs the verse number in the wrong place.

     The processing below is probably too fiddly to describe here in detail, and
     even then may well not cope with arrangements which I have not yet seen.

     However, for NIV2011 at least, it seems to do the job. */

  private fun processVersesContainingCanonicalTitles (rootNode: Node)
  {
    /**************************************************************************/
    fun processParaD (paraD: Node, followingNodes: List<Node>)
    {
      amendCanonicalTitle(paraD, 'B')
      IssueAndInformationRecorder.setReformattedTrailingCanonicalTitles()
    }


    /**************************************************************************/
    fun processChapter (chapter: Node)
    {
      val allNodes = Dom.getNodesInTree(chapter)
      var sidIx = -1
      for (i in allNodes.indices)
      {
        val node = allNodes[i]
        if (m_FileProtocol.tagName_verse() == Dom.getNodeName(node))
          sidIx = if (m_FileProtocol.attrName_verseSid() in node) i else -1
        else if (m_FileProtocol.isCanonicalTitleNode(node))
        {
          if (-1 != sidIx)
            processParaD(node, allNodes.subList(i + 1, allNodes.size))
        }
      }
    }



    /**************************************************************************/
    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach { processChapter(it) }
  }


  /****************************************************************************/
  private fun amendCanonicalTitle (titleNode: Node, paraBeforeOrAfter: Char)
  {
     val document = titleNode.ownerDocument
     val children = Dom.getChildren(titleNode)
     children.filter { val x = Dom.getNodeName(it); x != m_FileProtocol.tagName_verse() && x != m_FileProtocol.tagName_note()}
             .forEach {
               val italicsNode = m_FileProtocol.makeItalicsNode(titleNode.ownerDocument)
               Dom.insertNodeBefore(it, italicsNode)
               Dom.deleteNode(it)
               italicsNode.appendChild(it)
             }


     val para = m_FileProtocol.makePlainVanillaParaNode(titleNode.ownerDocument)

     if ('A' == paraBeforeOrAfter)
       Dom.insertNodeAfter(titleNode, para)
     else
       Dom.insertNodeBefore(titleNode, para)
  }
}
