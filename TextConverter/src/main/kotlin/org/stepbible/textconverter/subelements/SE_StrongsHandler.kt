package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.contains
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.miscellaneous.set
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node
import java.util.*


/****************************************************************************/
/**
 * Canonicalises Strong's references:
 *
 * -- Some texts give truncated references -- eg H12.  I expand this to
 *    four numbers by adding leading zeroes.
 *
 * -- Some texts have an extra leading zero on the front of references (such
 *    that purely numeric values look like 01234).  I gather this was a
 *    convention before the convention of prepending 'H' or 'G' was adopted.
 *    I truncate these so that the leading zero is removed.
 *
 * -- Some texts give multiple Strong's references within a single tag, but
 *    put 'H' or 'G' only on the first, using the convention that the later
 *    ones default to the original prefix.  These I make explicit.
 *
 * -- There are a few Strong's numbers which need to be corrected.  I handle
 *    this.
 *
 * -- I remove any superfluous spaces, and prepend the result with 'strong:'.
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
    m_CorrectStrong = ConfigData.getAsBoolean("stepCorrectStrongs", "yes")
    val strongs = Dom.getAllNodesBelow(rootNode)
      .filter { m_FileProtocol.isStrongsNode(it) }
      .filter { m_FileProtocol.attrName_strong() in it }
    strongs.forEach { doStrong(it) }
    collapseNestedStrongs(strongs)
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
  /* Some texts nest Strongs. */

  private fun collapseNestedStrongs (strongs: List<Node>)
  {
    /**************************************************************************/
    val nodeMap = IdentityHashMap<Node, Boolean>()
    strongs.forEach { nodeMap[it] = true }



    /**************************************************************************/
    for (node in nodeMap.keys)
    {
      if (!nodeMap[node]!!)
        continue

      val children: MutableList<Node> = mutableListOf()
      var p = node
      while (true)
      {
        val firstChild = p.firstChild
        if (m_FileProtocol.tagName_strong() != Dom.getNodeName(firstChild))
          break

        if (m_FileProtocol.attrName_strong() !in firstChild)
          break

        children.add(firstChild)
        nodeMap[firstChild] = false
        p = firstChild
      }

      if (children.isEmpty())
        continue

      var revisedStrong = node[m_FileProtocol.attrName_strong()]!!
      children.forEach { revisedStrong += " " + it[m_FileProtocol.attrName_strong()]!! }
      node[m_FileProtocol.attrName_strong()] = revisedStrong
      children.reversed().forEach { descendant -> Dom.promoteChildren(descendant); Dom.deleteNode(descendant) }
    }

//    Dbg.d(allNodes[0].ownerDocument)
  } // fun


  /****************************************************************************/
  /* Processes a single Strongs node. */

  private fun doStrong (node: Node)
  {
    var prefix = ""

    val rawElts = node[m_FileProtocol.attrName_strong()]!!.uppercase().replace("STRONG:", "").split("\\W+".toRegex())

    val strongsElts = rawElts
      .filter { it.isNotEmpty() } // Not all char:w's have a Strong's entry.  In USX, for instance, they may simply mark a glossary entry.
      .map {
        var strong = it.trim()
        val orig = strong

        if (strong.substring(0, 1).uppercase() in "GH")
        {
          prefix = strong.substring(0, 1).uppercase() // I believe uppercase is ok, and possibly that it is required.
          strong = strong.substring(1)
        }

        val suffix = if (strong.last().isLetter()) strong.last().toString().uppercase() else "" // See if there's an alphabetic suffix.

        strong = strong.substring(0, strong.length - suffix.length) // Get rid of any suffix.

        if (strong.length >= 5 && strong[0] == '0') strong = strong.substring(1) // If the result is 5 or more characters and starts with '0', we have a leading zero we don't want.

        if (strongIsValidish(prefix, strong) && m_CorrectStrong)
        {
          strong = prefix + "0".repeat(4 - strong.length) + strong // If too short, prepend leading zeroes.
          strong += suffix
          strong = getCorrection(strong)
          "strong:$strong"
        }
        else
          "strong:$orig"
      }

    if (strongsElts.isNotEmpty())
      node[m_FileProtocol.attrName_strong()] = strongsElts.joinToString(",")
  }


  /****************************************************************************/
  companion object
  {
    /**************************************************************************/
    /* Returns a corrected value if the input is subject to correction.
       Otherwise returns the 'uncorrected' value. */

    private fun getCorrection (uncorrected: String): String
    {
      if (m_Corrections.isEmpty())
        readCorrectionsData()

      return m_Corrections[uncorrected] ?: uncorrected
    }


    /**************************************************************************/
    private fun readCorrectionsData ()
    {
      FileLocations.getInputStream(FileLocations.getStrongsCorrectionsFilePath())!!.bufferedReader().use { it.readText() } .lines()
        .forEach {
          val line = it.trim()
          if (line.isNotEmpty() && '#' != line[0])
          {
            val parts = line.split("=>")
            m_Corrections[parts[0].trim()] = parts[1].trim()
          }
        }
    }


    /**************************************************************************/
    private fun strongIsValidish (prefix: String, strong: String): Boolean
    {
      val ok = prefix in "GH" && strong.length <= 4 && null != strong.toIntOrNull()
      if (!ok && m_CorrectStrong) Logger.warning("Invalid Strong: $prefix$strong.")
      return ok
    }


    /**************************************************************************/
    private val m_Corrections: MutableMap<String, String> = TreeMap(String.CASE_INSENSITIVE_ORDER)
    private var m_CorrectStrong = true
  }
}

