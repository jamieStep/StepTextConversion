package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.miscellaneous.Dom
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
    val paras = Dom.getNodesInTree(rootNode).filter { m_FileProtocol.isPlainVanillaPara(it) }
    paras.forEach { Dom.convertToSelfClosingNode(it) }
    if (paras.isNotEmpty())
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
