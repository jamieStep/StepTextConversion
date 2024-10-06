package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.getAllNodesBelow
import org.w3c.dom.Node

object PA_Utils
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun convertToEnclosingTags (parentNode: Node, tagNameToBeProcessed: String)
  {
    /**************************************************************************/
    /* Create a dummy node to make processing more uniform. */

    val dummyNode = Dom.createNode(parentNode.ownerDocument, "<$tagNameToBeProcessed _dummy_='y'/>")
    parentNode.appendChild(dummyNode)



    /**************************************************************************/
    /* Locate the nodes to be processed within the overall collection. */

    val allNodes = Dom.getAllNodesBelow(parentNode)
    val indexes: MutableList<Int> = mutableListOf()
    allNodes.indices
      .filter { tagNameToBeProcessed == Dom.getNodeName(allNodes[it]) }
      .forEach { indexes.add(it) }



    /**************************************************************************/
    /* Turn things into enclosing nodes. */

    for (i in 0..< indexes.size - 1)
    {
      val targetNode = allNodes[indexes[i]]
      val targetNodeParent = Dom.getParent(targetNode)!!
      for (j in indexes[i] + 1 ..< indexes[i + 1])
      {
        val thisNode = allNodes[j]
        if (targetNodeParent == Dom.getParent(thisNode))
        {
          Dom.deleteNode(thisNode)
          targetNode.appendChild(thisNode)
        }
      }
    }



    /**************************************************************************/
    Dom.deleteNode(dummyNode)
  }


  /****************************************************************************/
  /**
   *  Deletes contained whitespace at front or end of node.  Intended mainly
   *  for use with title nodes.
   *
   * @param titleNode Node to process.
   */

  fun deleteLeadingAndTrailingWhitespace (titleNode: Node)
  {
    /**************************************************************************/
    val allNodes = titleNode.getAllNodesBelow()



    /**************************************************************************/
    for (n in allNodes) // Delete leading whitespace within the title.
      if (isExtendedWhitespace(n))
        Dom.deleteNode(n)
      else
        break



    /**************************************************************************/
    for (n in allNodes.reversed()) // Delete trailing whitespace within the title.
      if (isExtendedWhitespace(n))
        Dom.deleteNode(n)
      else
        break
  }


  /****************************************************************************/
  /**
  * Whitespace is represented in a number of different ways.  This checks if
  * a node does indeed represent whitespace.
  *
  * @param node Node to be examined.
  * @return True if whitespace
  */

  fun isExtendedWhitespace (node: Node): Boolean = Dom.isWhitespace(node) || "lb" == Dom.getNodeName(node) || ("l" == Dom.getNodeName(node) && !node.hasChildNodes())







}