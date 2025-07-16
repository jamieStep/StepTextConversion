package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.w3c.dom.Node

/****************************************************************************/
/**
 * Takes things like enclosing paras, which I think work equally well as
 * self-closing tags, and converts them to self-closing form, thus reducing
 * the chances of cross-verse-boundary markup.  (Cross-boundary markup is no
 * longer the issue for us which once it was, but osis2mod sometimes gives
 * warnings, and this avoids them.)
 *
 * I'm fairly sure I already have processing to do this somewhere else, but I
 * can't find it now -- just in case you come across it and wonder why the
 * duplication.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_EnclosingTagToSelfClosingConverter: PA(), ObjectInterface
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
  * Arranges to replace selected enclosing tags by self-closing ones.
  *
  * @param dataCollection
  */

  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection)
    Rpt.reportWithContinuation(level = 1, "Converting enclosing tags to self-closing form where possible ...") {
      with (ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach {
            asyncable { (processRootNode(it)) }
          } // forEach
        } // run
      } // Parallel
    } // reportWithContinuation
  } // fun





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun processRootNode (rootNode: Node)
  {
    Rpt.reportBookAsContinuation(m_FileProtocol.getBookAbbreviation(rootNode))
    convertToSelfClosing(rootNode)
  }


  /****************************************************************************/
  private fun convertToSelfClosing (rootNode: Node)
  {
    val paras = rootNode.getAllNodesBelow().filter { m_DataCollection.getFileProtocol().isCollapsibleParaNode(it) }
    paras.forEach { Dom.promoteChildren(it) }
  }
}
