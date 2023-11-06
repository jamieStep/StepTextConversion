
package org.stepbible.preprocessor

import org.w3c.dom.*

/****************************************************************************/
/* This isn't needed actually to do anything, but IDEA seems to misbehave
   without it -- syntax highlighting and error reporting doesn't work, and
   when you build artifacts (ie the JAR which we need), the code generated
   here doesn't seem to turn up. */

fun main(args: Array<String>)
{
}

class Preprocessor
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * The text converter compares original text with processed text.  By default,
  * the 'original' text is exactly as it appears in the raw USX.  If this
  * present class has messed around with the text, this needs to generate a
  * modified version of the raw text which will be the same as the enhanced USX.
  *
  * For example, if the raw text contained '¶' which the preprocess method of
  * this class has removed, getTextForValidation needs to remove '¶' from the
  * text passed to it.
  *
  * @param text Text to be processed.
  *
  * @return Revised text.
  */

  fun getTextForValidation (text: String): String
  {
    return text.replace("¶", "")
  }


  /****************************************************************************/
  /**
  * The method accessed from the main text converter.  Applies any necessary
  * changes to the document.
  *
  * @param doc Document to be modified.
  *
  * @return List of errors, warnings and informationals.  Each will begin with
  *   'ERROR: ', 'WARNING: ' or 'INFORMATION: '.
  */

  fun preprocess (doc: Document): List<String>?
  {
    val textNodes = findAllTextNodes(doc)

    textNodes.filter { "¶" in it.textContent } .forEach { it.textContent = it.textContent.replace("¶", "") }

    textNodes.filter { 1 == it.textContent.length } .forEach {
      val parent = it.parentNode
      if ("char" == parent.nodeName && "bd" == getAttribute(parent, "style"))
      {
        val parentSibling = parent.nextSibling
        if ("#text" == parentSibling.nodeName && !parentSibling.textContent.startsWith(" "))
        {
          val savedContent = it.textContent
          parent.removeChild(it)
          parentSibling.textContent = savedContent + parentSibling.textContent
          parent.parentNode.removeChild(parent)
        }
      }
    }

    return null
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun findAllTextNodes (n: Node): List<Node>
  {
    val res: MutableList<Node> = ArrayList()
    val children = n.childNodes
    for (i in 0..< children.length)
    {
        val child = children.item(i)
        if (child.nodeType == Node.TEXT_NODE) res.add(child) else res.addAll(findAllTextNodes(child))
    }

    return res
  }

  /****************************************************************************/
  /**
   * Returns a given attribute value, or null if there is no such attribute.
   *
   * @param node Node.
   * @param attributeName  Name of attribute.
   * @return Attribute value, or null
   */

  private fun getAttribute (node: Node, attributeName: String): String?
  {
    if (!node.hasAttributes()) return null
    val n = node.attributes.getNamedItem(attributeName) ?: return null
    return n.nodeValue
  }
}

