package org.stepbible.textconverter.osisinputonly

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
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
 * @author ARA "Jamie" Jamieson
 */

object Osis_FootnoteHandler
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
  * Reinstates the saved footnotes.
  *
  * @param doc: Document to be updated.
  */

  fun reinstateFootnotes (doc: Document)
  {
    // I was tempted simply to assume that the note nodes in the target document
    // were still in the same order as was originally the case, but it's safer to
    // rely upon indexing, in case I add or delete nodes during processing.

    val map: MutableMap<Int, Node> = mutableMapOf()
    m_SavedData!!.findNodesByName("note").forEach { map[it["X_index"]!!.toInt()] = it }

    doc.findNodesByName("note")
    .filter { "X_index" in it }
    .forEach {
      val ix = it["X_index"]!!.toInt()
      val newNode = Dom.cloneNode(doc, map[ix]!!, deep = true)
      newNode -= "X_index"
      Dom.insertNodeBefore(it, newNode)
      Dom.deleteNode(it)
    }

    m_SavedData = null
  }


  /****************************************************************************/
  /**
  * Removes the footnotes from the given document (*not* the xrefs) and stores
  * them in a separate document.  This makes it possible to speed up the main
  * processing (we don't do much with footnotes, but if they are substantial
  * they get in the way and slow things down).  They can then be reinstated
  * right at the end of the processing.
  *
  * @param doc The document to be processed.
  */

  fun removeFootnotes (doc: Document)
  {
    Dbg.reportProgress("Temporarily removing footnotes.")

    m_SavedData = Dom.createDocument()
    val rootNode = Dom.createNode(m_SavedData!!, "<root/>")
    m_SavedData!!.appendChild(rootNode)

    doc.findNodesByName("note")
      .filter { "crossReference" != it["type"]}
      .forEach {
        val clonedNode = Dom.cloneNode(m_SavedData!!, it, deep = true)
        removeTemporaryAttributes(clonedNode)
        val ix = (m_Index++).toString()
        clonedNode["X_index"] = ix
        val placeHolder = Dom.cloneNode(it.ownerDocument, it, deep = false)
        placeHolder["X_index"] = ix
        Dom.insertNodeBefore(it, placeHolder)
        Dom.deleteNode(it)
        rootNode.appendChild(clonedNode)
      }
      
    //Dbg.d(doc)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun removeTemporaryAttributes (node: Node)
  {
    fun deleteTemporaries (node: Node) = Dom.getAttributes(node).filter { it.key.startsWith("_") }. forEach { attr -> Dom.deleteAttribute(node, attr.key) }
    node.getAllNodesBelow().filter { "_t" in it }.forEach(::deleteTemporaries)
    deleteTemporaries(node)
  }


  /****************************************************************************/
  private var m_Index = 0
  private var m_SavedData: Document? = null
}