package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.utils.X_DataCollection
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

open class SE_ListEncapsulator (dataCollection: X_DataCollection): SE(dataCollection)
{
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
    Dbg.reportProgress("Possibly encapsulating lists (but probably not) for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
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
  private fun encapsulateLists (rootNode: Node)
  {
      // listOf("io", "is", "li", "pi", "q") -- this may be relevant to USX, but I'm not implementing anything currently.
  }
}
