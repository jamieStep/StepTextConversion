package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ParallelRunning
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.getAllNodesBelow
import org.w3c.dom.Node

/******************************************************************************/
/**
 * During OSIS processing, it is sometimes convenient to introduce temporary
 * tags (all with names starting _X_).  These are not valid OSIS, so they have
 * to be replaced before the OSIS can be processed by osis2mod.
 *
 * The _X_ definitions which appear in usxToOsisTagConversionsEtc.conf are
 * also usable here.  Note, though, that I assume relatively simple requirements
 * here.  In USX-to-OSIS translations, I permit all kinds of manipulations to
 * be carried out.  Here I currently assume that a given flavour of _X_ tag is
 * to be replaced either by just its content, or else that it is to be replaced
 * by a hierarchy of tags, with the bottom-most containing the content of the
 * original node.
 *
 * @author ARA "Jamie" Jamieson
 */

object Osis_InternalTagReplacer: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Processes all _X_ tags.
  *
  * @param dataCollection Data to be processed.
  */

  fun process (dataCollection: X_DataCollection)
  {
    Rpt.reportWithContinuation(level = 1, "Osis_InternalTagReplacer: Replacing temporary nodes ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable {
              Rpt.reportBookAsContinuation(dataCollection.getFileProtocol().getBookAbbreviation(rootNode))
              Osis_InternalTagReplacerPerBook().processRootNode(rootNode)
            } // asyncable
          } // forEach
        } // run
      } // parallel
    } // reportWithContinuation
  } // fun
} // object



/******************************************************************************/
private class Osis_InternalTagReplacerPerBook ()
{
  /****************************************************************************/
  fun processRootNode (rootNode: Node)
  {
    rootNode.getAllNodesBelow().filter{ Dom.getNodeName(it).startsWith("_X_") }.forEach(::processNode)
  }


  /****************************************************************************/
  private fun processNode (node: Node)
  {
    /**************************************************************************/
    val nodeName = Dom.getNodeName(node)
    val tagProcessDetails = ConfigData.getUsxToOsisTagTranslation(nodeName)
    var tags: String? = tagProcessDetails?.first



    /**************************************************************************/
    if (null == tags)
    {
      Logger.error("Unknown tag type: " + Dom.toString(node))
      return
    }



    /**************************************************************************/
    /* This deals with something like

         stepUsxToOsisTagTranslation=(usx=Ͼ_X_nonCanonicalϿ osis=ϾϿ)

      where the intention is to ditch the tag, but retain its content. */

    if (tags.isEmpty())
    {
      val children = Dom.getChildren(node)
      children.forEach { Dom.deleteNode(it); Dom.insertNodeBefore(node, it) }
      Dom.deleteNode(node)
      return
    }



    /**************************************************************************/
    /* This deals with things like

         stepUsxToOsisTagTranslation=(usx=Ͼ_X_subverseSeparatorVariableϿ Ͼ<hi type='super'>Ͽ)

       where we want to ditch the node itself, but replace it by a substructure,
       in this case comprising <hi...> containing the children of the original
       node.  I allow for the possibility that we may want to replace the
       node by a hierarchy -- <thing><anotherThing>...</anotherThing></thing>. */

    var newNode: Node? = null
    var parent: Node? = null

    tags.split("<").forEach { nodeAsText ->
      if (nodeAsText.isEmpty())
      return@forEach

      val subNode = Dom.createNode(node.ownerDocument, "<" + nodeAsText.replace(">", "") + "/>")

      if (null == newNode)
        newNode = subNode
      else
        parent!!.appendChild(subNode)

      parent = subNode
    } // forEach


    val children = Dom.getChildren(node)
    children.forEach { Dom.deleteNode(it); parent!!.appendChild(it) }
    Dom.insertNodeBefore(node, newNode!!)
    Dom.deleteNode(node)
  }
}