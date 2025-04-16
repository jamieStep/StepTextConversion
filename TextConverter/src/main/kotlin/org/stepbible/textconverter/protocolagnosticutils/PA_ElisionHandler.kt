package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.TranslatableFixedText
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefKey
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefRange
import org.stepbible.textconverter.nonapplicationspecificutils.shared.Language
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
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
 * - inElision is added to all sids which form part of the elision.
 *
 * - emptyVerseType is added to the empty sids which make up the
 *   elision.  It will have the value 'elision' or 'tableElision'.
 *
 * - originalId appears on the master sid only, and gives the original
 *   (pre-expansion) sid for the elision.
 *
 * - masterForElisionOfLength is added to the master sid only,
 *   and gives a count of the number of elements making up the elision.
 *
 *
 * A footnote is added to the master sid, but only where the number of elements
 * in the elision exceeds two.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_ElisionHandler: PA(), ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                         Public / Protected                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Processes all elisions.
  *
  * @param dataCollection Data to be processed.
  */

  fun process (dataCollection: X_DataCollection)
  {
    if (!Permissions.okToProcess(Permissions.RestructureAction.ExpandElisions))
      return // At present, I assume we always will expand elisions -- and indeed I think things will go wrong if we don't.

    extractCommonInformation(dataCollection)
    m_EmptyVerseHandler = PA_MissingVerseHandler(dataCollection.getFileProtocol())
    Rpt.reportWithContinuation(level = 1, "Expanding elisions ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach {
            asyncable { PA_ElisonHandlerPerBook(m_FileProtocol, m_EmptyVerseHandler).processRootNode(it) }
          } // forEach
        } // run
      } // Parallel
    } // reportWithContinuation
  } // fun
}




private class PA_ElisonHandlerPerBook (val m_FileProtocol: X_FileProtocol, val m_EmptyVerseHandler: PA_MissingVerseHandler)
{
  /****************************************************************************/
  fun processRootNode (rootNode: Node)
  {
    Rpt.reportBookAsContinuation(m_FileProtocol.getBookAbbreviation(rootNode))
    val attrNameSid = m_FileProtocol.attrName_verseSid()
    val verses = rootNode.findNodesByName(m_FileProtocol.tagName_verse()).groupBy { Dom.hasAttribute(it, attrNameSid) }
    val verseSids = verses[true]!!
    val verseEids = verses[false]!!
    for (ix in verseSids.indices)
      if ('-' in verseSids[ix][attrNameSid]!!)
        processElision(verseSids[ix], verseEids[ix], if (ix + 1 >= verseSids.size) null else verseSids[ix + 1], Dom.findAncestorByNodeName(verseSids[ix], m_FileProtocol.tagName_chapter())!!)
  }


  /****************************************************************************/
  private fun addTemporaryAttributesToEmptyVerse (verse: Node, elisionType: String)
  {
    NodeMarker.setElisionType(verse, elisionType)
    NodeMarker.setEmptyVerseType(verse, elisionType)
  }


  /****************************************************************************/
  private fun addTemporaryAttributesToMasterVerse (verse: Node, elisionType: String, origSidAsString: String, nRefKeys: Int)
  {
    val rc = m_FileProtocol.readRefCollection(origSidAsString)
    NodeMarker.setOriginalId(verse, rc.toString()) // Force to USX format.
    NodeMarker.setElisionType(verse, elisionType)
    NodeMarker.setMasterForElisionOfLength(verse, "" + nRefKeys)
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

  private fun processElision (verseSid: Node, verseEid: Node, nextVerse: Node?, chapterNode: Node)
  {
    /**************************************************************************/
    //Dbg.dCont(Dom.toString(verse), "!a")



    /**************************************************************************/
    val refKeys = getRefKeysForRefRange(verseSid[m_FileProtocol.attrName_verseSid()]!!)
    val origSid = verseSid[m_FileProtocol.attrName_verseSid()]!!
    IssueAndInformationRecorder.elidedVerse(refKeys, origSid)



    /**************************************************************************/
    /* If it's a table elision, the expanded verses need to go before the
       _next_ sid -- unless there _is_ no next sid, because we're at the end
       of the chapter (recognisable because nextVerse will be null).  In this
       case I create a temporary node right at the end of the chapter and do
       the insertions before that, and then delete the temporary node again. */

    if ("tableElision" == NodeMarker.getElisionType(verseSid))
    {
      var insertionPoint = nextVerse
      if (null == nextVerse)
      {
        insertionPoint = Dom.createNode(verseSid.ownerDocument, "<XXX/>")
        chapterNode.appendChild(insertionPoint)
      }

      refKeys.subList(1, refKeys.size).forEach {
        val nodeList = m_EmptyVerseHandler.createEmptyVerseForElision(verseSid.ownerDocument, it, "tableElision") // Start, content, and optionally footnote (but no eid).
          m_FileProtocol.updateVerseSid(verseSid, refKeys[0])
          Dom.insertNodesBefore(insertionPoint!!, nodeList)
          // No need to add a footnote here -- the table processing will already have handled that.
       }

       if (null == nextVerse)
         Dom.deleteNode(insertionPoint!!)

       m_FileProtocol.updateVerseSid(verseSid, refKeys.first())
       m_FileProtocol.updateVerseEid(verseEid, refKeys.first())
    }



    /**************************************************************************/
    else // Not a table elision.
      refKeys.subList(0, refKeys.size - 1).forEach {
        val nodeList = m_EmptyVerseHandler.createEmptyVerseForElision(verseSid.ownerDocument, it) // Start, content, and optionally footnote (but no eid).
        addTemporaryAttributesToEmptyVerse(nodeList[0], "elision")
        m_FileProtocol.updateVerseSid(verseSid, refKeys.last())
        Dom.insertNodesBefore(verseSid, nodeList)
        if (refKeys.size > 2) // No footnote on master verse if there is only one other verse in the elision.
        {
          val footnote = m_FileProtocol.makeFootnoteNode(Permissions.FootnoteAction.AddFootnoteToMasterVerseInElision, verseSid.ownerDocument, m_FileProtocol.readRefCollection(verseSid[m_FileProtocol.attrName_verseSid()]!!).getLowAsRefKey(), TranslatableFixedText.stringFormat(Language.Vernacular, "V_elision_containsTextFromOtherVerses", m_FileProtocol.readRefCollection(verseSid[m_FileProtocol.attrName_verseSid()]!!)))
          if (null != footnote)
          {
            Dom.insertNodeAfter(verseSid, footnote)
            NodeMarker.setAddedFootnote(verseSid)
          }
        }

       m_FileProtocol.updateVerseSid(verseSid, refKeys.last())
       m_FileProtocol.updateVerseEid(verseEid, refKeys.last())
     }



    /**************************************************************************/
    addTemporaryAttributesToMasterVerse(verseSid, NodeMarker.getElisionType(verseSid) ?: "elision", origSid, refKeys.size)
  }
}
