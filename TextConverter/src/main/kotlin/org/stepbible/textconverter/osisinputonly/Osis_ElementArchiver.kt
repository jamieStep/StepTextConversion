package org.stepbible.textconverter.osisinputonly

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.utils.InternalOsisDataCollection
import org.w3c.dom.Document
import org.w3c.dom.Node


/******************************************************************************/
/**
 * Special processing for footnotes.
 *
 * This was forced upon me while processing NETfull.  This has enormously
 * extensive footnotes, and processing the full text looked as though it was
 * going to take the better part of 12 hours -- not ideal if you are concerned
 * that you may find, at the end of this, that there were errors in the
 * processing.
 *
 * In fact, we do very little with the actual content of footnotes, so I
 * extract them as early as possible in the processing, leaving placeholders
 * in the DOM.  I can then carry out the bulk of the processing without
 * worrying about having note-related material present; and I can then
 * reintroduce the notes at the end of the processing.
 *
 * I have since extended this so that it can accommodate cross-references and
 * things other than footnotes -- although it has been used in anger only with
 * cross-references and footnotes.
 *
 * If you want to use it with other things, you need to be sure that they don't
 * contain verse markers (and probably don't contain note-markers either), that
 * removing them temporarily from the text will not have any adverse
 * consequences, and that if they themselves need any modification etc, you do
 * not rely upon their immediate neighbourhood to make that processing possible.
 *
 * @author ARA "Jamie" Jamieson
 */

object Osis_ElementArchiver
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun archiveElements (doc: Document) = C_ElementsToBeArchived.forEach{ archiveElements(doc, it.first) }
  fun processElements () = C_ElementsToBeArchived.filter { null != it.second } .forEach { it.second(it.first) } // Elements which need to be updated within the archive.
  fun restoreElements (doc: Document) = C_ElementsToBeArchived.forEach{ restoreElements(doc, it.first) }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun archiveElements (doc: Document, extendedTagName: String)
  {
    Dbg.reportProgress("Temporarily removing nodes of type $extendedTagName.")

    val savedData = Dom.createDocument()
    val rootNode = Dom.createNode(savedData, "<jamie/>")
    savedData.appendChild(rootNode)

    getNodeListFromWorkingDocument(doc, extendedTagName)
      .forEach {
        val clonedNode = Dom.cloneNode(savedData, it, deep = true)
        removeTemporaryAttributes(clonedNode)
        val ix = (m_Index++).toString()
        clonedNode["X_index"] = ix
        val placeHolder = Dom.cloneNode(it.ownerDocument, it, deep = false)
        placeHolder["X_index"] = ix
        Dom.insertNodeBefore(it, placeHolder)
        Dom.deleteNode(it)
        rootNode.appendChild(clonedNode)
      }

      m_SavedData[extendedTagName] = savedData
  }


  /****************************************************************************/
  private fun getNodeListFromArchive (extendedTagName: String) = Dom.getChildren(m_SavedData[extendedTagName]!!.documentElement)


  /****************************************************************************/
  private fun getNodeListFromWorkingDocument (doc: Document, extendedTagName: String): List<Node>
  {
    return if (':' in extendedTagName)
    {
      val (nodeName, typeName) = extendedTagName.split(":")
      doc.findNodesByAttributeValue(nodeName, "type", typeName)
    }
    else
      doc.findNodesByName(extendedTagName)
  }


  /****************************************************************************/
  private fun processCrossReferences (extendedTagName: String)
  {
    Osis_CrossReferenceChecker.process(InternalOsisDataCollection, getNodeListFromArchive(extendedTagName).filter { "crossReference" == it["type"] })
  }


  /****************************************************************************/
  private fun restoreElements (doc: Document, extendedTagName: String)
  {
    Dbg.reportProgress("Reinstating $extendedTagName nodes if necessary.")
    val map: MutableMap<Int, Node> = mutableMapOf()
    Dom.getChildren(m_SavedData[extendedTagName]!!.documentElement). forEach { map[it["X_index"]!!.toInt()] = it }
    getNodeListFromWorkingDocument(doc, extendedTagName)
      .filter { "X_index" in it }
      .forEach {
        val ix = it["X_index"]!!.toInt()
        val newNode = Dom.cloneNode(doc, map[ix]!!, deep = true)
        newNode -= "X_index"
        Dom.insertNodeBefore(it, newNode)
        Dom.deleteNode(it)
      }

    m_SavedData.remove(extendedTagName) // Free up memory.
  }


  /****************************************************************************/
  private fun removeTemporaryAttributes (node: Node)
  {
    fun deleteTemporaries (node: Node) = Dom.getAttributes(node).filter { it.key.startsWith("_") }. forEach { attr -> Dom.deleteAttribute(node, attr.key) }
    node.getAllNodesBelow().filter { "_t" in it }.forEach(::deleteTemporaries)
    deleteTemporaries(node)
  }


  /****************************************************************************/
  private val C_ElementsToBeArchived = listOf(Pair("note", ::processCrossReferences))
  private var m_Index = 0
  private val m_SavedData: MutableMap<String, Document> = mutableMapOf()
}