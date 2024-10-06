package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ParallelRunning
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
 * This object provides somewhere where we *could* do this if we opted to.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_ListEncapsulator: PA()
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
  * Encapsulates lists.  Except that it doesn't actually do anything.
  *
  * @param dataCollection
  */

  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection)
    Rpt.reportWithContinuation(level = 1, "Possibly encapsulating lists (but probably not) ...") {
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
    encapsulateLists(rootNode)
  }


  /****************************************************************************/
  private fun encapsulateLists (rootNode: Node)
  {
      // listOf("io", "is", "li", "pi", "q") -- this may be relevant to USX, but I'm not implementing anything currently.
  }
}
