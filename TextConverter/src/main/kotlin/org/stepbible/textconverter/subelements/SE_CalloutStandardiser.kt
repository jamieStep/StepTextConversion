package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.set
import org.stepbible.textconverter.support.stepexception.StepExceptionShouldHaveBeenOverridden
import org.stepbible.textconverter.utils.IssueAndInformationRecorder
import org.stepbible.textconverter.utils.Osis_FileProtocol
import org.stepbible.textconverter.utils.Usx_FileProtocol
import org.stepbible.textconverter.utils.Z_FileProtocol
import org.w3c.dom.Node

open class SE_CalloutStandardiser protected constructor (fileProtocol: Z_FileProtocol): SE
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  protected open fun setCallout (node: Node): Unit = throw StepExceptionShouldHaveBeenOverridden()
  protected val m_FileProtocol = fileProtocol




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
    Dbg.reportProgress("Standardising callouts for ${m_FileProtocol.getBookCode(rootNode)}.")

    var doneSomething = false

    fun convert (x: Node)
    {
      setCallout(x)
      doneSomething = true
    }

    Dom.findNodesByName(rootNode, m_FileProtocol.tagName_note(), false).forEach { convert(it) }
    if (doneSomething) IssueAndInformationRecorder.setChangedFootnoteCalloutsToHouseStyle()
  }
 }




/*****************************************************************************/
object Osis_SE_CalloutStandardiser: SE_CalloutStandardiser(Osis_FileProtocol)
{
  override fun setCallout (node: Node)
  {
    node["n"] = ConfigData[if ("explanation" == node["type"]) "stepExplanationCallout" else "stepCrossReferenceCallout"]!!
  }
}




/******************************************************************************/
object Usx_SE_CalloutStandardiser: SE_CalloutStandardiser(Usx_FileProtocol)
{
  override fun setCallout (node: Node)
  {
    node["callout"] = ConfigData[if ("f" == node["style"]) "stepExplanationCallout" else "stepCrossReferenceCallout"]!!
  }
}