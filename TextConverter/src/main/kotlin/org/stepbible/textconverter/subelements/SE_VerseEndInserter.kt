package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node
import java.io.File
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
    //Dbg.d(rootNode.ownerDocument)
    Dbg.reportProgress("Handling cross-boundary markup for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    deleteVerseEnds(rootNode)                                   // Remove any existing verse ends so we can reposition them.
    Utils.insertDummyVerseTags(m_FileProtocol, rootNode)     // Dummy verse end at the end of each chapter, so we always have something to insert before.
    insertVerseEnds(rootNode)                                   // Initial positioning.
    Utils.deleteDummyVerseTags(m_FileProtocol, rootNode) // Get rid of the dummy nodes.
    //Dbg.d(rootNode.ownerDocument) // $$$$$$$$$$
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
//    Dbg.d(sidWhoseEidWeAreCreating)
//    if (Dbg.dCont(Dom.toString(sidWhoseEidWeAreCreating), "Ps.1.1"))
//      Dbg.d(sidWhoseEidWeAreCreating.ownerDocument)

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
//      Dbg.d(if (sidWhoseEidWeAreCreating.isSiblingOf(verseEnd)) "True" else "False")
//      Dbg.d(sidWhoseEidWeAreCreating.parentNode); Dbg.d(verseEnd.parentNode)
      NodeMarker.setCrossBoundaryMarkup(verseEnd)
      IssueAndInformationRecorder.crossVerseBoundaryMarkup("", m_FileProtocol.readRefCollection(id).getFirstAsRefKey(), forceError = false)
      //Dbg.d(sidWhoseEidWeAreCreating.ownerDocument)
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
      ix = thunk.ix
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
    var ix = startingIx
    while (true)
    {
      /************************************************************************/
      val nodeAtWhichToStart = m_AllNodes[--ix]
      val (action, nodeWhichDeterminedAction) = m_FileProtocol.getVerseEndInteraction(nodeAtWhichToStart)
      when (action)
      {
        STOP -> { // We can't pass this node, so we will have to insert after it.
            return InsertVerseEndThunk(action, ix, sidWhoseEidWeAreCreating.isSiblingOf(nodeAtWhichToStart))
        }

        SKIP -> { // We're allowed to skip this node (and possibly an ancestor).  There may be a still better position further to the left, but the caller can worry about that.
          if (sidWhoseEidWeAreCreating.isSiblingOf(nodeAtWhichToStart))
            return InsertVerseEndThunk(action, m_NodeMap[nodeWhichDeterminedAction]!!, sidWhoseEidWeAreCreating.isSiblingOf(nodeWhichDeterminedAction))
        }
      }



      /************************************************************************/
      /* If we're the first child of our parent and haven't yet hit a STOP, it's
         ok to move up so as to start scanning from the parent. */

      if (nodeAtWhichToStart.parentNode.firstChild === nodeAtWhichToStart)
        ix = m_NodeMap[nodeAtWhichToStart.parentNode]!!
    }
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


  /****************************************************************************/
  /* Gets a list and a map of all nodes, and also marks nodes to reflect their
     actual canonicity in this text. */

  private fun markTags (rootNode: Node)
  {
    m_AllNodes = rootNode.getAllNodes()
    //debugPrintOrderedNodeList(m_AllNodes)
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
    val myMarker = if (CALC == fromAbove) m_FileProtocol.getVerseEndInteraction(node).first else fromAbove
    node["_vEnd"] = myMarker.toString()
    Dom.getChildren(node).forEach { markHierarchy(it, myMarker) }
  }


  /****************************************************************************/
  /* Removes all canonicity markers. */

  private fun unmarkTags (rootNode: Node) = rootNode.getAllNodes().forEach { it -= "vEnd" }








  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Debug                                    **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun debugPrintOrderedNodeList (nodes: List<Node>)
  {
    File("C:/Users/Jamie/Desktop/nodeList.txt").bufferedWriter().use { writer ->
      var n = 0
      nodes.forEach { writer.write("${n++}: ${Dom.toString(it)}\n") }
    }
  }
}