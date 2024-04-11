package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
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
      .map { it.trim().uppercase() } // I believe uppercase is ok, and possibly that it is required.

      .map {// Split out any prefix from the rest of the reference.
        var strong = it.replace("strong:", "")
        if (strong.substring(0, 1) in "GH")
        {
          prefix = strong.substring(0, 1)
          strong = strong.substring(1)
        }

        strong
      }

      .map {// Do everything else.
        var strong = it

        val suffix = if (strong.last().isLetter()) strong.last().toString().uppercase() else "" // See if there's an alphabetic suffix.

        strong = strong.substring(0, strong.length - suffix.length) // Get rid of any suffix.

        if (strong.length >= 5 && strong[0] == '0') strong = strong.substring(1) // If the result is 5 or more characters and starts with '0', we have a leading zero we don't want.

        strong = prefix + "0".repeat(4 - strong.length) + strong // If too short, prepend leading zeroes.

        strong = prefix + strong + suffix
        strong = getCorrection(strong)
        "strong:$strong"
      }

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
      FileLocations.getInputStream(FileLocations.getStrongsCorrectionsFilePath(), null)!!.bufferedReader().use { it.readText() } .lines()
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
    private val m_Corrections: MutableMap<String, String> = TreeMap(String.CASE_INSENSITIVE_ORDER)
  }
}

