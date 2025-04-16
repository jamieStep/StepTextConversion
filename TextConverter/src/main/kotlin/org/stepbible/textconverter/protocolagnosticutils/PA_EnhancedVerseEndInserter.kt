package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.w3c.dom.Node
import java.io.File
import java.util.*
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType


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

object PA_EnhancedVerseEndInserter: PA(), ObjectInterface
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

    if (null != Dom.findNodeByAttributeName(dataCollection.getRootNodes()[0], m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid()))
      return // I assume that if there is at least one verse eid in the first book, then there will be eids throughout.

    Rpt.reportWithContinuation(level = 1, "Handling cross-boundary markup ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach {
            asyncable { PA_EnhancedVerseEndInserterPerBook(m_FileProtocol).processRootNode(it) }
          } // forEach
        } // run
      } // Parallel
    } // reportWithContinuation
  } // fun
}




/******************************************************************************/
private class PA_EnhancedVerseEndInserterPerBook (val m_FileProtocol: X_FileProtocol)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun processRootNode (rootNode: Node)
  {
    Rpt.reportBookAsContinuation(m_FileProtocol.getBookAbbreviation(rootNode))
    val chapterNodes = rootNode.findNodesByName(m_FileProtocol.tagName_chapter(), false)
    insertVerseEnds(rootNode, chapterNodes) // Initial positioning.
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Adds verse ends for each sid.  We treat each chapter separately.  Within
     this, we treat verses separately according as they are or are not
     associated with a table.

     Where a verse is associated with a table in the sense that the verse owns
     it, we need to place the eid immediately after the table.

     For other sids, we need to do rather more processing. */

  private fun insertVerseEnds (rootNode: Node, chapterNodes: List<Node>)
  {
    val dummyVerses: MutableList<Node> = mutableListOf()

    chapterNodes.forEach {
      val dummyVerse = Dom.createNode(it.ownerDocument, "<${m_FileProtocol.tagName_verse()} ${m_FileProtocol.attrName_verseSid()}='Gen.999.999'/>")
      dummyVerses.add(dummyVerse)
      it.appendChild(dummyVerse) // This means we can always insert eids before the next verse.  Without it, the last eid would have no next verse.
    }

    initialise(rootNode)

    chapterNodes.forEach {
      insertVerseEndsForChapter(it)
    }

    dummyVerses.forEach {
      Dom.deleteNode(it)
    }
  }


  /****************************************************************************/
  private fun insertVerseEndsForChapter (chapterNode: Node)
  {
    //val dbg = Dbg.dCont(Dom.toString(chapterNode), "50")
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

//   if (dbg)
//     Dbg.d(chapterNode.ownerDocument)
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
    //val dbg = Dbg.dCont(Dom.toString(sidWhoseEidWeAreCreating), "Num.15.31")



    /**************************************************************************/
    if ("_Dummy" in sidWhoseEidWeAreCreating)
      return



    /**************************************************************************/
    val eid = m_FileProtocol.makeVerseEidNode(sidWhoseEidWeAreCreating.ownerDocument, sidWhoseEidWeAreCreating[m_FileProtocol.attrName_verseSid()]!!)



    /**************************************************************************/
    /* Work backwards from just before the next eid node until we hit a
       canonical text node.  I work backwards from the eid rather than forward
       from the sid because it's likely to be quicker -- most verses are likely
       to end somewhere very close to the eid. */

    val sidIx = m_NodeMap[sidWhoseEidWeAreCreating]!!
    val nextSidIx = sidIx + 1 + m_AllNodes.subList(sidIx + 1, m_AllNodes.size).indexOfFirst { m_FileProtocol.tagName_verse() == Dom.getNodeName(it) }
    var lastCanonicalIx = nextSidIx
    while (true)
    {
      val node = m_AllNodes[--lastCanonicalIx]

      if (node === sidWhoseEidWeAreCreating)
        break //throw StepExceptionWithStackTraceAbandonRun("insertVerseEnd empty verse not expected: ${Dom.toString(sidWhoseEidWeAreCreating)}.")

      val isCanonical = m_FileProtocol.isCanonicalNode(node)
      val isWhitespace = Dom.isWhitespace(node)
      val nodeName = Dom.getNodeName(node)

      if ("#text" == nodeName && !isWhitespace && isCanonical)
        break // We've hit a non-blank canonical text node.  The eid must fall somewhere after this.

      if (isCanonical && !isWhitespace && !node.hasChildNodes()) // This is intended to cater for things like eg Selah, which is canonical, but has no children.
        break                                                    // I _think_ that anything else canonical will contain text, which the previous test will pick up.
    }



    /**************************************************************************/
    fun getLevel (node: Node): Int
    {
      var res = 0
      var n: Node? = node

      while (null != n)
      {
        ++res
        n = n.parentNode
      }

      return res
    }



    /**************************************************************************/
    /* lastCanonicalIx now points at the ... well, the last canonical node.  We
       know that the eid must be inserted somewhere after this, but we still
       have some flexibility.  If the intended eid location would not make the
       sid and eid into siblings, we can move the eid rightwards through its
       siblings and then upwards through its ancestor chain.  However, there is
       no point in doing that if it takes us past the level at which the sid
       lies -- if that happens, then we know that there is actually nowhere we
       can place the eid, so we have to give up. */

    val initialInsertAfterNode = m_AllNodes[lastCanonicalIx]
    var insertAfterNode = m_AllNodes[lastCanonicalIx]
    val sidLevel = getLevel(sidWhoseEidWeAreCreating)
    var insertAfterNodeLevel = getLevel(insertAfterNode)
    while (!Dom.isSiblingOf(insertAfterNode, sidWhoseEidWeAreCreating) && insertAfterNodeLevel >= sidLevel)
    {
      --insertAfterNodeLevel
      insertAfterNode = insertAfterNode.parentNode
    }



    /**************************************************************************/
    /* If we managed to get to a sibling position, then we need to insert after
       insertAfterNode.  If we didn't, then -- so far as I can manage to work
       out -- we might as well simply position the node after the initial
       position. */

    var crossBoundary = false
    if (!Dom.isSiblingOf(insertAfterNode, sidWhoseEidWeAreCreating))
    {
      insertAfterNode = initialInsertAfterNode
      crossBoundary = true
    }



    /**************************************************************************/
    /* Didn't find a good place to park the eid.  We therefore have to accept
       the present location (and need to mark it as cross-boundary). */

    Dom.insertNodeAfter(insertAfterNode, eid)
    if (crossBoundary)
      NodeMarker.setCrossBoundaryMarkup(sidWhoseEidWeAreCreating)
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

  private fun insertVerseEndOLD (sidWhoseEidWeAreCreating: Node, nextVerseSid: Node)
  {
    /**************************************************************************/
    if ("_Dummy" in sidWhoseEidWeAreCreating)
      return



    /**************************************************************************/
    val eid = m_FileProtocol.makeVerseEidNode(sidWhoseEidWeAreCreating.ownerDocument, sidWhoseEidWeAreCreating[m_FileProtocol.attrName_verseSid()]!!)



    /**************************************************************************/
    /* Work forward from the node after the sid looking for the rightmost
       canonical node, and stopping once we hit the next verse sid.

       The aim here is to leave lastCanonicalIx pointing at the last
       _definitely_ canonical node.  This will normally be a text node,
       but it may also be a note node -- note nodes need special processing
       because normally they are pseudo-canonical (ie they need to remain as
       part of their owning verses), but occasionally they may appear outside
       of verses, where they are non-canonical.  (Except that they don't
       actually seem to work outside of verses ...)

       There may still be skippable nodes after the last canonical node --
       empty para nodes and empty poetry nodes and blank text can all be
       treated as either canonical or non-canonical depending upon context.
       We worry about that shortly. */

    var ix = m_NodeMap[sidWhoseEidWeAreCreating]!!
    val ixPastEnd = m_NodeMap[nextVerseSid]!!
    var lastDefinitelyCanonicalIx = -1
    var lastPotentiallyCanonicalIx = -1
    while (++ix != ixPastEnd)
    {
      val node = m_AllNodes[ix]

      if (m_FileProtocol.isCanonicalTitleNode(node) || m_FileProtocol.isAcrosticDivNode(node) || m_FileProtocol.isSpeakerNode(node))
      {
        lastPotentiallyCanonicalIx = ix
        while (Dom.isDescendantOf(m_AllNodes[ix], m_AllNodes[++lastPotentiallyCanonicalIx]))
          ;
        ix = lastPotentiallyCanonicalIx--
        continue
      }

      when (Dom.getNodeName(node))
      {
        "#text" ->
        {
          if (!Dom.isWhitespace(node) && (m_FileProtocol.isCanonicalNode(node) || m_FileProtocol.treatAsCanonicalNodeEvenThoughNot(node)))
            lastDefinitelyCanonicalIx = ix
        }



        // 'note' is usually going to be pseudo canonical (ie it has to remain with the verse content).
        // But there may also be instances where words are annotated with notes outside of verses.
        // Processing should be simplified here, because earlier processing removes the content of
        // note nodes to speed things up.  At this point, therefore, we shouldn't have to worry about
        // the content.  The content is restored later.

        "note" ->
        {
          if (m_FileProtocol.isCanonicalNode(node))
            lastDefinitelyCanonicalIx = ix
        }
      } // when
    } // while



    /**************************************************************************/
    /* If we haven't found any nodes which need to be retained with the verse,
       it must be empty, and so the verse-end can simply be inserted
       immediately after the current sid.  (In point of fact, I don't think
       we should ever see this situation, because 'empty' verses aren't actually
       empty.) */

    if (-1 == lastDefinitelyCanonicalIx)
      throw StepExceptionWithStackTraceAbandonRun("insertVerseEnd unexpected case")




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

    fun couldBeTreatedAsNonCanonical (node: Node): Boolean
    {
      return when (Dom.getNodeName(node))
      {
        "#text" -> Dom.isWhitespace(node)
        "p", "q" -> node.textContent.isBlank()
        else -> false
      }
    }

    ix = lastDefinitelyCanonicalIx
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

      if (!Dom.allFollowingSiblingsSatisfy(proposedInsertAfter, { n -> couldBeTreatedAsNonCanonical(n) } ))
        break

      proposedInsertAfter = proposedInsertAfter.parentNode
    }



    /**************************************************************************/
    /* Didn't find a good place to park the eid.  We therefore have to accept
       the present location (and need to mark it as cross-boundary). */

    Dom.insertNodeAfter(m_AllNodes[lastDefinitelyCanonicalIx], eid)
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
  /**                               Debug                                    **/
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