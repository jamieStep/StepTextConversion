package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.getAllNodesBelow
import org.stepbible.textconverter.support.shared.FeatureIdentifier
import org.stepbible.textconverter.support.stepexception.StepException
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node


/****************************************************************************/
/**
 * Collects certain ad hoc information needed to work out what to put into the
 * Sword configuration file.
 *
 * @author ARA "Jamie" Jamieson
 */

class SE_TextAnalyser (dataCollection: X_DataCollection): SE(dataCollection)
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun thingsIveDone () = listOf(ProcessRegistry.FeatureDataCollected)


  /****************************************************************************/
  override fun processRootNodeInternal (rootNode: Node)
  {
    Dbg.reportProgress("Collecting feature details for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")
    countVersesInParas(rootNode)
    getSampleTextForConfigData(rootNode)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* For some reason I haven't fathomed, the Sword config file needs to have a
     setting which relates to para usage. I _think_ what they require is to know
     whether there are any paras containing more than one verse, although I have
     to say I'm not too clear about that -- what they actually seem to be
     concerned with is whether we have verse-per-line or not. */

  private fun countVersesInParas (rootNode: Node)
  {
    /**************************************************************************/
    /* In the USX variant of this, we may be called multiple times, once for
       each book.  There's no point in processing all of them if we already
       know that we have multiple verses in a para. */

    if (m_AlreadyKnowResultForCountVersesInParas)
      return



    /**************************************************************************/
    fun countVerses (x: Node)
    {
      if (!m_FileProtocol.isParaWhichCouldContainMultipleVerses(x))
        return

      val descendants = Dom.getAllNodesBelow(x)
      val n = descendants.count { m_FileProtocol.tagName_verse() == Dom.getNodeName(it) }
      if (n > 1)
      {
        m_AlreadyKnowResultForCountVersesInParas = true
        IssueAndInformationRecorder.setHasMultiVerseParagraphs()
        FeatureIdentifier.setHasMultiVerseParagraphs()
        throw StepException("") // No need for further processing -- we now know all that's needed.
      }
    }



    /**************************************************************************/
    try
    {
      Dom.findNodesByName(rootNode, "para", false).forEach { countVerses(it) }
    }
    catch (_: StepException)
    {
      // Here simply so countVerses can exit prematurely.
    }
  }


  /****************************************************************************/
  /* Sets the sample text needed by ConfigData to determine text direction.
     This looks for the first text canonical text node after the first verse
     node whose non-blank length is 'long enough'. */

  private fun getSampleTextForConfigData (rootNode: Node)
  {
    if (m_AlreadyGotSampleText)
      return

    var hadVerse = false

    val x = rootNode.getAllNodesBelow()
      .first { if (m_FileProtocol.tagName_verse() == Dom.getNodeName(it)) hadVerse = true;
               hadVerse && Dom.isTextNode(it) && m_FileProtocol.isCanonicalNode(it) && it.textContent.replace("\\s+".toRegex(), "").length > 5 }.textContent

    ConfigData.setSampleText(x)
  }


  /****************************************************************************/
  private val m_AlreadyGotSampleText = false
  private var m_AlreadyKnowResultForCountVersesInParas = false
}
