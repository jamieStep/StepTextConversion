package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.stepexception.StepExceptionShouldHaveBeenOverridden
import org.stepbible.textconverter.utils.Osis_FileProtocol
import org.stepbible.textconverter.utils.Usx_FileProtocol
import org.stepbible.textconverter.utils.Z_FileProtocol
import org.w3c.dom.Node

/****************************************************************************/
/**
 * In theory, certain flavours of list need to be encapsulated in OSIS with
 * bracketing markers.  In practice, we don't do this at present, because
 * osis2mod doesn't seem to require it, and having the tags there introduces
 * excessive vertical whitespace into the rendering.  The downside is that we
 * generate non-compliant OSIS, and therefore can't make our modules available
 * to Crosswire.
 *
 * @author ARA "Jamie" Jamieson
 */

open class SE_ListEncapsulator protected constructor (fileProtocol: Z_FileProtocol): SE
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  open fun encapsulateLists (rootNode: Node): Unit = throw StepExceptionShouldHaveBeenOverridden()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun process (rootNode: Node)
  {
    Dbg.reportProgress("Possibly encapsulating lists (but probably not) for ${m_FileProtocol.getBookCode(rootNode)}.")
    encapsulateLists(rootNode)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  protected val m_FileProtocol = fileProtocol
}





/******************************************************************************/
object Osis_SE_ListEncapsulator: SE_ListEncapsulator(Osis_FileProtocol)
{
  override fun encapsulateLists (rootNode: Node) {  }
}





/******************************************************************************/
object Usx_SE_ListEncapsulator: SE_ListEncapsulator(Usx_FileProtocol)
{
  /****************************************************************************/
  override fun encapsulateLists (rootNode: Node) = listOf("io", "is", "li", "pi", "q").forEach { encapsulateList(rootNode, it) }


  /******************************************************************************/
  /* Wraps poetry or bullet point lists within a container node (equivalent to
     HTML <ul>).  Or, as noted above, actually _doesn't_ wrap things.*/

  private fun encapsulateList (rootNode: Node, type: String)
  {
    // No implementation currently -- we don't encapsulate lists.
  }
}