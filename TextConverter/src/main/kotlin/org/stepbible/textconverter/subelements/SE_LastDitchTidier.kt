package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.contains
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/****************************************************************************/
/**
 * Ad hoc tidying up -- for example tweaking stuff which otherwise seems to
 * come out wrong for no apparent reason.
 *
 * @author ARA "Jamie" Jamieson
 */

class SE_LastDitchTidier (dataCollection: X_DataCollection): SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun thingsIveDone () = listOf(ProcessRegistry.FeatureDataCollected)


  /****************************************************************************/
  override fun processRootNodeInternal (rootNode: Node)
  {
    Dbg.reportProgress("Last ditch tidying. ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    moveNotesInsideVerses(rootNode)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Some texts appear to place notes outside of verses (ie after the eid).
     If this happens, then at best the note doesn't appear, and at worst
     it messes up the positioning of the verse number. */

  private fun moveNotesInsideVerses (rootNode: Node)
  {
    var eid: Node? = null
    Dom.getNodesInTree(rootNode).forEach {
      when (Dom.getNodeName(it))
      {
        m_FileProtocol.tagName_verse() ->
          eid = if (m_FileProtocol.attrName_verseEid() in it) it else null

        m_FileProtocol.tagName_note() ->
          if (null != eid)
          {
            Dom.deleteNode(it)
            Dom.insertNodeBefore(eid!!, it)
          }

        else ->
          if (!Dom.isWhitespace(it))
            eid = null
      }

    }
  }
}
