package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.applicationspecificutils.Original
import org.stepbible.textconverter.applicationspecificutils.Revised
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.concurrent.ConcurrentHashMap


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
  /**                               Archive                                  **/
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

  fun archiveElements (dataCollection: X_DataCollection, filter: (Node) -> Boolean)
  {
    val me = this

    with(ParallelRunning(true)) {
      run {
        Rpt.reportWithContinuation(level = 1, "Temporarily archiving selected nodes ...") {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable {
              Rpt.reportBookAsContinuation(dataCollection.getFileProtocol().getBookAbbreviation(rootNode))
              PA_ElementArchiverArchivePerBook(me).processRootNode(rootNode, filter)
            } // asyncable
          } // forEach
        } // reportWithContinuation
      } // run
    } // with

    m_Valid = true
  } // fun


  /****************************************************************************/
  private class PA_ElementArchiverArchivePerBook (val owningArchiver: PA_ElementArchiver)
  {
    /**************************************************************************/
    /* This is called once for each type of node to be archived.  I archive
       nodes by copying them to a temporary document, rather than simply by
       creating a list.  This is necessary because I don't just need to save
       nodes -- I need to save the structure beneath each node as well. */

    fun processRootNode (rootNode: Node, filter: (Node) -> Boolean)
    {
      /************************************************************************/
      /* Create a document to hold the archived data, and give it a root
         node. */

      val doc = Dom.createDocument()
      val rootNodeForArchive = Dom.createNode(doc, "<jamie/>")
      doc.appendChild(rootNodeForArchive)
      owningArchiver.getArchives()[rootNode] = doc



      /************************************************************************/
      rootNode.getAllNodesBelow().filter(filter).forEach { originalNode ->
      val ix = (owningArchiver.getNewIndex()).toString()              // Unique index.

      val clonedNode = Dom.cloneNode(doc, originalNode, deep = true)  // Clone the node to be archived into the temporary document.
      removeTemporaryAttributes(clonedNode)                           // When we reinstate this later, I don't _think_ we want any temporary attributes.
      clonedNode["_X_index"] = ix                                     // Give the clone a unique index which ties it back to the original document.
      rootNodeForArchive.appendChild(clonedNode)                      // Store the cloned node in the temporary document.

      originalNode["_X_index"] = ix                                   // Give the original node the same index we've just added to the clone.
      Dom.deleteChildren(originalNode)                                // And remove the substructure.  This is the main thing which speeds up other processing.
    } // fun
  } // class


  /****************************************************************************/
  private fun removeTemporaryAttributes (node: Node)
  {
    fun deleteTemporaries (node: Node) = Dom.getAttributes(node).filter { it.key.startsWith("_") }. forEach { attr -> Dom.deleteAttribute(node, attr.key) }
    node.getAllNodesBelow().filter { "_t" in it }.forEach(::deleteTemporaries)
    deleteTemporaries(node)
  }
}



  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                          Modify references                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Changes those portions of cross-references which actually implement the
  * links (ie the 'loc' attribute in USX, or the 'reference' attribute in
  * OSIS).
  *
  * @param dataCollection Used to determine whether we are dealing with OSIS or
  *   USX.
  * @param mappings Keyed on *old* refKey; value is *new* refKey.
  * @param mask b -> match on book, replace book; bc -> match on book+chapter,
  *   replace book+chapter; etc.  You can even give bcvs, although how useful
  *   that is, I'm not sure.
  */

  fun modifyReferences (dataCollection: X_DataCollection, mappings: Map<Original<RefKey>, Revised<RefKey>>, mask: String)
  {
    if (!m_Valid)
      throw StepExceptionWithStackTraceAbandonRun("Accessing archive at a time when it is invalid.")

    if (m_Archives.isEmpty())
     return


    with(ParallelRunning(true)) {
      run {
        Rpt.reportWithContinuation(level = 1, "Modifying any cross-references affected by reversification or renaming to  ...") {
          m_Archives.keys.forEach { archivedNode ->
            asyncable {
              Rpt.reportBookAsContinuation(dataCollection.getFileProtocol().getBookAbbreviation(archivedNode))
              PA_Utils.modifyReferences(dataCollection.getFileProtocol(),
                                        m_Archives[archivedNode]!!.documentElement,
                                        dataCollection.getFileProtocol().tagName_crossReference(),
                                        dataCollection.getFileProtocol().attrName_crossReference(),
                                        mappings,
                                        mask)
            } // asyncable
          } // forEach
        } // reportWithContinuation
      } // run
    } // with
  } // fun





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Restore                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Restores the elements which we archived earlier.
  *
  * @param dataCollection Data to be processed.
  * @return List of restored elements in the target document.
  */

  fun restoreElements (dataCollection: X_DataCollection)
  {
    if (!m_Valid)
      throw StepExceptionWithStackTraceAbandonRun("Accessing archive at a time when it is invalid.")

    m_Valid = false
    val me = this

    with(ParallelRunning(true)) {
      run {
        Rpt.reportWithContinuation(1, "Reinstating archived nodes if any ...") {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable {
              Rpt.reportBookAsContinuation(dataCollection.getFileProtocol().getBookAbbreviation(rootNode))
              PA_ElementArchiverRestorePerBook(me).processRootNode(rootNode) }
          } // forEach
        } // report
      } // run
    } // with
  }  // fun



  /****************************************************************************/
  private class PA_ElementArchiverRestorePerBook (val owningArchiver: PA_ElementArchiver)
  {
    /**************************************************************************/
    fun processRootNode (rootNode: Node): List<Node>
    {
      val placeHolders = rootNode.getAllNodesBelow().filter { "_X_index" in it } // List of all placeholders in the document into which we are reinstating things.
      return restoreElements(rootNode, placeHolders)
    }


    /**************************************************************************/
    private fun restoreElements (rootNode: Node, placeHolders: List<Node>): List<Node>
    {
      /************************************************************************/
      if (placeHolders.isEmpty())
        return listOf()



      /************************************************************************/
      /* Map index attribute of nodes in the saved document to the nodes
         themselves. */

      val targetDoc = rootNode.ownerDocument
      val res: MutableList<Node> = mutableListOf()
      val map: MutableMap<Int, Node> = mutableMapOf()
      Dom.getChildren(owningArchiver.getArchives()[rootNode]!!.documentElement). forEach { map[it["_X_index"]!!.toInt()] = it }



      /************************************************************************/
      /* The place-holders live in the target document, and their _X_index
         attribute relates them to nodes in one or other of the saved documents
         (bearing in mind that we have a separate saved document for each type
         of node we have archived).  We run over the place-holders looking for
         nodes with the same index in the save-document which we are currently
         handling.  If present, we move the saved version back into the main
         document. */

      placeHolders.forEach {
        val ix = it["_X_index"]!!.toInt()
        if (ix in map)
        {
          val newNode = Dom.cloneNode(targetDoc, map[ix]!!, deep = true)     // Clone the archived node back into the original document.
          newNode -= "_X_index"                                              // Remove the _X_index attribute.
          Dom.insertNodeBefore(it, newNode)                                  // Position the cloned node before the place-holder.
          Dom.deleteNode(it)                                                 // And delete the place-holder.
          res += newNode
        } // if
      } // forEach

      return res
    } // fun restoreElements
  } // class PA_ElementArchiverRestorePerBook




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Miscellaneous                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  internal fun getArchives () = m_Archives
  @Synchronized internal fun getNewIndex () = ++m_Index



  /****************************************************************************/
  private var m_Archives = ConcurrentHashMap<Node, Document>()
  private var m_Valid = false


  /****************************************************************************/
  private companion object {
    var m_Index = 0
  }
} // PA_ElementArchiver
