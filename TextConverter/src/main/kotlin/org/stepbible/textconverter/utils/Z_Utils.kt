package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.stepexception.StepExceptionShouldHaveBeenOverridden
import org.w3c.dom.Document
import org.w3c.dom.Node

/******************************************************************************/
/**
 * Utilities whose implementation differs slightly according to whether we
 * are dealing with USX or OSIS.
 *
 * @author ARA "Jamie" Jamieson
 */

open class Z_Utils protected constructor (fileProtocol: Z_FileProtocol)
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
   * Checks if the book uses number attributes rather than sid/eid.  If it
   * does, converts it to use sid and eid.
   *
   * This can cope with both milestone and (I think) with non-milestone.  If
   * we have a text which gives only verse- or chapter- starts, they get sids.
   * (I don't think there is any way a text in milestone form can have eids,
   * because I don't think USX caters for it.)
   *
   * @param rootNode Node for book.
   */

  open fun sidify (rootNode: Node): Unit = throw StepExceptionShouldHaveBeenOverridden()


  /****************************************************************************/
  /**
   * Creates the collection of nodes required to represent a footnote.
   *
   * @param document
   * @param refKey
   * @param text
   * @param callout If null, the default value from the configuration data is used.
   *                If a MarkerHandler, then the next value obtained from that.
   * @return 'note' node for footnote.
   */

  fun makeFootnote (document: Document, refKey: RefKey, text: String, callout: Any? = null): Node
  {
    val caller =
      when (callout)
      {
        null      -> ConfigData["stepExplanationCallout"]!!
        is String -> callout
        else      -> (callout as MarkerHandler).get()
      }


    val note = m_FileProtocol.makeFootnoteNode(document, refKey, caller)
    note.appendChild(Dom.createTextNode(document, text))
    return note
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private val m_FileProtocol = fileProtocol
}




/******************************************************************************/
object Osis_Utils: Z_Utils(Osis_FileProtocol)
{
  override fun sidify (rootNode: Node) { }
}





/******************************************************************************/
object Usx_Utils: Z_Utils(Usx_FileProtocol)
{
  /****************************************************************************/
  override fun sidify (rootNode: Node)
  {
    /**************************************************************************/
    var bookName = rootNode["code"]!!
    var chapterNo = ""



    /************************************************************************/
    fun processNode (node: Node)
    {
      when (Dom.getNodeName(node))
      {
        /********************************************************************/
        "chapter" ->
        {
          if ("sid" in node) return
          if ("eid" in node) return
          chapterNo = node["number"]!!
          node -= "number"
          node["sid"] = "$bookName $chapterNo"
        }



        /**********************************************************************/
        "verse" ->
        {
          if ("sid" in node) return
          if ("eid" in node) return
          val verseNo = node["number"]!!
          node -= "number"
          node["sid"] = "$bookName $chapterNo:$verseNo"
        }
      } // when
    } // processNode



    /**************************************************************************/
    Dom.getNodesInTree(rootNode).forEach { processNode(it) }
  }
}
