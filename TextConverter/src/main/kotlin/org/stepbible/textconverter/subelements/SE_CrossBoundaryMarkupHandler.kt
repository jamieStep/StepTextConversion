package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node

/****************************************************************************/
/**
 * Both USX and OSIS permit -- and therefore encourage -- formatting and
 * semantic markup to run across verse boundaries.  This is a problem for any
 * tool like STEPBible which deals, under some circumstances, with individual
 * verses.  And it is also a problem for osis2mod (although here it is a little
 * difficult to determine which constructs are and are not problematical,
 * because osis2mod itself changes certain things -- for example, I believe
 * it converts enclosing paras into self-closing paras, so although paras in
 * theory could result in cross-boundary markup, they will no longer do so).
 *
 * @author ARA "Jamie" Jamieson
 */

class SE_CrossBoundaryMarkupHandler (dataCollection: X_DataCollection) : SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun process (rootNode: Node)
  {
    Dbg.reportProgress("Handling cross-boundary markup for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    changeParaPToMilestone(rootNode)                        // Possibly change para:p to milestone, to make cross-boundary markup less of an issue.
    splitEnclosingSpanTypeNodes(rootNode)                   // If a sid happens to be directly within a char node, split the char node so that the verse can be moved out of it.
    SE_VerseEndInserter(m_DataCollection).process(rootNode) // Position verse ends so as to reduce the chances of cross-boundary markup.
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Plain vanilla paras occur a lot in most texts, and have a habit of running
     across verse boundaries.  An easy way of avoiding this is to convert them
     to self-closing tags, so that the para tag marks the _start_ of the
     paragraph.  This isn't ideal, because it limits what the tag can do for
     you.  As an enclosing tag it can do more than just influence the
     formatting at the start of the paragraph: it can also influence the
     formatting at the end, and can, for instance, also change margins and
     so on.  However, that concern may be academic, because it appears to be
     the case that osis2mod forces things to be self-closing even if you
     haven't done so earlier in the processing.  However, it does make things
     easier if we convert paras to self-closing. */

  private fun changeParaPToMilestone (rootNode: Node)
  {
    val paras = Dom.getNodesInTree(rootNode).filter {m_FileProtocol.isPlainVanillaPara(it) }
    paras.forEach { Dom.convertToSelfClosingNode(it) }
    if (paras.isNotEmpty())
      IssueAndInformationRecorder.setForcedSelfClosingParas()

  }


  /****************************************************************************/
  /* Remove all existing verse ends, so we can insert them in what we see as
     the optimal position. */

  private fun deleteVerseEnds (rootNode: Node)
  {
    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach { chapterNode ->
      Dom.findNodesByAttributeName(chapterNode, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid()).forEach(Dom::deleteNode)
    }
  }


  /****************************************************************************/
  /* We need the 'verses.size - 2' below to avoid processing the final dummy
     verse. */

  private fun insertVerseEnds (rootNode: Node)
  {
    // Dbg.outputDom(m_Document, "a")
    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach { chapterNode ->
      val verses = Dom.findNodesByName(chapterNode, m_FileProtocol.tagName_verse(), false)
      for (i in 0..< verses.size - 1)
        insertVerseEnd(verses[i][m_FileProtocol.attrName_verseSid()]!!, verses[i], verses[i + 1])
    }
  }


  /****************************************************************************/
  /* We have a fair degree of liberty when placing end nodes, so long as all
     canonical text remains within verses.

     We are placing the eid for sid sidWhoseEidWeAreCreating.  nextVerseSid is
     the next sid after that one.  We know that the new eid must be somewhere
     _before_ the latter.

     But we can probably do better than place it _immediately_ before
     nextVerseSid, because we can work leftwards through the siblings of
     nextVerseSid, and we need stop only if we find one which is itself
     canonical or contains canonical material.  If we do find one of these
     'stop' nodes, then the verse-end is inserted after it and that's the end
     of the processing.

     If we end up being able to move past _all_ of the left-hand siblings of
     nextVerseSid, we can move up to the parent of nextVerseSid, and work through
     _it's_ left-hand siblings in the same way ... and so on.

     If we haven't already inserted the verse end, eventually, given that
     chapters are enclosing nodes, we must end up at the same level as
     sidWhoseEidWeAreCreating, or else at the same level as some ancestor of
     sidWhoseEidWeAreCreating.  At this point, we can still carry on moving left,
     though, because we are bound to hit one of three cases: a) a canonical text
     node; b) a node which _contains_ canonical text; or c) an ancestor of
     sidWhoseEidWeAreCreating.

     Cases a) and b) we have already mentioned above in essence -- they
     represent stop nodes, and the new eid needs to be inserted immediately to
     their right.

     We're only going to hit case c) (I think) if this ancestor of
     sidWhoseEidWeAreCreating contains canonical text as well as
     sidWhoseEidWeAreCreating itself.

     In this case, we can now work our way down the tree, tentatively placing
     the eid as the last child of each node, and then moving leftwards over any
     non-canonical nodes.  If at this point, the eid will be a sibling of
     sidWhoseEidWeAreCreating, we can place the eid at this point.  Otherwise we
     can work down into this node and repeat the process -- the main refinement
     being the extent to which we can optimise this process.

     This then leaves only the case where we have been trying to worm our way
     downwards, but have concluded that this isn't going to let us place the eid
     as a sibling of sidWhoseEidWeAreCreating.  There seem to be two obvious
     options here -- place the eid at the _lowest_ place in the hierarchy which
     we have found for it, or place it at the _highest_ (ie adjacent to the
     highest level ancestor of sidWhoseEidWeAreCreating which we found in
     previous processing. */

  private fun insertVerseEnd (id: String, sidWhoseEidWeAreCreating: Node, nextVerseSid: Node)
  {
    /**************************************************************************/
    //Dbg.d("=============================")
    //Dbg.dCont(id, "1KI 7:1-12")
    //Dbg.d(sidWhoseEidWeAreCreating)
    //Dbg.d(nextVerseSid)
    //val dbg = Dbg.dCont(id, "HAB 3:1")



    /**************************************************************************/
    fun flagFailure (a: Node, b:Node)
    {
      NodeMarker.setCrossBoundaryMarkup(a)
      NodeMarker.setCrossBoundaryMarkup(b)
    }



    /**************************************************************************/
    /* Determines whether a given node should be regarded as containing
       canonical text, or being a canonical text node.  Actually, perhaps that
       is slightly misleading.  This method is used only when attempting to
       place a verse eid node, where basically we can move it towards the
       start of the node list so long as any nodes we skip over are definitely
       non-canonical.

       Returns true if ...

       - The node is a non-whitespace text node and is _not_ under a node whose
         contents we know a priori will always be non-canonical.

       - The node contains a verse node.  (This is a late addition -- it caters
         for the situation where we have an empty verse within, say, a para.  We
         want to treat empty verses as being canonical -- or more to the point,
         we can't move the eid for a given verse back past the sid, so we
         therefore also can't traverse anything which contains the sid.)

       - Any text node under the given node is canonical.
     */

    fun containsCanonicalText (node: Node): Boolean
    {
      if (isInherentlyCanonicalTag(node)) return true
      if (Dom.isTextNode(node) && !Dom.isWhitespace(node) && !isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(node)) return true
      if (null != Dom.findNodeByName(node, m_FileProtocol.tagName_verse(), false)) return true
      return Dom.findAllTextNodes(node).filter { !Dom.isWhitespace(it) }.any { !isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(it) }
    }



    /**************************************************************************/
    /* I take whitespace as skippable, plus any empty para, and any kind of tag
       known to be non-canonical, except that I _think_ we want to treat notes
       as not skippable (ie we want to retain them _inside_ verses). */

    fun canBeRegardedAsNonCanonical (node: Node): Boolean
    {
      if (Dom.isWhitespace(node)) return true // You can skip whitespace.
      val nodeName = Dom.getNodeName(node)
      if (m_FileProtocol.isPlainVanillaPara(node) && !node.hasChildNodes()) return true // You can skip empty paras.
      if (m_FileProtocol.tagName_verse() == nodeName) return false // You can't skip verses.
      if (m_FileProtocol.tagName_note()  == nodeName) return false // You can't skip notes.
      if ("#text" == nodeName) return false
      if (isInherentlyNonCanonicalTag(node) || !containsCanonicalText(node)) return true // You can skip non-canonical tags, or any tag which has no canonical text nodes below it.
      return false
    }



    /**************************************************************************/
    val verseEnd = Dom.createNode(sidWhoseEidWeAreCreating.ownerDocument, "<${m_FileProtocol.tagName_verse()} ${m_FileProtocol.attrName_verseEid()}='$id'/>") // The new node.



    /**************************************************************************/
    /* Don't be put off by the name.  This initial setting isn't a putative
       insertion point, but we will progressively refine things. */

    var putativeInsertionPoint = nextVerseSid



    /**************************************************************************/
    /* For the remaining discussion, let's simplify the job of isSkippable, and
       imagine it simply looks for a node which is inherently canonical, which
       is of interest, because we can't place the eid before canonical text.
       So we start off by running left across the siblings looking for such a
       node.  If we find one, then that's the place of interest (but will need
       further refinement).  If we don't find one (ie there are no blockages
       left of where we are), then we move up a level, and work left from that
       point, and so on. */

    while (true)
    {
      val p = Dom.getPreviousSiblingNotSatisfying(putativeInsertionPoint, ::canBeRegardedAsNonCanonical)

      if (null != p) // We've found a place we can't go beyond.
      {
        putativeInsertionPoint = p
        break
      }

      putativeInsertionPoint = putativeInsertionPoint.parentNode
    }



    /**************************************************************************/
    /* putativeInsertionPoint is now pointing at a node which we can't skip
       _past_, but we may be able to work our way down _into_ it, so long we
       stay to the right of any canonical content.  However, given that the
       whole point of what we're doing here is to try and get the sid and eid
       as siblings, there's no point in trying this at all unless the sid is a
       descendant of the putative insertion point. */

   if (Dom.hasAsAncestor(sidWhoseEidWeAreCreating, putativeInsertionPoint))
   {
     var improvedInsertionPoint = putativeInsertionPoint
     val dummyNode = Dom.createNode(sidWhoseEidWeAreCreating.ownerDocument, "<TEMP/>")
     while (true)
     {
       if (Dom.isSiblingOf(improvedInsertionPoint, sidWhoseEidWeAreCreating)) break
       if (isInherentlyNonCanonicalTag(improvedInsertionPoint)) break
       if (!improvedInsertionPoint.hasChildNodes()) break
       improvedInsertionPoint.appendChild(dummyNode)
       val p = Dom.getPreviousSiblingNotSatisfying(improvedInsertionPoint.lastChild, ::canBeRegardedAsNonCanonical) ?: throw StepException("!!!!")
       Dom.deleteNode(dummyNode)
       improvedInsertionPoint = p
     }

     if (Dom.isSiblingOf(improvedInsertionPoint, sidWhoseEidWeAreCreating))
     {
       Dom.insertNodeAfter(improvedInsertionPoint, verseEnd)
       return
     }
   }



    /**************************************************************************/
    /* Nowhere good to put it, so may as well leave it at the top of the
       structure until experience teaches us there's something better we can
       do. */

    if (Dom.hasAsAncestor(nextVerseSid, putativeInsertionPoint))
      Dom.insertNodeBefore(putativeInsertionPoint, verseEnd)
    else
      Dom.insertNodeAfter(putativeInsertionPoint, verseEnd)

    if (!Dom.isSiblingOf(sidWhoseEidWeAreCreating, verseEnd))
      flagFailure(sidWhoseEidWeAreCreating, verseEnd)
  }


  /****************************************************************************/
  private fun isInherentlyCanonicalTag (node: Node) = m_FileProtocol.isInherentlyCanonicalTag(node)
  private fun isInherentlyNonCanonicalTag (node: Node) = m_FileProtocol.isInherentlyNonCanonicalTag(node)
  private fun isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag (node: Node) = m_FileProtocol.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(node)
  private fun isSpanType (node: Node) = m_FileProtocol.isSpanType(node)


  /****************************************************************************/
  /* I imagine it's unlikely we'll have verses which start inside a char node,
     but if we do I think it's ok to split the char node -- to end it before
     the verse and resume it immediately afterwards. */

  private fun splitEnclosingSpanTypeNodes (rootNode: Node)
  {
    var doneSomething = false
    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_verse(), false).filter { isSpanType(it.parentNode) } .forEach { splitEnclosingSpanTypeNode(it); doneSomething = true }
    if (doneSomething) IssueAndInformationRecorder.setSplitCrossVerseBoundarySpanTypeTags()
  }


  /****************************************************************************/
  private fun splitEnclosingSpanTypeNode (verse: Node)
  {
    /**************************************************************************/
    val parent = verse.parentNode
    val siblings = Dom.getSiblings(verse)
    val versePos = Dom.getChildNumber(verse)



    /****************************************************************************/
   if (0 == versePos)
   {
     Dom.deleteNode(verse)
     Dom.insertNodeBefore(parent, verse)
     return
   }



    /****************************************************************************/
    if (siblings.size - 1 == versePos)
    {
      Dom.deleteNode(verse)
      Dom.insertNodeAfter(parent, verse)
      return
    }



    /****************************************************************************/
    val pre = parent.cloneNode(true)
    val post = parent.cloneNode(true)
    Dom.getChildren(post).subList(0, versePos).forEach { Dom.deleteNode(it) }
    Dom.getChildren(pre).subList(versePos, siblings.size).forEach { Dom.deleteNode(it) }
    Dom.insertNodeBefore(parent, pre)
    Dom.deleteNode(verse)
    Dom.insertNodeBefore(parent, verse)
    Dom.insertNodeAfter(parent, post)
    Dom.deleteNode(parent)
  }
}
