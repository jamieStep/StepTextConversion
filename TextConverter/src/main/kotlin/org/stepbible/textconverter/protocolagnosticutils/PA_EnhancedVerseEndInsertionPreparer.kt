package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.findNodesByName
import org.stepbible.textconverter.applicationspecificutils.*
import org.w3c.dom.Node
import java.util.IdentityHashMap

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
 * The changes I apply here are hopefully not ones which will be visible to
 * the user, although that does rather depend upon how things like paras are
 * formatted, because I change enclosing paras into self-closing.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_EnhancedVerseEndInsertionPreparer: PA()
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
  * Makes mildly unfortunate changes to the OSIS to reduce the chances of
  * hitting cross-boundary markup.
  *
  * @param dataCollection Data to be processed.
  */

  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection)
    Dbg.withReportProgressSub("Restructuring text to make reversification easier.") {
      dataCollection.getRootNodes().forEach {
        changeParaPToMilestone(it)                        // Possibly change para:p to milestone, to make cross-boundary markup less of an issue.
        splitEnclosingSpanTypeNodes(it)                   // If a sid happens to be directly within a char node, split the char node so that the verse can be moved out of it.
      }
    }
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
    /**************************************************************************/
    /* A bit awkward.  I have come across at least one text which has nested
       plain vanilla paragraphs.  If I simply take all of the vanilla paras
       which the document contains as they stand, there is a risk that I
       may process a parent -- which entails moving / recreating children --
       and then find I need to process a child, and the child may have been
       replaced by this point.

       To cater for this, the processing below looks to see if the list of
       plain vanilla nodes contains nodes which appear under other nodes in
       the list, and then arranges to process the most deeply nested first. */

    fun convertToSelfClosingNode (nodes: List<Node>)
    {
      val map = IdentityHashMap<Node, MutableList<Node>>()
      nodes.forEach { map[it] = mutableListOf() }
      nodes.forEach { node ->
        var p = node.parentNode
        while (null != p)
        {
          map[p]?.let { map[node]!!.add(p) }
          p = p.parentNode
        }
      }

      nodes.sortedByDescending { map[it]!!.size } .forEach { Dom.convertToSelfClosingNode(it) }
    }



   /**************************************************************************/
    val vanillaParas = Dom.getAllNodesBelow(rootNode).filter { m_FileProtocol.isPlainVanillaPara(it) }
    convertToSelfClosingNode(vanillaParas)



   /**************************************************************************/
   /* For these, nesting should not be an issue. */

    val poetryParas = Dom.getAllNodesBelow(rootNode).filter { m_FileProtocol.isPoetryPara(it) }
    convertToSelfClosingNode(poetryParas)



   /**************************************************************************/
    if (vanillaParas.isNotEmpty() || poetryParas.isNotEmpty())
      IssueAndInformationRecorder.setForcedSelfClosingParas()
  }


  /****************************************************************************/
  private fun isSpanType (node: Node) = m_FileProtocol.isSpanType(node)


  /****************************************************************************/
  /* I imagine it's unlikely we'll have verses which start inside a char node,
     but if we do I think it's ok to split the char node -- to end it before
     the verse and resume it immediately afterwards. */

  private fun splitEnclosingSpanTypeNodes (rootNode: Node)
  {
    val versesUnderSpan = Dom.findNodesByName(rootNode, m_FileProtocol.tagName_verse(), false).filter { isSpanType(it.parentNode) }
    if (versesUnderSpan.isEmpty()) return

    var ix = 0
    while (true)
    {
      ix += splitEnclosingSpanTypeNode(versesUnderSpan[ix])
      if (ix >= versesUnderSpan.size)
        break
    }

    IssueAndInformationRecorder.setSplitCrossVerseBoundarySpanTypeTags()
  }


  /****************************************************************************/
  /* The verse is a child of a span type, and I assume that a span can be
     split into two, one part before the verse node, then the verse, and then
     the rest.  Doing this means we don't have cross-boundary markup.

     It's always possible that the one span-type container holds more than one
     verse, in which case I don't want to repeat this processing on the
     others.  I therefore return to the caller a count of the number of
     verses within this container, so the caller can avoid calling this method
     to process the others. */

  private fun splitEnclosingSpanTypeNode (verse: Node): Int
  {
    /**************************************************************************/
//    Dbg.d(Dom.toString(verse))
//    if (Dbg.dCont(Dom.toString(verse), "<verse _t='y', _usx='verse', osisID='Ps.51.2', sID='Ps.51.2'>"))
//      Dbg.d(verse.ownerDocument)



    /**************************************************************************/
    val parent = verse.parentNode
    val res = parent.findNodesByName(m_FileProtocol.tagName_verse()).count()
    val siblings = Dom.getSiblings(verse)
    val versePos = Dom.getChildNumber(verse)



   /****************************************************************************/
   /* If the verse is the first child of the parent, we can simply delete the
      verse and then reinsert it before the parent. */

   if (0 == versePos)
   {
     Dom.deleteNode(verse)
     Dom.insertNodeBefore(parent, verse)
     return res
   }



    /****************************************************************************/
    /* If the verse is the last child of the parent, we can delete the verse and
       insert it after the parent. */

    if (siblings.size - 1 == versePos)
    {
      Dom.deleteNode(verse)
      Dom.insertNodeAfter(parent, verse)
      return res
    }



    /****************************************************************************/
    /* The verse is somewhere in the middle of the children of the parents, so we
       have to split things out into a before, the verse, and an after. */

    val pre = parent.cloneNode(true)
    val post = parent.cloneNode(true)
    Dom.getChildren(post).subList(0, versePos + 1).forEach { Dom.deleteNode(it) } // Delete everything prior to and including the verse.
    Dom.getChildren(pre).subList(versePos, siblings.size).forEach { Dom.deleteNode(it) } // Delete the verse and everything after it.
    Dom.insertNodeBefore(parent, pre)
    Dom.deleteNode(verse)
    Dom.insertNodeBefore(parent, verse)
    Dom.insertNodeAfter(parent, post)
    Dom.deleteNode(parent)
    //Dbg.d(parent.ownerDocument)
    return res
  }
}
