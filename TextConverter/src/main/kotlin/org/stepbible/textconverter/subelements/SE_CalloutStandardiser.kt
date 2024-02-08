package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node

class SE_CalloutStandardiser (dataCollection: X_DataCollection): SE(dataCollection)
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
   * Forces callouts into standard form.  I'm not sure of the desirability of
   * this, but that's a consideration for another day.
   *
   * @param rootNode
   */

  override fun process (rootNode: Node)
  {
    Dbg.reportProgress("Standardising callouts for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")

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
