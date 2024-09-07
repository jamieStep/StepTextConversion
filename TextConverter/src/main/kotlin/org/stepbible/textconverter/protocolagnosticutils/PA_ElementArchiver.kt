package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.w3c.dom.Document
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Removes certain elements from a text and archives them, with a possible
 * view to restoring them later.
 *
 * Much of the converter processing involves scanning repeatedly over all the
 * elements in a text.  In some texts, a lot of these elements have
 * comparatively little processing applied to them, but there may be very many
 * of them.  Notes are a case in point: we do very little to them, but in a
 * text like NET2full there are tens of thousands of notes -- a lot of nodes
 * in themselves, but many more when you take into account the fact that each
 * has a further structure below it.
 *
 * To speed up processing, it is desirable to remove such nodes from the
 * document being processed.  We can then carry out the bulk of the processing
 * without having to cope with these nodes ... and then reinstate them at the
 * end.
 *
 * This is handled by this present class.
 *
 * You can create multiple instance of this class; and you can call a single
 * instance multiple times to archive different collections of nodes.  A
 * single instance reinstates all of its archived nodes to a single Document.
 *
 * @author ARA "Jamie" Jamieson
 */

class PA_ElementArchiver
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
  * Extracts elements of the kinds selected by 'filter' and stores them in an
  * archive so they can be reinstated later.
  *
  * @param dataCollection Data to be processed.
  * @param filter Selects nodes to be archived.
  */

  fun archiveElements (dataCollection: X_DataCollection, filter: (Node) -> Boolean) =
    archiveElements(dataCollection.getDocument(), filter)


  /****************************************************************************/
  /**
  * Restores the elements which we archived earlier.
  *
  * @param dataCollection Data to be processed.
  */

  fun restoreElements (dataCollection: X_DataCollection) = restoreElements(dataCollection.getDocument())





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun archiveElements (doc: Document, filter: (Node) -> Boolean)
  {
    Dbg.withReportProgressSub("Temporarily archiving selected nodes.") {
      archiveElements1(doc, filter)
    }
  }


  /****************************************************************************/
  /* This is called once for each type of node to be archived.  I archive nodes
     by copying them to a temporary document, rather than simply by creating a
     list.  This is necessary because I don't just need to save nodes -- I need
     to save the structure beneath each node as well.
   */

  private fun archiveElements1 (doc: Document, filter: (Node) -> Boolean)
  {
    /**************************************************************************/
    /* Create a document to hold the archived data, and give it a root node. */

    val rootNodeForArchive = Dom.createNode(m_Archive, "<jamie/>")
    m_Archive.appendChild(rootNodeForArchive)




    /**************************************************************************/
    doc.getAllNodesBelow().filter(filter).forEach {
      val ix = (m_Index++).toString()                             // Unique index.
      val clonedNode = Dom.cloneNode(m_Archive, it, deep = true)  // Clone the node to be archived into the temporary document.
      removeTemporaryAttributes(clonedNode)                       // When we reinstate this later, I don't _think_ we want any temporary attributes.
      clonedNode["X_index"] = ix                                  // Give the clone a unique index which ties it back to the original document.
      rootNodeForArchive.appendChild(clonedNode)                  // Store the cloned node in the temporary document.

      it["X_index"] = ix                                          // Give the original node the same index we've just added to the clone.
      Dom.deleteChildren(it)                                      // And remove the substructure.  This is the main thing which speeds up other processing.
    }
  }


  /****************************************************************************/
  private fun restoreElements (targetDoc: Document)
  {
    val placeHolders = targetDoc.getAllNodesBelow().filter { "X_Index" in it } // List of all placeholders in the document into which we are reinstating things.
    Dbg.withReportProgressSub("Reinstating archived nodes if any.") {
      restoreElements(targetDoc, placeHolders)
    }
  }


  /****************************************************************************/
  private fun restoreElements (targetDoc: Document, placeHolders: List<Node>)
  {
    /**************************************************************************/
    if (placeHolders.isEmpty())
      return



    /**************************************************************************/
    /* Map index attribute of nodes in the saved document to the nodes
       themselves. */

    val map: MutableMap<Int, Node> = mutableMapOf()
    Dom.getChildren(m_Archive.documentElement). forEach { map[it["X_index"]!!.toInt()] = it }



    /**************************************************************************/
    /* The place-holders live in the target document, and their X_index
       attribute relates them to nodes in one or other of the saved documents
       (bearing in mind that we have a separate saved document for each type
       of node we have archived).  We run over the place-holders looking for
       nodes with the same index in the save-document which we are currently
       handling.  If present, we move the saved version back into the main
       document. */

    placeHolders.forEach {
      val ix = it["X_index"]!!.toInt()
      if (ix in map)
      {
        val newNode = Dom.cloneNode(targetDoc, map[ix]!!, deep = true)     // Clone the archived node back into the original document.
        newNode -= "X_index"                                               // Remove the X_index attribute.
        Dom.insertNodeBefore(it, newNode)                                  // Position the cloned node before the place-holder.
        Dom.deleteNode(it)                                                 // And delete the place-holder.
      }
    }
  }


  /****************************************************************************/
  private fun removeTemporaryAttributes (node: Node)
  {
    fun deleteTemporaries (node: Node) = Dom.getAttributes(node).filter { it.key.startsWith("_") }. forEach { attr -> Dom.deleteAttribute(node, attr.key) }
    node.getAllNodesBelow().filter { "_t" in it }.forEach(::deleteTemporaries)
    deleteTemporaries(node)
  }


  /****************************************************************************/
  private var m_Archive = Dom.createDocument()

  private companion object {
    var m_Index = 0
  }
}