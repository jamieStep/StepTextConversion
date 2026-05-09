package org.stepbible.textconverter.textspecificpreprocessors

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.contains
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMResult
import javax.xml.transform.dom.DOMSource

object PreprocessorForMhm
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process ()
  {
    /**************************************************************************/
    val osisFilePath = FileLocations.getInputOsisFilePath()!!
    copyFile(osisFilePath)
    val dom = Dom.getDocument(osisFilePath)



    /**************************************************************************/
    collapseWhitespace(dom)
    handleSimulatedIndents(dom)
    removeEmptyPFollowedBySid(dom)



    /**************************************************************************/
    Dom.outputDomAsXml(dom, osisFilePath, null)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun collapseWhitespace (dom: Document)
  {
    Dom.collapseWhitespace(dom) // Collapse consecutive whitespace nodes.

    val paras = Dom.findNodesByName(dom, "p")

    paras // Remove whitespace immediately following the end of paras.
      //.filter { !it.hasChildNodes() }
      .filter { null != it.nextSibling && it.nextSibling.nodeType == Node.TEXT_NODE && it.textContent.trim().isEmpty() }
      .map { it.nextSibling }
      .forEach { Dom.deleteNode(it) }

    paras // Remove whitespace immediately preceding the end of paras.
      //.filter { !it.hasChildNodes() }
      .filter { null != it.previousSibling && it.previousSibling.nodeType == Node.TEXT_NODE && it.textContent.trim().isEmpty() }
      .map { it.previousSibling }
      .forEach { Dom.deleteNode(it) }
  }


  /****************************************************************************/
  private fun copyFile (filePath: String)
  {
    val sourceFile = File(filePath)
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val baseName = sourceFile.nameWithoutExtension
    val newName = "${timestamp}_$baseName.tmp"
    val dest = File(sourceFile.parentFile, newName)
    sourceFile.copyTo(dest, overwrite = false)
  }


  /****************************************************************************/
  private fun handleSimulatedIndents (dom: Document)
  {
    Dom.findNodesByName(dom, "lb")
    .filter { null != it.previousSibling && "verse" == Dom.getNodeName(it.previousSibling) && "sID" in it.previousSibling }
    .forEach { lb ->
      Dom.insertNodeBefore(lb.previousSibling, lb)
    }
  }


  /****************************************************************************/
  private fun removeEmptyPFollowedBySid (dom: Document)
  {
    /**************************************************************************/
    fun isEmptyContainer (node: Node): Boolean
    {
      if (!node.hasChildNodes()) return true;
      for (n in Dom.getChildren(node))
      {
        if (Node.TEXT_NODE != n.nodeType) return false
        if (n.textContent.trimStart().isNotEmpty()) return false
      }

      return true
    }



   /**************************************************************************/
   Dom.findNodesByName(dom, "p")
      .filter { isEmptyContainer(it) }
      .filter { ( null != it.nextSibling && "verse" == Dom.getNodeName(it.nextSibling) && "sID" in it.nextSibling!! ) ||
                ( null != it.nextSibling && null != it.nextSibling.nextSibling && "verse" == Dom.getNodeName(it.nextSibling.nextSibling) && "sID" in it.nextSibling.nextSibling!! )
              }
      .forEach {
        val comment = Dom.createCommentNode(dom, "Was empty p")
        val replacement = Dom.createNode(dom, "<lb/>")
        Dom.insertNodeBefore(it, comment)
        Dom.insertNodeBefore(it, replacement)
        Dom.deleteNode(it)
      }
  }
}