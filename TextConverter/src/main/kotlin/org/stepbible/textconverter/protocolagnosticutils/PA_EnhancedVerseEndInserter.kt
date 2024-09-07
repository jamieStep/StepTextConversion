package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.applicationspecificutils.*
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
* I guarantee (so long as the processing is correct ...) that the result is
* OSIS with eids in positions that give a correct partitioning into verses;
* but I can't always guarantee that I will have succeeded in avoiding cross-
* boundary markup, because some texts are structured in a way which makes that
* impossible.
*
* There is somewhat more drastic processing elsewhere which further reduces the
* changes of getting cross-boundary markup (converting enclosing paras to
* milestones, for instance); and osis2mod also does some of its own fairly
* drastic restructuring.  Even with all of this, though, I have to repeat that
* there is no absolute guarantee of success.
*
* @author ARA "Jamie" Jamieson
*/

object PA_EnhancedVerseEndInserter: PA()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
 
  /****************************************************************************/
  /**
  * Tries to place verse eids in optimal locations.
  * 
  * @param dataCollection Data to be processed.
  */
  
  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection)
    dataCollection.getRootNodes().forEach(::processRootNode)
  }
  
  
  
  
  
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun processRootNode (rootNode: Node)
  {
    Dbg.withReportProgressSub("Handling cross-boundary markup for ${m_FileProtocol.getBookAbbreviation(rootNode)}.") {
      val chapterNodes = rootNode.findNodesByName(m_FileProtocol.tagName_chapter(), false)
      insertVerseEnds(rootNode, chapterNodes) // Initial positioning.
    }
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
    if ("_Dummy" in sidWhoseEidWeAreCreating)
      return



    /**************************************************************************/
    val eid = m_FileProtocol.makeVerseEidNode(sidWhoseEidWeAreCreating.ownerDocument, sidWhoseEidWeAreCreating[m_FileProtocol.attrName_verseSid()]!!)
//    Dbg.d(Dom.toString(eid))
//    if ("Gen.2.1" in Dom.toString(eid))
//      Dbg.d(sidWhoseEidWeAreCreating.ownerDocument)



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

      if (m_FileProtocol.isCanonicalTitleNode(node) || m_FileProtocol.isAcrosticDivNode(node) || m_FileProtocol.isSpeakerNode(node))
        continue

      when (Dom.getNodeName(node))
      {
        "#text" ->
        {
          if (!Dom.isWhitespace(node) && (m_FileProtocol.isCanonicalNode(node) || m_FileProtocol.treatAsCanonicalNodeEvenThoughNot(node)))
            lastCanonicalIx = ix
        }



        // 'note' is usually going to be pseudo canonical (ie it has to remain with the verse content).
        // But there may also be instances where words are annotated with notes outside of verses.
        // Processing should be simplified here, because earlier processing removes the content of
        // note nodes to speed things up.  At this point, therefore, we shouldn't have to worry about
        // the content.  The content is restored later.

        "note" ->
        {
          if (m_FileProtocol.isCanonicalNode(node))
            lastCanonicalIx = ix
        }


        //
        "q" ->
        {
          //if (!node["marker"].isNullOrEmpty())
          //Dbg.d("***" + Dom.toString(node))
          lastCanonicalIx = ix
        }
      } // when
    } // while



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
       node which _has_ to be within the verse.  However, there is still some
       wiggle room (or there is if we are prepared to accept non-canonical
       nodes into the verse if necessary).

       If the proposed eid position would not make the eid into a sibling of
       the sid, we can try positioning the eid after the parent of the
       proposed position, so long as any tags which we have to skip over to
       do that are non-canonical -- and we can repeat this up the tree if
       necessary. */

    ix = lastCanonicalIx
    var proposedInsertAfter = m_AllNodes[ix]
    while (true)
    {
      if (Dom.isSiblingOf(sidWhoseEidWeAreCreating, proposedInsertAfter))
      {
        Dom.insertNodeAfter(proposedInsertAfter, eid)
        return
      }

      if (m_FileProtocol.tagName_chapter() == Dom.getNodeName(proposedInsertAfter)) // No point in even thinking of going above chapter level.
        break

      if (!Dom.allFollowingSiblingsSatisfy(proposedInsertAfter, { n -> !m_FileProtocol.isCanonicalNode(n) } ))
        break

      proposedInsertAfter = proposedInsertAfter.parentNode
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