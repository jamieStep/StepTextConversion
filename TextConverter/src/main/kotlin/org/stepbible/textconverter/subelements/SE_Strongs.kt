package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.miscellaneous.minusAssign
import org.stepbible.textconverter.support.miscellaneous.set
import org.stepbible.textconverter.support.stepexception.StepExceptionShouldHaveBeenOverridden
import org.stepbible.textconverter.utils.IssueAndInformationRecorder
import org.stepbible.textconverter.utils.Osis_FileProtocol
import org.stepbible.textconverter.utils.Usx_FileProtocol
import org.stepbible.textconverter.utils.Z_FileProtocol
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

open class SE_Strongs protected constructor (fileProtocol: Z_FileProtocol): SE
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
  * See head-of-class comments.
  *
  * @param rootNode Root of document, or of portion of document being
  *   processed.
  */

  override fun process (rootNode: Node)
  {
    Dbg.reportProgress("Handling Strongs for ${m_FileProtocol.getBookCode(rootNode)}.")
    val strongs = getStrongsNodes(rootNode)
    strongs.forEach { doStrong(it) }
    if (strongs.isNotEmpty()) IssueAndInformationRecorder.setStrongs()
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  protected open fun getStrongsNodes (rootNode: Node): List<Node> = throw StepExceptionShouldHaveBeenOverridden()
  protected val m_FileProtocol = fileProtocol





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




/****************************************************************************/
object Osis_SE_Strongs: SE_Strongs(Osis_FileProtocol)
{
  override fun getStrongsNodes (rootNode: Node) = Dom.findNodesByAttributeName(rootNode, "w", "lemma")
}





/****************************************************************************/
object Usx_SE_Strongs: SE_Strongs(Usx_FileProtocol)
{
  override fun getStrongsNodes (rootNode: Node) = Dom.findNodesByName(rootNode, "char", false)
    .filter { Dom.hasAttribute(it, "strong") }
    .map { it -= "style"; it} // It's convenient to remove style attributes here, since they confuse later processing.
}
