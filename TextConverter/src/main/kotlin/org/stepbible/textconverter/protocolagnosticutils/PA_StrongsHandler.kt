package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.w3c.dom.Node
import java.util.*


/****************************************************************************/
/**
 * Canonicalises Strong's references:
 *
 * - Some texts give truncated references -- eg H12.  I expand this to
 *   four numbers by adding leading zeroes.
 *
 * - Some texts have an extra leading zero on the front of references (such
 *   that purely numeric values look like 01234).  I gather this was a
 *   convention before the convention of prepending 'H' or 'G' was adopted.
 *   I truncate these so that the leading zero is removed.
 *
 * - Some texts give multiple Strong's references within a single tag, but
 *   put 'H' or 'G' only on the first, using the convention that the later
 *   ones default to the original prefix.  These I make explicit.
 *
 * - Some texts have additional information within the lemma attribute --
 *   eg lemma = "strong:G0520 lemma.TR:απαγαγετε".  This is ok, and is
 *   retained.
 *
 * - There are a few Strong's numbers which need to be corrected.  I handle
 *   this.
 *
 * - I remove any superfluous spaces, and prepend the result with 'strong:'.
 *
 * - Some texts used Strong's tags purely to give glossary entries.  I
 *   delete these tags (but retain their content).
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_StrongsHandler: PA(), ObjectInterface
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
  * Processes all Strong's tags.
  * 
  * @param dataCollection
  */
  
  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection)
    with(ParallelRunning(true)) {
      run {
        Rpt.reportWithContinuation(level = 1, "Handling Strongs ...") {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable { PA_StrongsHandlerPerBook(m_FileProtocol).processRootNode(rootNode) }
          } // forEach
        } //withProcessingBooks
      } // run
    } // with
  } // fun
}




/******************************************************************************/
private class PA_StrongsHandlerPerBook (val m_FileProtocol: X_FileProtocol)
{
  /****************************************************************************/
  /**
  * Canonicalises Strong's markup.
  *
  * @param rootNode Root of document, or of portion of document being
  *   processed.
  */

  fun processRootNode (rootNode: Node)
  {
    Rpt.reportBookAsContinuation(m_FileProtocol.getBookAbbreviation(rootNode))
    processRootNode1(rootNode)
  }


  /****************************************************************************/
  private fun processRootNode1 (rootNode: Node)
    {
    //Dbg.d(rootNode.ownerDocument)
    val strongs = Dom.getAllNodesBelow(rootNode).filter { m_FileProtocol.isStrongsNode(it) }

    strongs.forEach { // The code here makes the assumption that USX and OSIS both use the same attributes.  There doesn't actually seem to be any documentation for the USX attreibutes.
      if ((it["lemma"] ?: "").isNotEmpty())
        doStrong(it)
      else if ((it["gloss"] ?: "").isEmpty() && // A glossary-only entry.
               (it["morph"] ?: "").isEmpty())
      {
        Dom.promoteChildren(it)
        Dom.deleteNode(it)
      }
    }

    val nStrongs = strongs.size - collapseNestedStrongs(strongs)
    if (nStrongs > 0) IssueAndInformationRecorder.setStrongs()
  }


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

      val children: MutableList<Node> = mutableListOf()
      var p = node
      while (true)
      {
        val firstChild = p.firstChild

        if (null == firstChild) // This shouldn't happen -- we should never have an entirely empty Strong's tag -- but I've seen texts where it does.
        {
          val text = "Empty Strong's reference: ${Dom.toString(node)}.  (This may not be a problem -- USX char:w is used for a number of purposes, not just for Strong's.)"
          Logger.warning(text)
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

  private val C_MultiStrongSeparator = "(\\s*,\\s*|\\s+)".toRegex() // Comma optionally preceded and / or followed by spaces, or just one or more spaces.

  private fun doStrong (node: Node)
  {
    /**************************************************************************/
    var prefix = ""



    /**************************************************************************/
    fun rejigStrongs (attr: String): String
    {
      var strong = attr.trim()
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



//    /**************************************************************************/
//    val rawElts = node[m_FileProtocol.attrName_strong()]!!.replace("(?i)STRONG:".toRegex(), "").split(C_MultiStrongSeparator)
//    val strongsElts = rawElts.map(::rejigStrongs).filter { "0000" !in it } // 0000 is used in at least one text as a dummy entry and is to be ignored.
//
//    if (strongsElts.isEmpty()) // The only element(s) were 0000.
//    {
//      node -= m_FileProtocol.attrName_strong() // Remove the Strong's attribute.
//      if (!node.hasAttributes()) // And if that leaves no attributes at all, remove the node, but retain its children.
//      {
//        Dom.promoteChildren(node)
//        Dom.deleteNode(node)
//      }
//    }
//    else // Replace the Strong's attribute with the tidied up elements.
//      node[m_FileProtocol.attrName_strong()] = strongsElts.joinToString(" ")
//  }
//
//
    /**************************************************************************/
    val strongsElts = node[m_FileProtocol.attrName_strong()]!!.trim().replace("(?i)STRONG:".toRegex(), "").split(C_MultiStrongSeparator)
    node[m_FileProtocol.attrName_strong()] = strongsElts.joinToString(" "){ rejigStrongs(it) }
  }


  /****************************************************************************/
  /* Returns a corrected value if the input is subject to correction.
     Otherwise returns the 'uncorrected' value. */

  private fun getCorrection (uncorrected: String): String
  {
    if (m_Corrections.isEmpty())
      readCorrectionsData()

    return m_Corrections[uncorrected] ?: uncorrected
  }


  /****************************************************************************/
  private fun readCorrectionsData ()
  {
    FileLocations.getInputStream(FileLocations.getStrongsCorrectionsFilePath()).first!!.bufferedReader().use { it.readText() } .lines()
      .forEach {
        val line = it.trim()
        if (line.isNotEmpty() && '#' != line[0])
        {
          val parts = line.split("=>")
          m_Corrections[parts[0].trim()] = parts[1].trim()
        }
      }
  }


  /****************************************************************************/
  private fun strongIsValidish (prefix: String, strong: String): Boolean
  {
    var ok = prefix in "GH" && strong.length <= 4
    val strongAsInt = strong.toIntOrNull() ?: -1
    ok = strongAsInt > 0
    if (!ok && 0 != strongAsInt) Logger.warning("Invalid Strong: $prefix$strong.") // Don't issue a warning for 0000, because some texts use that where there is no Strong's number for a word.
    return ok
  }


  /****************************************************************************/
  private val m_Corrections: MutableMap<String, String> = TreeMap(String.CASE_INSENSITIVE_ORDER)
}
