package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.ref.RefRange
import org.stepbible.textconverter.support.shared.Language
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node

/****************************************************************************/
/**
 * Expands elisions which are marked as such in the raw text, or ones
 * generated as a consequence of table expansion.
 *
 * Temporary attributes are used as follows:
 *
 * - inTableElision: The processing looks for this on the incoming sid
 *   to determine whether it is processing a table or a plain vanilla elision.
 *
 * - _inElision is added to all sids which form part of the elision.
 *
 * - _emptyVerseType is added to the empty sids which make up the
 *   elision.  It will have the value 'elision' or 'tableElision'.
 *
 * - _originalId appears on the master sid only, and gives the original
 *   (pre-expansion) sid for the elision.
 *
 * - _masterForElisionOfLength is added to the master sid only,
 *   and gives a count of the number of elements making up the elision.
 *
 *
 * A footnote is added to the master sid, but only where the number of elements
 * in the elision exceeds two.
 *
 * @author ARA "Jamie" Jamieson
 */

class SE_ElisionHandler (dataCollection: X_DataCollection): SE(dataCollection)
{
  /****************************************************************************/
  /* NOTE: Previous versions of this code assumed that verse ends were already
     in place.  However, that strikes me now as a complication: it is simpler
     to run this at a time when we have only sids, and then insert the eids
     later. */
  /****************************************************************************/





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Public / Protected                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun myPrerequisites() = listOf(ProcessRegistry.VerseMarkersReducedToSidsOnly, ProcessRegistry.TablesRestructured, !ProcessRegistry.BasicVerseEndPositioning, !ProcessRegistry.EnhancedVerseEndPositioning)
  override fun thingsIveDone() = listOf(ProcessRegistry.ElisionsExpanded)


  /****************************************************************************/
  override fun processRootNodeInternal (rootNode: Node) = doIt(rootNode)





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

    Dbg.reportProgress("Handling elisions for ${m_FileProtocol.getBookAbbreviation(rootNode)}.")

