package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node
import java.io.File
import java.util.*


/******************************************************************************/
/**
* This class positions verse eids.  The aim is partly to simplify processing
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
* This class assumes earlier processing will have removed any eids which were
* initially present.  This may seem a little counterproductive, but some
* versions of USX don't actually have eids at all, and by removing any existing
* ones (even where we are starting from OSIS), it means that everything is now
* uniform.
*
* I now attempt to position the eids.  The rules are that no canonical text
* can fall outside of a verse.  There may also be some advantage in placing
* *non*-canonical text outside of verses, but I don't worry about that too
* much.
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

class SE_EnhancedVerseEndInserter (dataCollection: X_DataCollection) : SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun thingsIveDone() = listOf(ProcessRegistry.EnhancedVerseEndPositioning)


  /****************************************************************************/
  override fun processRootNodeInternal (rootNode: Node)
  {
    Dbg.reportProgress("Handling cross-boundary markup for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    val chapterNodes = rootNode.findNodesByName(m_FileProtocol.tagName_chapter(), false)
    val dummies = insertDummyVerseTags(m_FileProtocol, chapterNodes) // Dummy verse end at the end of each chapter, so we always have something to insert _before_.
    insertVerseEnds(rootNode, chapterNodes)                          // Initial positioning.
    dummies.forEach(Dom::deleteNode)                                 // Get rid of the dummy nodes.
    //Dbg.d(rootNode.ownerDocument)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Inserts dummy verse sids at the ends of chapters so we always have
   * something we can insert stuff _before_. */

  private fun insertDummyVerseTags (fileProtocol: X_FileProtocol, chapterNodes: List<Node>): List<Node>
  {
    val res: MutableList<Node> = mutableListOf()
    chapterNodes.forEach { chapterNode ->
      val dummySidRef = fileProtocol.readRef(chapterNode[fileProtocol.attrName_chapterSid()]!!)
      dummySidRef.setV(RefBase.C_BackstopVerseNumber)
      val dummySidRefAsString = fileProtocol.refToString(dummySidRef.toRefKey())
      val dummySid = chapterNodes[0].ownerDocument.createNode("<verse ${fileProtocol.attrName_verseSid()}='$dummySidRefAsString'/>")
      NodeMarker.setDummy(dummySid)
      chapterNode.appendChild(dummySid)
      res.add(dummySid)
    }

    return res
  }


  /****************************************************************************/
  /* Adds verse ends for each sid.  We treat each chapter separately.  Within
     this, we treat verses separately according as they are or are not
     associated with a table.

     Where a verse is associated with a table in the sense that the verse owns
     it, we need to place the eid immediately after the table.

     For other sids, we need to do rather more processing. */

  private fun insertVerseEnds (rootNode: Node, chapterNodes: List<Node>)
  {
    initialise(rootNode)

    chapterNodes.forEach { chapterNode ->
      val verses = Dom.findNodesByName(chapterNode, m_FileProtocol.tagName_verse(), false)
      val tables: Map<Int, Node> = Dom.findNodesByName(chapterNode, m_FileProtocol.tagName_table(), false)
        .filter { NodeMarker.hasUniqueId(it) }
        .associateBy { NodeMarker.getUniqueId(it)!!.toInt() }

      verses.filter { "tableElision" == NodeMarker.getElisionType(it) } .forEach {
        val table = tables[NodeMarker.getUniqueId(it)!!.toInt()]!!
        val verseEnd = m_FileProtocol.makeVerseEidNode(it.ownerDocument, it[m_FileProtocol.attrName_verseSid()]!!)
        Dom.insertNodeAfter(table, verseEnd)
      }

      for (i in 0 ..< verses.size - 1) // -1 so we don't process the dummy verse.
        if ("tableElision" != NodeMarker.getElisionType(verses[i]))
          insertVerseEnd(verses[i], verses[i + 1])
    }
  }


  /****************************************************************************/
  /* The aim here is to place the verse-end.  It can't come after the next
     verse-start.  But it does have to come after all canonical nodes, and
     also any pseudo-canonical nodes like notes.  Except ...

     Just very occasionally we also have to cater for the possibility that
     there may be a canonical title tag lying around.  At present I think this
     can only be (in OSIS terms) title:psalm or title:acrostic.  These are
     assumed to belong to the _next_ verse, and therefore we can't go past
     them. */

  private fun insertVerseEnd (sidWhoseEidWeAreCreating: Node, nextVerseSid: Node)
  {
    /**************************************************************************/
    val eid = m_FileProtocol.makeVerseEidNode(sidWhoseEidWeAreCreating.ownerDocument, sidWhoseEidWeAreCreating[m_FileProtocol.attrName_verseSid()]!!)



    /**************************************************************************/
    /* Work forward from the node after the sid looking for the rightmost
       canonical node, and stopping once we hit the next verse sid.  I _think_
       I need look only at text nodes and notes here.  True, things like
       canonical headings also need to remain with verses, but they will
       contain text which itself is recognised here as canonical, and it
       should be enough to recognise the text. */

    var ix = m_NodeMap[sidWhoseEidWeAreCreating]!!
    val ixPastEnd = m_NodeMap[nextVerseSid]!!
    var lastCanonicalIx = -1
    while (++ix != ixPastEnd)
    {
      val node = m_AllNodes[ix]

      if (m_FileProtocol.isCanonicalTitleNode(node) || m_FileProtocol.isAcrosticDivNode(node))
        break

      when (Dom.getNodeName(node))
      {
        "#text" ->
        {
          if (!Dom.isWhitespace(node) && m_FileProtocol.isCanonicalNode(node))
            lastCanonicalIx = ix
        }

        "note" -> // 'note' is usually going to be pseudo canonical (ie it has to remain with the verse content).  But there may also be instances where words are annotated with notes outside of verses.
        {
          if (m_FileProtocol.isCanonicalNode(node))
            lastCanonicalIx = ix
        }
      }
    }



    /**************************************************************************/
    /* If we haven't found any nodes which need to be retained with the verse,
       it must be empty, and so the verse-end can simply be inserted
       immediately after the current sid.  (In point of fact, I don't think
       we should ever see this situation, because 'empty' verses aren't actually
       empty.) */

    if (-1 == lastCanonicalIx)
    {
      Dom.insertNodeAfter(sidWhoseEidWeAreCreating, eid)
      return
    }



    /**************************************************************************/
    /* At this point, we know that lastCanonicalIx is pointing to the rightmost
       node which has to be within the verse.  However, there is still some
       wiggle room -- we can move the node further to the right if needs be
       (admittedly then accepting non-canonical nodes into the verse, which may
       not be entirely idea) until either we hit the next verse or until we
       succeed in finding a position such that inserting the eid after that
       location would make the eid the sibling of the sid.  Note that there's
       no guarantee this will be successful: some structures are such that
       we simply _will_ end up with cross-boundary markup. */

    ix = lastCanonicalIx
    while (true)
    {
      val proposedInsertAfter = m_AllNodes[ix]
      if (Dom.isSiblingOf(sidWhoseEidWeAreCreating, proposedInsertAfter))
      {
        Dom.insertNodeAfter(proposedInsertAfter, eid)
        return
      }

      if (++ix == ixPastEnd)
        break
    }



    /**************************************************************************/
    /* Didn't find a good place to park the eid.  We therefore have to accept
       the present location (and need to mark it as cross-boundary). */

    Dom.insertNodeAfter(m_AllNodes[lastCanonicalIx], eid)
    NodeMarker.setCrossBoundaryMarkup(sidWhoseEidWeAreCreating)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                           Initialisation                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun initialise (rootNode: Node)
  {
    m_NodeMap.clear()
    m_AllNodes = rootNode.getAllNodesBelow()
    m_AllNodes.indices.forEach { m_NodeMap[m_AllNodes[it]] = it }
  }


  /****************************************************************************/
  private lateinit var m_AllNodes: List<Node>
  private val m_NodeMap = IdentityHashMap<Node, Int>()






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