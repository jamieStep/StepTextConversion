package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.applicationspecificutils.Original
import org.stepbible.textconverter.applicationspecificutils.Revised
import org.stepbible.textconverter.applicationspecificutils.X_FileProtocol
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
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


  /****************************************************************************/
  /**
  * Looks for attributes containing references which match a given pattern,
  * and modifies them.  For example, you can look for all attributes which
  * contain references having a given book and give them a new book; all
  * attributes having a given book/chapter combination and give them a new
  * book/chapter; etc.
  *
  * @param fileProtocol Lets the method work out whether we're dealing with
  *   USX or OSIS.
  * @param rootNode The root node below which nodes are processed.
  * @param tagName Name of nodes to be examined.
  * @param attributeName: The attribute to be updated.
  * @param mappings Keyed on *old* refKey; value is *new* refKey.
  * @param mask b -> match on book, replace book; bc -> match on book+chapter,
  *   replace book+chapter; etc.  You can even give bcvs, although how useful
  *   that is, I'm not sure.
  */

  fun modifyReferences (fileProtocol: X_FileProtocol, rootNode: Node, tagName: String, attributeName: String, mappings: Map<Original<RefKey>, Revised<RefKey>>, mask: String)
  {
    /**************************************************************************/
    val getComparisonRefKey: (RefKey) -> RefKey = when (mask)
    {
      "b"    -> { refKey -> Ref.clearC(Ref.clearV(Ref.clearS(refKey))) }
      "bc"   -> { refKey -> Ref.clearV(Ref.clearS(refKey)) }
      "bcv"  -> { refKey -> Ref.clearS(refKey) }
      "bcvs" -> { refKey -> refKey }
      else -> throw StepExceptionWithStackTraceAbandonRun("Invalid mask in PA_ElementArchiverReferenceModifier: $mask")
    }




    /**************************************************************************/
    val reviseKey: (RefKey, RefKey) -> RefKey = when (mask)
    {
      "b"    -> { refKey: RefKey, revisionKey: RefKey -> Ref.setB(refKey, Ref.getB(revisionKey)) }
      "bc"   -> { refKey: RefKey, revisionKey: RefKey -> Ref.setB(Ref.setC(refKey, Ref.getC(revisionKey)), Ref.getB(revisionKey)) }
      "bcv"  -> { refKey: RefKey, revisionKey: RefKey -> Ref.setB(Ref.setC(refKey, Ref.getC(Ref.setV(refKey, Ref.getV(revisionKey)))), Ref.getB(revisionKey)) }
      "bcvs" -> { _: RefKey, revisionKey: RefKey -> revisionKey }
      else -> throw StepExceptionWithStackTraceAbandonRun("Invalid mask in PA_ElementArchiverReferenceModifier: $mask")
    }



    /**************************************************************************/
    val refNodes = rootNode.findNodesByName(tagName)
    val refRanges = refNodes.filter { fileProtocol.readRefCollection(it[attributeName]!!).getElements()[0] is RefRange } .map { fileProtocol.readRefCollection(it[attributeName]!!).getElements()[0] as RefRange }
    val comparisonValuesLow  = refRanges.map { getComparisonRefKey(it.getLowAsRefKey ()) }
    val comparisonValuesHigh = refRanges.map { getComparisonRefKey(it.getHighAsRefKey()) }

    mappings
      .forEach { (oldRefKey, newRefKey) ->
        val comparisonValueForOldRefKey  = getComparisonRefKey(oldRefKey.value)
        for (ix in refNodes.indices)
        {
          val doLow  = comparisonValuesLow[ix]  == comparisonValueForOldRefKey
          val doHigh = comparisonValuesHigh[ix] == comparisonValueForOldRefKey

          if (!doLow && !doHigh)
            continue

          val low  = if (doLow)  reviseKey(refRanges[ix].getLowAsRefKey(),  newRefKey.value)  else refRanges[ix].getLowAsRefKey()
          val high = if (doLow)  reviseKey(refRanges[ix].getHighAsRefKey(), newRefKey.value) else refRanges[ix].getHighAsRefKey()

          refNodes[ix][attributeName] = if (low == high)
            fileProtocol.refToString(low)
          else
            fileProtocol.refToString(low)
        } // for
      } // forEach
  } // fun
} // PA_Utils
