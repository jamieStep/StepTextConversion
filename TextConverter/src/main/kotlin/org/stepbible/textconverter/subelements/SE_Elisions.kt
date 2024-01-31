package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.Translations
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.shared.Language
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node

/****************************************************************************/
/**
 * Expands elisions.
 *
 * @author ARA "Jamie" Jamieson
 */

open class SE_Elisions protected constructor (fileProtocol: Z_FileProtocol, emptyVerseHandler: Z_EmptyVerseHandler): SE
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun process (rootNode: Node) = doIt(rootNode)





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  protected val m_EmptyVerseHandler = emptyVerseHandler
  protected val m_FileProtocol = fileProtocol





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun doIt (rootNode: Node)
  {
    if (!ConfigData.getAsBoolean("stepExpandElisions")) return
    Dbg.reportProgress("Handling elisions for ${m_FileProtocol.getBookCode(rootNode)}.")
    val elidedVerses = Dom.findNodesByAttributeName(rootNode, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid()).filter { m_FileProtocol.readRefCollection(it, m_FileProtocol.attrName_verseSid()).isRange() }
    elidedVerses.forEach { processElision(it) }
  }


  /****************************************************************************/
  private fun processElision (verse: Node)
  {
    val refKeys = m_FileProtocol.readRefCollection(verse, m_FileProtocol.attrName_verseSid()).getAllAsRefKeys()
    IssueAndInformationRecorder.elidedVerse(refKeys.last(), Dom.toString(verse))

    refKeys.subList(0, refKeys.size - 1).forEach {
         val nodeList = m_EmptyVerseHandler.createEmptyVerseForElision(verse.ownerDocument, it) // Start, end, content, and optionally footnote.
         Dom.insertNodesBefore(verse, nodeList)
    }

    if (refKeys.size <= 2) return // No footnote on master verse if there is only one other verse in the elision.
    val footnote = m_FileProtocol.makeFootnoteNode(verse.ownerDocument, Ref.rdUsx(verse["sID"]!!).toRefKey(), Translations.stringFormat(Language.Vernacular, "V_elision_containsTextFromOtherVerses"))
    Dom.insertNodeAfter(verse, footnote)
    Utils.addTemporaryAttribute(verse, "_temp_inElision", "y")
  }
}


/****************************************************************************/
object Osis_SE_Elisions: SE_Elisions(Osis_FileProtocol, Osis_EmptyVerseHandler)
object Usx_SE_Elisions: SE_Elisions(Usx_FileProtocol, Usx_EmptyVerseHandler)
