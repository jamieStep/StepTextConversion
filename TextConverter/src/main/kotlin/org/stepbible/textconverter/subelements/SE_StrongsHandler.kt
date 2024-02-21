package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.miscellaneous.set
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/****************************************************************************/
/**
 * Canonicalises Strong's references -- expands numbers to four characters
 * with leading zeroes, removes spaces, ensures that all start with either
 * 'G' or 'H' (in some texts, I've seen the first start that way, and the
 * others assumed to default to the same thing); and prepends each with
 * 'strong:'.
 *
 * @author ARA "Jamie" Jamieson
 */

class SE_StrongsHandler (dataCollection: X_DataCollection): SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun thingsIveDone () = listOf(ProcessRegistry.StrongsCanonicalised)


  /****************************************************************************/
  /**
  * See head-of-class comments.
  *
  * @param rootNode Root of document, or of portion of document being
  *   processed.
  */

  override fun processRootNodeInternal (rootNode: Node)
  {
    Dbg.reportProgress("Handling Strongs for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    val strongs = Dom.getNodesInTree(rootNode).filter { m_FileProtocol.isStrongsNode(it) }
    strongs.forEach { doStrong(it) }
    if (strongs.isNotEmpty()) IssueAndInformationRecorder.setStrongs()
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Processes a single Strongs node. */

  private fun doStrong (node: Node)
  {
    var prefix = ""
    val strongsElts = node[m_FileProtocol.attrName_strong()]!!.split("\\W+".toRegex())
      .map { it.trim().uppercase() }
      .map {
        var strong = it.replace("strong:", "")
        if (strong.substring(0, 1) in "GH")
        {
          prefix = strong.substring(0, 1)
          strong = strong.substring(1)
        }

        "strong:" + prefix + "0".repeat(4 - strong.length) + strong
      }

    node[m_FileProtocol.attrName_strong()] = strongsElts.joinToString(",")
  }
}

