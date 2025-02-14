package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ParallelRunning
import org.w3c.dom.Node

/****************************************************************************/
/**
 * Forces callouts into house style.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_CalloutStandardiser: PA(), ObjectInterface
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
  * Forces callouts into house style.
  *
  * @param dataCollection
  */

  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection)
    Rpt.reportWithContinuation(level = 1, "Forcing callouts into house style ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable { PA_CalloutStandardiserPerBook(m_FileProtocol).processRootNode(rootNode) }
          } // forEach
        } // run
      } // parallel
    } // reportWithContinuation
  } // fun
}




/******************************************************************************/
private class PA_CalloutStandardiserPerBook (val m_FileProtocol: X_FileProtocol)
{
  /****************************************************************************/
  /**
   * Forces callouts into standard form.  I'm not sure of the desirability of
   * this, but that's a consideration for another day.
   *
   * @param rootNode
   */

  fun processRootNode (rootNode: Node)
  {
    Rpt.reportBookAsContinuation(m_FileProtocol.getBookAbbreviation(rootNode))
    var doneSomething = false

    fun convert (x: Node)
    {
      m_FileProtocol.standardiseCallout(x)
      doneSomething = true
    }

    Dom.findNodesByName(rootNode, m_FileProtocol.attrName_note(), false).forEach { convert(it) }
    if (doneSomething) IssueAndInformationRecorder.setChangedFootnoteCalloutsToHouseStyle()
  }
}
