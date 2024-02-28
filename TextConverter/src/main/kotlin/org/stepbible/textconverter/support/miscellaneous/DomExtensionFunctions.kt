package org.stepbible.textconverter.support.miscellaneous

import org.w3c.dom.*
import java.util.*
import kotlin.collections.ArrayList

/******************************************************************************/
/**
* Extension functions for DOM items.  This file duplicates much of the
* functionality in the Dom collection.
*
* @author ARA "Jamie" Jamieson
*/


/******************************************************************************/
/**
 * Returns a list containing all nodes with a given name, or null if not
 * found.
 *
 *
 * IMPORTANT: You can't use this method to find text nodes.
 *
 * @param nodeName Node name.
 * @param includeRoot If true, and if the node being operated is of the required
 *          type, that node is included at the front of the returned list.
 * @return List of nodes.
 */

 fun Node.findNodesByName (nodeName: String?, includeRoot: Boolean = false): List<Node>
{
  val res = toListOfNodes((this as Element).getElementsByTagName(nodeName)).toMutableList()
  if (includeRoot && nodeName == this.nodeName) res.add(0, this)
  return res
}


/******************************************************************************/
/**
 * Returns the attributes of the given node.  (The map may be empty if there
 * are no attributes.)
 *
 * @return  Attributes.
 */

fun Node.getAttributeMap (): Map<String, String>
{
  val res: MutableMap<String, String> = TreeMap<String, String>(java.lang.String.CASE_INSENSITIVE_ORDER)
  if (!hasAttributes()) return res
  val attributeMap: NamedNodeMap = this.attributes
  for (i in 0..< attributeMap.length)
  {
      val n: Node = attributeMap.item(i)
      res[n.nodeName] = n.nodeValue
  }

  return res
}


/******************************************************************************/
/**
 * Generates a printable representation of a node and its attributes.  Note that
 * sadly this cannot be called toString -- or at any rate there is no point in
 * doing so -- because it is shadowed by the toString within the Node class.
 *
 * @return String.
 */

fun Node.stringify (): String
{
    val nodeName = this.nodeName
    if ("#text" == nodeName) return "#text: " + this.textContent else if ("#comment" == nodeName) return "#comment: " + this.textContent
    var res = "$nodeName "
    var attributes = ""

    if (this.hasAttributes())
    {
      val attribs = getAttributeMap()
      for (k in attribs.keys) attributes += k + ": " + attribs[k] + ", "
    }

    if (attributes.isNotEmpty()) attributes = attributes.substring(0, attributes.length - 2)
    res += attributes
    return res
}


/********************************************************************************/
/**
 * Converts a NodeList structure to List&lt;Node&gt;>.
 *
 * @param nl Node list to be converted.
 * @return Revised structure.
 */

private fun toListOfNodes (nl: NodeList): List<Node>
{
  val res: MutableList<Node> = ArrayList(nl.length)
  for (i in 0 until nl.length) res.add(nl.item(i))
  return res
}


