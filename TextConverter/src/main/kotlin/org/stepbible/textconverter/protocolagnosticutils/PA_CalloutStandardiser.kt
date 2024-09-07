package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.applicationspecificutils.*
import org.w3c.dom.Node

/****************************************************************************/
/**
 * Forces callouts into house style.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_CalloutStandardiser: PA()
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
    Dbg.withReportProgressSub("Forcing callouts into house style.") {
      extractCommonInformation(dataCollection)
      dataCollection.getRootNodes().forEach(::processRootNode)
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Forces callouts into standard form.  I'm not sure of the desirability of
   * this, but that's a consideration for another day.
   *
   * @param rootNode
   */

  private fun processRootNode (rootNode: Node)
  {
    Dbg.withReportProgressSub("Standardising callouts for ${m_FileProtocol.getBookAbbreviation(rootNode)}.") {
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
}
