package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.utils.X_DataCollection
import org.stepbible.textconverter.utils.NodeMarker
import org.w3c.dom.Node


/******************************************************************************/
/**
* This class deals with verse eids.  The aim is partly to simplify processing
* generally, and partly to reduce the likelihood of cross-boundary markup.
*
* Both USX and OSIS use milestone markup for verses.  (In fact I think OSIS also
* permits enclosing markup, but it deprecates it and I have never actually seen
* it used.)  Milestone markup permits -- and by implication, encourages --
* cross-verse-boundary semantic and formatting markup; and this is a problem
* for osis2mod and indeed for us, because a lot of what we do involves dealing
* with individual verses, which are difficult to excise from context if markup
* may run across their boundary.
*
* This class begins by removing any existing eids.  This may seem a little
* counterproductive, but some versions of USX don't actually have eids at all,
* and by removing any existing ones, it means that everything is now uniform.
*
* I then attempt to position the eids.  The rules are that no canonical text
* can fall outside of a verse, and that as far as possible, *non*-canonical
* material *should* fall outside.
*
* Note that there is no guarantee of 100% success here -- I just do my best.
* There is somewhat more drastic processing elsewhere which further reduces the
* changes of getting cross-boundary markup (converting enclosing paras to
* milestones, for instance); and osis2mod also does some of its own fairly
* drastic restructuring.  Even with all of this, though, I have to repeat that
* there is no absolute guarantee of success.
*
* @author ARA "Jamie" Jamieson
*/

class SE_VerseEndInserter (dataCollection: X_DataCollection) : SE(dataCollection)
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
    deleteVerseEnds(rootNode)             // Remove any existing verse ends so we can reposition them.
    insertDummyVerseMarkers(rootNode)     // Dummy verse end at the end of each chapter, so we always have something to insert before.
    insertVerseEnds(rootNode)             // Initial positioning.
    deleteDummyVerseMarkersEnds(rootNode) // Get rid of the dummy nodes.
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun deleteDummyVerseMarkersEnds (rootNode: Node)
  {
    rootNode.findNodesByName(m_FileProtocol.tagName_verse()).filter { NodeMarker.hasDummy(it) }.forEach(Dom::deleteNode)
  }


  /****************************************************************************/
  /* Remove all existing verse ends, so we can insert them in what we see as
     the optimal position. */

  private fun deleteVerseEnds (rootNode: Node)
  {
    rootNode.findNodesByName(m_FileProtocol.tagName_chapter(), false).forEach { chapterNode ->
      chapterNode.findNodesByAttributeName(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid()).forEach(Dom::deleteNode)
    }
  }


  /****************************************************************************/
  /* Inserts dummy verse sids so that we always have something we can insert
     _before_. */

  private fun insertDummyVerseMarkers (rootNode: Node)
  {
    rootNode.findNodesByName(m_FileProtocol.tagName_chapter(), false).forEach { chapterNode ->
      val dummySidRef = m_FileProtocol.readRef(chapterNode[m_FileProtocol.attrName_chapterSid()]!!)
      dummySidRef.setV(RefBase.C_BackstopVerseNumber)
      val dummySidRefAsString = m_FileProtocol.refToString(dummySidRef.toRefKey())
      val dummySid = rootNode.ownerDocument.add("<verse ${m_FileProtocol.attrName_verseSid()}='$dummySidRefAsString'/>")
      NodeMarker.setDummy(dummySid)
      chapterNode.appendChild(dummySid)
    }
  }


  /****************************************************************************/
  private fun insertVerseEnds (rootNode: Node)
  {
    // Dbg.outputDom(m_Document, "a")
    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach { chapterNode ->
      val verses = Dom.findNodesByName(chapterNode, m_FileProtocol.tagName_verse(), false)
      for (i in 0 ..< verses.size - 1) // -1 so we don't process the dummy verse.
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
}