    rootNode.findNodesByAttributeName(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid())
      .filter { m_FileProtocol.readRefCollection(it, m_FileProtocol.attrName_verseSid()).isRange() }
      .forEach { processElision(it) }
  }


  /****************************************************************************/
  private fun addTemporaryAttributesToEmptyVerse (verse: Node, elisionType: String)
  {
    NodeMarker.setElisionType(verse, elisionType)
    NodeMarker.setEmptyVerseType(verse, elisionType)
  }


  /****************************************************************************/
  private fun addTemporaryAttributesToMasterVerse (verse: Node, elisionType: String)
  {
    val sidAsString = verse[m_FileProtocol.attrName_verseSid()]!!
    val rc = m_FileProtocol.readRefCollection(sidAsString)
    NodeMarker.setOriginalId(verse, rc.toString()) // Force to USX format.
    NodeMarker.setElisionType(verse, elisionType)
    NodeMarker.setMasterForElisionOfLength(verse, "" + (rc.getAllAsRefs().size + 1))
  }


  /****************************************************************************/
  /* We need a little extra jiggery-pokery here when dealing with elisions.
     I can _read_ the data for an elision regardless of whether it has subverses
     or not.  However, converting a range which includes subverses into
     a collection of refKeys is a bit fraught, or it is if the range crosses a
     verse boundary, because I don't necessarily know how many subverses there
     are in the lower portions.  Given this, I didn't set up getAllAsRefKeys to
     cope with a subverse range which runs across boundaries.  However, there
     are some special cases which we _can_ cope with. */

   private fun getRefKeysForRefRange (sid: String): List<RefKey>
  {
    /**************************************************************************/
    val rc = m_FileProtocol.readRefCollection(sid)
    val refRange = rc.getElements()[0] as RefRange
    val refLow = refRange.getFirstAsRef()
    val refHigh = refRange.getLastAsRef()



    /**************************************************************************/
    /* No subverses -- easy. */

    if (!refLow.hasS() && !refHigh.hasS())
      return refRange.getAllAsRefKeys()



    /**************************************************************************/
    /* If both are in the same verse, I think again that the underlying
       facilities will just cope, regardless of whether either contains a
       subverse.  Or at least I'll assume that until I discover otherwise. */

    if (refLow.toRefKey_bcv() == refHigh.toRefKey_bcv())
      return refRange.getAllAsRefKeys()




    /**************************************************************************/
    /* We now know that we're spanning a verse boundary.  If the low ref has
       a subverse, we're rather stuck, because we don't know of there are any
       other subverses which we have to cope with.  For want of anything better
       to do, let's assume it's the last subverse.  The steps below are
       therefore as follows ...

       1. Create a range based purely upon the first and last _verses_,
          ignoring subverses, and turn it into a list of refKeys.

       2. If the low ref has a subverse, replace the first refKey in this list
          by one which has this subverse setting.  This now gives us the
          correct starting point, assuming that it is indeed reasonable to
          pretend there are no further subverses.

       3. If the high ref has a subverse, things become more complicated.  The
          list as it stands will contain just an entry for the high verse
          itself, so to a first approximation we can simply generate a range
          which covers subverse (a) up to the last subverse and append that.
          The only question then is whether we retain the verse, or whether
          we start with subverse (a).  I suspect, depending upon circumstances,
          that both answers are wrong. */

    val refKeys = RefRange(Ref.rd(refLow.toRefKey_bcv()), Ref.rd(refHigh.toRefKey_bcv())).getAllAsRefKeys().toMutableList()

    if (refLow.hasS()) // Deal with bottom end.
    {
      Logger.warning("Cross-verse range starts at a subverse; assumed there are no later subverses at the low end: $sid.")
      refKeys[0] = Ref.setS(refKeys[0], refLow.getS())
    }

    if (refHigh.hasS())
    {
      Logger.warning("Cross-verse range ends at a subverse; assumed that the high verse should be included in its own right: $sid.")
      val refKeyHigh = refHigh.toRefKey()
      for (s in 1 .. refHigh.getS())
        refKeys.add(Ref.setS(refKeyHigh, s))
    }

    return refKeys
}


  /****************************************************************************/
  /* For general elisions (at present), the empty verses come first and then
     the master verse.  For table elisions, the master comes first followed by
     the empty verses. */

  private fun processElision (verse: Node)
  {
    /**************************************************************************/
    //Dbg.dCont(Dom.toString(verse), "!a")



    /**************************************************************************/
    val refKeys = getRefKeysForRefRange(verse[m_FileProtocol.attrName_verseSid()]!!)
    IssueAndInformationRecorder.elidedVerse(refKeys, verse[m_FileProtocol.attrName_verseSid()]!!)

    if ("tableElision" == NodeMarker.getElisionType(verse))
       refKeys.subList(1, refKeys.size).forEach {
          val nodeList = m_EmptyVerseHandler.createEmptyVerseForElision(verse.ownerDocument, it, false) // Start, content, and optionally footnote (but no eid).
          m_FileProtocol.updateVerseSid(verse, refKeys[0])
          Dom.insertNodesAfter(verse, nodeList)
       }
     else
       refKeys.subList(0, refKeys.size - 1).forEach {
         val nodeList = m_EmptyVerseHandler.createEmptyVerseForElision(verse.ownerDocument, it, false) // Start, content, and optionally footnote (but no eid).
         addTemporaryAttributesToEmptyVerse(nodeList[0], "elision")
         m_FileProtocol.updateVerseSid(verse, refKeys.last())
         Dom.insertNodesBefore(verse, nodeList)
      }

    addTemporaryAttributesToMasterVerse(verse, NodeMarker.getElisionType(verse) ?: "elision")
    m_FileProtocol.updateVerseSid(verse, refKeys.last())
    if (refKeys.size <= 2) return // No footnote on master verse if there is only one other verse in the elision.

    val footnote = m_FileProtocol.makeFootnoteNode(verse.ownerDocument, m_FileProtocol.readRefCollection(verse[m_FileProtocol.attrName_verseSid()]!!).getLowAsRefKey(), Translations.stringFormat(Language.Vernacular, "V_elision_containsTextFromOtherVerses", m_FileProtocol.readRefCollection(verse[m_FileProtocol.attrName_verseSid()]!!)))
    Dom.insertNodeAfter(verse, footnote)
    NodeMarker.setAddedFootnote(verse)
  }


  /****************************************************************************/
  private val m_EmptyVerseHandler = EmptyVerseHandler(dataCollection)
}
