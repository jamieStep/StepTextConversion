package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node
import java.util.*


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
    deleteVerseEnds(rootNode)                                   // Remove any existing verse ends so we can reposition them.
    Utils.insertDummyVerseTags(m_FileProtocol, rootNode)     // Dummy verse end at the end of each chapter, so we always have something to insert before.
    insertVerseEnds(rootNode)                                   // Initial positioning.
    Utils.deleteDummyVerseTags(m_FileProtocol, rootNode) // Get rid of the dummy nodes.
    Dbg.d(rootNode.ownerDocument) // $$$$$$$$$$
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

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
  private fun insertVerseEnds (rootNode: Node)
  {
    // Dbg.outputDom(m_Document)
    markTags(rootNode) // $$$
    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_chapter(), false).forEach { chapterNode ->
      val verses = Dom.findNodesByName(chapterNode, m_FileProtocol.tagName_verse(), false)
      for (i in 0 ..< verses.size - 1) // -1 so we don't process the dummy verse.
        insertVerseEnd(verses[i][m_FileProtocol.attrName_verseSid()]!!, verses[i], verses[i + 1])
    }

    unmarkTags(rootNode) // $$$
 }






  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            New version                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* To swap back to the old version, rename insertVerseEnd / insertVerseEnds
     so that the earlier processing won't pick them up, and rename the
     corresponding functions in the 'Old version' section so they _will_ be
     picked up. */

  /****************************************************************************/
  /* Sure hope this works because it's about the twentieth attempt at doing
     this in a maintainable and acceptably fast way ...

     We are concerned here with placing the eids for verses which lack them (and
     if this present code is being called, we assume that eids are lacking
     completely).

     We _could_ simply place the eid for verse n immediately before the sid for
     verse n + 1.  However, this may lead to cross-boundary markup -- markup
     which possibly we could avoid if we were to make use of the available
     leeway to place the eid in a slightly better place.

     In fact, we can place the eid anywhere we like so long as no canonical text
     ends up outside verses.  The approach I have adopted is to start with the
     eid immediately before the next sid, and then move it 'leftwards' towards
     the current sid until an appropriate position is located (I will define
     'appropriate' shortly).

     In support of this, I use two data structures.  The most important one is a
     list of all nodes within the book currently being processed.  And the other
     is a map which relates nodes to their index into this list.

     The vital thing to note here is that the _list_ is in 'pre-order'.  In other
     words, if a node has children, the node appears in the list first,
     _followed_ by its children.  If, say, the first of the children -- X, say --
     itself has children, then X appears followed by its children before you get
     X's sibling ... and so on.

     My assertion is that moving right to left in this list looking for an
     insertion point automatically traverses all potential locations in the
     correct order: I simply move leftwards until I find a node which I cannot
     traverse (because to do so would place canonical text outside of the verse)
     or until I hit a location which is a sibling of the sid I am processing.

     In fact, if I hit a sibling, I continue moving left to see if I can get any
     better siblings.

     As regards assessing whether I should stop at a particular location or not,
     some nodes have predefined characteristics.  For example, I can always skip
     over a nn-canonical title node, because I can be sure that won't contain
     any canonical text.  I can also always skip over empty text nodes and
     comment nodes because it doesn't matter where they come in relation to
     verse ends.  On the other hand I can't skip over, say divineName, because
     that always contains canonical text.  The one oddity is footnote/cross-ref.
     These don't contain canonical text, but nonetheless I don't want to skip
     over them because I want them to remain part of the verse.

     If I come across an indeterminate node, I determine its canonicity by
     working up its ancestor hierarchy until I find something whose canonicity
     is known.  If I get up as far as a chapter node, I assume the element is
     canonical.

     Note that in theory I could address this either by moving the eid leftwards
     as just discussed, or the sid rightwards, or both.  I've opted to move the
     eid leftwards partly because I suspect it will have to move less and will
     therefore be quicker to process, and partly because doing this has little
     impact upon the appearance of the rendered text.  Moving the sid rightwards
     is more likely to change the appearance.

     Note also that there is no guarantee this will work: there are some
     arrangements of text where cross-boundary markup simply cannot be avoided.
 */

  private fun insertVerseEnd (id: String, sidWhoseEidWeAreCreating: Node, nextVerseSid: Node)
  {
    Dbg.dCont(id, "Ps.1.3")
    val verseEnd = m_FileProtocol.makeVerseEidNode(sidWhoseEidWeAreCreating.ownerDocument, id)
    val thunk = insertVerseEnd_locatePositionForEid(sidWhoseEidWeAreCreating, nextVerseSid)
    val insertPos = m_AllNodes[thunk.ix]
    when (thunk.action)
    {
      SKIP -> Dom.insertNodeBefore(insertPos, verseEnd)
      STOP -> Dom.insertNodeAfter(insertPos, verseEnd)
    }
    if (!thunk.isSiblingOfSid)
    {
      NodeMarker.setCrossBoundaryMarkup(verseEnd)
      IssueAndInformationRecorder.crossVerseBoundaryMarkup("", m_FileProtocol.readRefCollection(id).getFirstAsRefKey(), forceError = false)
    }
  }


  /****************************************************************************/
  /* Works up the hierarchy looking for a definitive assessment of whether this
     node can be skipped or not.

     STOP: If the caller chooses to work with the node which gave rise to this
           result, the eid should be inserted _after_ thunk.ix.  No more
           searches should be carried out after receiving this result.

     SKIP: If the caller chooses to work with the node which gave rise to this
           result, the eid should be inserted _before_ thunk.ix.  If more
           searches are carried out, the search should resume from the node
           _before_ thunk.ix.
     */

  private fun insertVerseEnd_getAction (node: Node): InsertVerseEndThunk
  {
    var p = node

    while (true)
    {
      /************************************************************************/
      /* Deal with text and comment nodes first.  (We'll hit this, if at all,
         only on the first iteration of this loop.  After that we'll be working
         up the hierarchy, and text nodes and comment nodes don't feature as
         the parents of anything.) */

      val nodeName = Dom.getNodeName(p)
      if ('#' == nodeName[0])
      {
        when (nodeName[1])
        {
          'c' -> return InsertVerseEndThunk(SKIP, m_NodeMap[p]!!) // Can always skip comments.

          't' -> // Can skip whitespace.
          {
            if (p.textContent.trim().isEmpty())
              return InsertVerseEndThunk(SKIP, m_NodeMap[p]!!) // Can always skip whitespace.
            else
              p = p.parentNode // Will need to assess based upon parent node.
          }
        } // when
      }



      /************************************************************************/
      /* Anything other than text and comment. */

      val thisAction = m_FileProtocol.getVerseEndInteraction(p)

      when (thisAction)
      {
        // Say we can skip, and give the index of the node which told us so,
        // because we can skip directly to a point before that one, even if
        // it's higher up the hierarchy.
        SKIP -> return InsertVerseEndThunk(SKIP, m_NodeMap[p]!!)


        // Say we have to stop, and return the original node.  The verse-end
        // will have to be inserted immediately after that.
        STOP -> return InsertVerseEndThunk(STOP, m_NodeMap[node]!!)


        OOPS -> throw StepException("Uncatered for node when placing verse ends: $p")
      }


      p = p.parentNode
    }
  }


  /****************************************************************************/
  /* Looks leftwards for either a STOP or a location which is sibling to the
     sid. */

  private fun insertVerseEnd_locatePositionForEid (sidWhoseEidWeAreCreating: Node, nextVerseSid: Node): InsertVerseEndThunk
  {
    /**************************************************************************/
    var ix = m_NodeMap[nextVerseSid]!! // Start looking from before the next sid.
    var siblingThunk: InsertVerseEndThunk? = null
    var stopThunk: InsertVerseEndThunk? = null



    /**************************************************************************/
    while (true)
    {
      val thunk = insertVerseEnd_skipBackToSiblingOrStop(sidWhoseEidWeAreCreating, ix) // Find sibling or STOP.

      if (STOP == thunk.action)
      {
        if (thunk.isSiblingOfSid) // May be both STOP _and_ a sibling (I think ...)
          siblingThunk = thunk
        else
          stopThunk = thunk
        break
      }

      siblingThunk = thunk // Must be a sibling, and must be better than any previous one.  Save this and move back.
      ix = thunk.ix - 1
    }



    /**************************************************************************/
    return siblingThunk ?: stopThunk!!
  }


  /****************************************************************************/
  /* Looks back starting at the node before that passed as argument until it
     finds a node past which the end node we are trying to insert cannot go,
     or until it finds a sibling of the sid, whichever happens first. */

  private fun insertVerseEnd_skipBackToSiblingOrStop (sidWhoseEidWeAreCreating: Node, startingIx: Int): InsertVerseEndThunk
  {
    /**************************************************************************/
    var ix = startingIx
    var thunk: InsertVerseEndThunk



    /**************************************************************************/
    while (true)
    {
      thunk = insertVerseEnd_getAction(m_AllNodes[--ix])

      // If this is returned, the caller should not look further; and if the
      // caller accepts this option, the eid should be placed _after_ this node.
      if (STOP == thunk.action) // If this output is accepted, have to insert _after_ thunk.ix.
        break


      thunk.isSiblingOfSid = sidWhoseEidWeAreCreating.isSiblingOf(m_AllNodes[thunk.ix])
      if (thunk.isSiblingOfSid)
        break

      ix = thunk.ix
    }


    /**************************************************************************/
    return thunk
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                      Markers for new version                           **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private data class InsertVerseEndThunk (var action: Char, var ix: Int, var isSiblingOfSid: Boolean = false)


  /****************************************************************************/
  private lateinit var m_AllNodes: List<Node>
  private val m_NodeMap = IdentityHashMap<Node, Int>()


  /****************************************************************************/
  /* We use the canonicity of nodes to determine whether we can skip over them
     when placing verse ends.  The canonicity values are a bit confusing in this
     context, so I define some better names for them here. */

  private val STOP = 'Y'
  private val SKIP = 'N'
  private val CALC = '?'
  private val OOPS = 'X'


  /****************************************************************************/
  /* Gets a list and a map of all nodes, and also marks nodes to reflect their
     actual canonicity in this text. */

  private fun markTags (rootNode: Node)
  {
    m_AllNodes = rootNode.getAllNodes()
    m_AllNodes.indices.forEach { m_NodeMap[m_AllNodes[it]] = it }
    m_AllNodes.filter { m_FileProtocol.tagName_chapter() == Dom.getNodeName(it) } .forEach {
      it["_vEnd"] = STOP.toString() // If we reach chapter level with no firm decision as to canonicity, we must be dealing with canonical text, and can't skip it.
      markHierarchy(it, CALC)
    }
  }


  /****************************************************************************/
  /* Marks a node and its children with their actual canonicity in this text. */

  private fun markHierarchy (node: Node, fromAbove: Char)
  {
    if (Dom.getNodeName(node).startsWith('#')) return // Can't mark text or comment nodes.
    val myMarker = if (CALC == fromAbove) m_FileProtocol.getVerseEndInteraction(node) else fromAbove
    node["_vEnd"] = myMarker.toString()
    Dom.getChildren(node).forEach { markHierarchy(it, myMarker) }
  }


  /****************************************************************************/
  /* Removes all canonicity markers. */

  private fun unmarkTags (rootNode: Node) = rootNode.getAllNodes().forEach { it -= "vEnd" }








  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Old version                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* See discussion at 'New version'. */

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

  private fun insertVerseEndOld (id: String, sidWhoseEidWeAreCreating: Node, nextVerseSid: Node)
  {
    /**************************************************************************/
    //Dbg.d("=============================")
    //Dbg.dCont(id, "1KI 7:1-12")
    //Dbg.d(sidWhoseEidWeAreCreating)
    //Dbg.d(nextVerseSid)
    //val dbg = Dbg.dCont(id, "HAB 3:1")



    /**************************************************************************/
    val verseEnd = sidWhoseEidWeAreCreating.ownerDocument.createNode("<${m_FileProtocol.tagName_verse()} ${m_FileProtocol.attrName_verseEid()}='$id'/>") // The new node.



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
      val p = Dom.getPreviousSiblingNotSatisfying(putativeInsertionPoint, ::insertVerseEnd_canBeRegardedAsNonCanonical)

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
       val p = Dom.getPreviousSiblingNotSatisfying(improvedInsertionPoint.lastChild, ::insertVerseEnd_canBeRegardedAsNonCanonical) ?: throw StepException("!!!!")
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
      insertVerseEnd_flagFailure(sidWhoseEidWeAreCreating, verseEnd)
  }


  /****************************************************************************/
  fun insertVerseEnd_flagFailure (a: Node, b:Node)
  {
    NodeMarker.setCrossBoundaryMarkup(a)
    NodeMarker.setCrossBoundaryMarkup(b)
  }


  /****************************************************************************/
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

  fun insertVerseEnd_containsCanonicalText (node: Node): Boolean
  {
    if (isInherentlyCanonicalTag(node)) return true
    if (Dom.isTextNode(node) && !Dom.isWhitespace(node) && !isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(node)) return true
    if (null != Dom.findNodeByName(node, m_FileProtocol.tagName_verse(), false)) return true
    return Dom.findAllTextNodes(node).filter { !Dom.isWhitespace(it) }.any { !isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(it) }
  }



  /****************************************************************************/
  /* I take whitespace as skippable, plus any empty para, and any kind of tag
     known to be non-canonical, except that I _think_ we want to treat notes
     as not skippable (ie we want to retain them _inside_ verses). */

  fun insertVerseEnd_canBeRegardedAsNonCanonical (node: Node): Boolean
  {
    if (Dom.isWhitespace(node)) return true // You can skip whitespace.
    if (m_FileProtocol.isPlainVanillaPara(node) && !node.hasChildNodes()) return true // You can skip empty paras.
    val nodeName = Dom.getNodeName(node)
    if (m_FileProtocol.tagName_verse() == nodeName) return false // You can't skip verses.
    if (m_FileProtocol.tagName_note()  == nodeName) return false // You can't skip notes.
    if ("#text" == nodeName) return false // You can't skip non-blank text.
    if (isInherentlyNonCanonicalTag(node) || !insertVerseEnd_containsCanonicalText(node)) return true // You can skip non-canonical tags, or any tag which has no canonical text nodes below it.
    return false
  }



  /****************************************************************************/
  /* I take whitespace as skippable, plus any empty para, and any kind of tag
     known to be non-canonical, except that I _think_ we want to treat notes
     as not skippable (ie we want to retain them _inside_ verses). */

  fun insertVerseEnd_canBeRegardedAsNonCanonicalOld (node: Node): Boolean
  {
    if (Dom.isWhitespace(node)) return true // You can skip whitespace.
    if (m_FileProtocol.isPlainVanillaPara(node) && !node.hasChildNodes()) return true // You can skip empty paras.
    val nodeName = Dom.getNodeName(node)
    if (m_FileProtocol.tagName_verse() == nodeName) return false // You can't skip verses.
    if (m_FileProtocol.tagName_note()  == nodeName) return false // You can't skip notes.
    if ("#text" == nodeName) return false // You can't skip non-blank text.
    if (isInherentlyNonCanonicalTag(node) || !insertVerseEnd_containsCanonicalText(node)) return true // You can skip non-canonical tags, or any tag which has no canonical text nodes below it.
    return false
  }



  /****************************************************************************/
  private fun isInherentlyCanonicalTag (node: Node) = m_FileProtocol.isInherentlyCanonicalTag(node)
  private fun isInherentlyNonCanonicalTag (node: Node) = m_FileProtocol.isInherentlyNonCanonicalTag(node)
  private fun isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag (node: Node) = m_FileProtocol.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(node)
}