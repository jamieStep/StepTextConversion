package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.findNodesByName
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

class SE_EnhancedVerseEndInsertionPreparer (dataCollection: X_DataCollection) : SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun thingsIveDone () = listOf(ProcessRegistry.CrossBoundaryMarkupSimplified)


  /****************************************************************************/
  override fun processRootNodeInternal (rootNode: Node)
  {
    //Dbg.d(rootNode.ownerDocument)
    changeParaPToMilestone(rootNode)                        // Possibly change para:p to milestone, to make cross-boundary markup less of an issue.
    splitEnclosingSpanTypeNodes(rootNode)                   // If a sid happens to be directly within a char node, split the char node so that the verse can be moved out of it.
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
    val vanillaParas = Dom.getNodesInTree(rootNode).filter { m_FileProtocol.isPlainVanillaPara(it) }
    vanillaParas.forEach { Dom.convertToSelfClosingNode(it) }

    val poetryParas = Dom.getNodesInTree(rootNode).filter { m_FileProtocol.isPoetryPara(it) }
    poetryParas.forEach { Dom.convertToSelfClosingNode(it) }

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