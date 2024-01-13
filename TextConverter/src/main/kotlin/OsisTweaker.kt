/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.w3c.dom.Document
import org.w3c.dom.Node



/******************************************************************************/
/**
 * Tweaks OSIS.
 *
 * Over the years we have discovered a number of issues with the way STEP
 * renders modules.  These are effectively bugs in STEP.  However, some of them
 * can be fixed by tweaking the OSIS which feeds into the modules.  Where this
 * is feasible at reasonable cost (and where the resulting OSIS could still be
 * used by third parties), it is appreciably easier to adopt this expedient
 * rather than attempt to work out how to fix STEP: obviously fixing STEP is the
 * correct approach, but we don't really presently have the expertise to do it.
 *
 * This class takes an existing OSIS file and applies any tweaks which we have
 * identified.  (The fact that it works from an existing OSIS file means that
 * we can apply it both to USX / VL based modules and to ones where the input
 * is itself OSIS.)
 *
 * As an example of the kind of thing we are talking about here, we recently
 * discovered that with something like
 *
 *     ...>, <note ...
 *
 * the comma is sometimes dropped (but not always).  Introducing
 *
 *    <hi type='normal'/>
 *
 * before all note tags seems to fix this, and aside from the fact that it makes
 * the OSIS file larger, has no real adverse consequences either for us or, we
 * imagine, for third parties.
 *
 * @author ARA "Jamie" Jamieson
 */

object OsisTweaker
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Note that in this method, we assume that the OSIS file has already been
     created, and we apply changes which require an understanding of the DOM
     structure.  Compare and contrast with the pre method. */

  fun process (inputFilePath: String)
  {
    m_Document = Dom.getDocument(inputFilePath)
    doChanges()
    Dom.outputDomAsXml(m_Document, FileLocations.getInternalTempOsisFilePath(), null)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Private                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  private fun doChanges ()
  {
    doCommaNote()
    doLg()
    doUnacceptableTextCharacters()
  }


  /****************************************************************************/
  /* We have discovered that with something like:

        ...>, <note ...

     the comma is sometimes dropped (but not always).  Introducing

        <hi type='normal'/>

     immediately before the note seems to fix this, and since it doesn't actually
     do anything, should not adversely affect third parties using the OSIS
     (although they may be a bit bemused by it).

     I attempt to be slightly more refined here, in that if it looks as though
     the text has already been tweaked in this manner, I don't do anything. */

  private fun doCommaNote (): Boolean
  {
    var changed = false

    fun insertNode (before: Node)
    {
      val newNode = Dom.createNode(m_Document, "<hi type='normal'/>")
      Dom.insertNodeBefore(before, newNode)
      changed = true
    }

    Dom.findNodesByName(m_Document, "note")
      .filter { null != it.previousSibling && "#text" != Dom.getNodeName(it.previousSibling) && "," == it.previousSibling.textContent.trim() }
      .forEach { insertNode(it) }

    return changed
  }


  /****************************************************************************/
  /* Remove <lg> and promote children.  In theory, lg is required in OSIS (it's
     used to wrap list-type things).  However, it introduces excessive vertical
     whitespace when rendered, and nothing seems to reply upon it. */

  private fun doLg ()
  {
    Dom.findNodesByName(m_Document, "lg"). forEach {
      Dom.promoteChildren(it)
      Dom.deleteNode(it)
    }
  }


  /****************************************************************************/
  /* Removes invalid characters from text nodes.  In particular, DIB inserts
     \u000c because it results in nice formatting in his editor.  Unfortunately,
     this is an invalid character. */

  private fun doUnacceptableTextCharacters ()
  {
    fun doIt (node: Node)
    {
      val s1 = node.textContent
      val s2 = s1.replace("\u000c", "")
      if (s1.length != s2.length)
        node.textContent = s2
    }


    Dom.findAllTextNodes(m_Document).forEach { doIt(it)}
  }


  /****************************************************************************/
  private lateinit var m_Document: Document
}

