package org.stepbible.textconverter.builders

import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.get
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.set
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ParallelRunning
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceAbandonRun
import org.w3c.dom.Node
import java.util.concurrent.atomic.AtomicBoolean


/******************************************************************************/
/**
* Takes any proprietary tags and converts them into pukka OSIS formatting tags
* This was introduced mainly to support variant information which, in order for
* it to be explicit, required us to introduce our own proprietary semantic
* markup.
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_InitialOsisConvertProprietaryTags
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process (dataCollection: X_DataCollection): Boolean
  {
    return processVariantTags(dataCollection)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun processVariantTags (dataCollection: X_DataCollection): Boolean
  {
    val res = AtomicBoolean(false)

      with(ParallelRunning(true)) {
        run {
          Rpt.reportWithContinuation(level = 1, "Converting variant tags ...") {
            dataCollection.getRootNodes().forEach { rootNode ->
              asyncable {
                Rpt.reportBookAsContinuation(dataCollection.getFileProtocol().getBookAbbreviation(rootNode))
                if (processVariantTags(rootNode))
                  res.set(true)
              } // asyncable
            } // forEach
          } // reportWithContinuation
        } // run
      } // with

      return res.get()
  }


  /****************************************************************************/
  private fun processVariantTags (rootNode: Node): Boolean
  {
    val nodes = Dom.findNodesByAttributeName(rootNode, "note", "x-step-type")
    if (nodes.isEmpty()) return false

    nodes.forEach {
      when (it["x-step-type"])
      {
        "alternatives" -> processVariantTag_alternatives(it)
        "only-in-some" -> processVariantTag_onlyInSome(it)
        else           -> throw StepExceptionWithoutStackTraceAbandonRun("Unexpected proprietary tag: ${Dom.toString(it)}")
      }
    }

    return true
  }


  /****************************************************************************/
  /* Here we have a containing tag which contains the 'standard' text, and a
     number of subtags, each of which contains a possible variant reading.
     We want the standard text in bold, followed in turn by each of the
     variant readings.  Each of the latter is to appear within angle brackets,
     and the closing bracket should indicate the source of that variant text. */

  private fun processVariantTag_alternatives (node: Node)
  {
    /**************************************************************************/
    val variants = Dom.findNodesByName(node, "rdg", false)



    /**************************************************************************/
    //fun dbgChildren (n: Node) = Dom.getChildren(n).forEach { Dbg.d(Dom.toString(it)) }


    /**************************************************************************/
    /* Insert the <...> and append the source as a superscript. */

    variants.forEach { variant ->
      Dom.insertNodeBefore(variant.firstChild, Dom.createTextNode(node.ownerDocument, " &lt;"))
      variant.appendChild(Dom.createTextNode(node.ownerDocument, "&gt;"))

      val explanation = Dom.createNode(node.ownerDocument, "<hi/>")
      explanation["type"] = "super"
      explanation.textContent = variant["x-source"]
      variant.appendChild(explanation)
      variant.appendChild(Dom.createTextNode(node.ownerDocument, " "))
    }



    /**************************************************************************/
    /* Turn the parent into a boldface node. */

    Dom.deleteAllAttributes(node)
    Dom.setNodeName(node, "hi")
    node["type"] = "bold"



    /**************************************************************************/
    /* Insert a temporary node at the same level as the parent.  We use this
       as a marker, so that we can insert stuff before it.  Then move the
       children of the variants up to that level, and remove the marker
       again. */

    val tempNode = Dom.createNode(node.ownerDocument, "<x/>")
    Dom.insertNodeAfter(node, tempNode)

    variants.forEach { variant ->
      val children = Dom.getChildren(variant)
      children.forEach { Dom.insertNodeBefore(tempNode, it) }
    }

    Dom.deleteNode(tempNode)



    /**************************************************************************/
    /* Finally, we no longer need the variant nodes themselves, because we have
       moved their content. */

    Dom.deleteNodes(variants)
  }


  /****************************************************************************/
  /* Here we expect to have just one chunk of text, which we want to render
     in bold, along with a superscripted explanation. */

  private fun processVariantTag_onlyInSome (node: Node)
  {
    /**************************************************************************/
    val content = Dom.findNodeByName(node, "rdg", false)!!
    val source = content["x-source"]!!



    /**************************************************************************/
    /* Change content tag to hi:bold. */

    Dom.deleteAllAttributes(content)
    Dom.setNodeName(content, "hi")
    content["type"] = "bold"



    /**************************************************************************/
    /* Add the superscripted explanation. */

    val explanation = Dom.createNode(node.ownerDocument, "<hi/>")
    explanation["type"] = "super"
    explanation.textContent = source
    node.appendChild(explanation)



    /**************************************************************************/
    /* Move contents of node up to the same level as node, and then delete node
       itself. */

    Dom.promoteChildren(node)
    Dom.deleteNode(node)
  }
}