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
 * -- Some texts have additional information within the lemma attribute --
 *    eg lemma = "strong:G0520 lemma.TR:απαγαγετε".  This is ok, and is
 *    retained.
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
    //Dbg.d(rootNode.ownerDocument)
    Dbg.reportProgress("Handling Strongs for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    val strongs = Dom.getAllNodesBelow(rootNode)
      .filter { m_FileProtocol.isStrongsNode(it) }
      .filter { m_FileProtocol.attrName_strong() in it }
    strongs.forEach { doStrong(it) }
    val nStrongs = strongs.size - collapseNestedStrongs(strongs)
    if (nStrongs > 0) IssueAndInformationRecorder.setStrongs()
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  //private var overallCount = 0 // $$$
  /****************************************************************************/
  /* Some texts nest Strongs. */

  private fun collapseNestedStrongs (strongs: List<Node>): Int
  {
    /**************************************************************************/
    var deletedCount = 0
    val nodeMap = IdentityHashMap<Node, Boolean>()
    strongs.forEach { nodeMap[it] = true }



    /**************************************************************************/
    for (node in nodeMap.keys)
    {
      if (!nodeMap[node]!!)
        continue

      //node["ZZZ"] = (overallCount++).toString() // $$$

      val children: MutableList<Node> = mutableListOf()
      var p = node
      while (true)
      {
        val firstChild = p.firstChild

        if (null == firstChild) // This shouldn't happen -- we should never have an entirely empty Strong's tag -- but I've seen texts where it does.
        {
          val text = "Empty Strong's reference: ${Dom.toString(node)}."
          Logger.warning(text)
          //Dbg.d(Dom.findAncestorByNodeName(p, "chapter")!!)
          //if (1 == ++overallCount) Dbg.d(node.ownerDocument)
          Dbg.d(text)
          Dom.deleteNode(node) // No point in retaining an empty Strong's tag.
          ++deletedCount
          break
        }

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

    return deletedCount
  } // fun


  /****************************************************************************/
  /* Processes a single Strongs node. */

  private val C_ExtendedStrongInfo = "(?i)(G|H)\\d\\d\\d\\d".toRegex()
  private val C_LemmaWithinStrongs = "(?i)^lemma.+".toRegex()
  private val C_MultiStrongSeparator = "(\\s*,\\s*|\\s+)".toRegex() // Comma optionally preceded and / or followed by spaces, or just one or more spaces.

  private fun doStrong (node: Node)
  {
    /**************************************************************************/
    var prefix = ""



    /**************************************************************************/
    fun rejigStrongs (attr: String): String
    {
      var strong = attr.trim()
      if (strong.matches(C_ExtendedStrongInfo))
        return strong.uppercase()
      else if (strong.matches(C_LemmaWithinStrongs))
        return strong

      if (strong.substring(0, 1).uppercase() in "GH")
      {
        prefix = strong.substring(0, 1).uppercase() // I believe uppercase is ok, and possibly that it is required.
        strong = strong.substring(1)
      }

      val suffix = if (strong.last().isLetter()) strong.last().toString().uppercase() else "" // See if there's an alphabetic suffix.

      strong = strong.substring(0, strong.length - suffix.length) // Get rid of any suffix.

      if (strong.length >= 5 && strong[0] == '0') strong = strong.substring(1) // If the result is 5 or more characters and starts with '0', we have a leading zero we don't want.

      return if (strongIsValidish(prefix, strong))
      {
        strong = prefix + "0".repeat(4 - strong.length) + strong // If too short, prepend leading zeroes.
        strong += suffix
        strong = getCorrection(strong)
        "strong:$strong"
      }
      else
        "strong:$attr"
    }



    /**************************************************************************/
    val rawElts = node[m_FileProtocol.attrName_strong()]!!.replace("(?i)STRONG:".toRegex(), "").split(C_MultiStrongSeparator)
    val strongsElts = rawElts.map(::rejigStrongs)
    node[m_FileProtocol.attrName_strong()] = strongsElts.joinToString(" ")
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
      if (!ok) Logger.warning("Invalid Strong: $prefix$strong.")
      return ok
    }


    /**************************************************************************/
    private val m_Corrections: MutableMap<String, String> = TreeMap(String.CASE_INSENSITIVE_ORDER)
  }
}
