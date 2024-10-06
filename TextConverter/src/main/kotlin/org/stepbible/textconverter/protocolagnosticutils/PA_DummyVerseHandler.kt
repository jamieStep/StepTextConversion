package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.applicationspecificutils.NodeMarker

import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefBase
import org.w3c.dom.Node

/******************************************************************************/
/**
* Common base class for protocol-agnostic utilities.
*
* @author ARA "Jamie" Jamieson
*/

object PA_DummyVerseHandler
{
  /****************************************************************************/
  /**
   *  Inserts dummy verse sids at the ends of chapters so we always have
   *  something we can insert stuff *before*.
   *
   *  @param dataCollection
   */

  fun insertDummyVerses (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    fun addDummyVerseToChapter (chapterNode: Node)
    {
      val dummySidRef = dataCollection.getFileProtocol().readRef(chapterNode[dataCollection.getFileProtocol().attrName_chapterSid()]!!)
      dummySidRef.setV(RefBase.C_BackstopVerseNumber)
      val dummySidRefAsString = dataCollection.getFileProtocol().refToString(dummySidRef.toRefKey())
      val dummySid = chapterNode.ownerDocument.createNode("<verse ${dataCollection.getFileProtocol().attrName_verseSid()}='$dummySidRefAsString'/>")
      NodeMarker.setDummy(dummySid)
      chapterNode.appendChild(dummySid)
    }



    /**************************************************************************/
    Rpt.report(level = 1, "Adding dummy verses to simplify later processing ...")
    with(ParallelRunning(true)) {
      run {
        dataCollection.getRootNodes().forEach { rootNode ->
          asyncable {
            Rpt.reportBookAsContinuation(dataCollection.getFileProtocol().getBookAbbreviation(rootNode))
            rootNode.findNodesByName(dataCollection.getFileProtocol().tagName_chapter()).forEach(::addDummyVerseToChapter) }
        } // forEach
      } // run
    } // parallel
  } // fun


  /****************************************************************************/
  /**
   * Removes the dummy verses created by [insertDummyVerses].
   *
   * @param dataCollection
   */

  fun removeDummyVerses (dataCollection: X_DataCollection)
  {
    Rpt.report(level = 1, "Removing any dummy verses ...")
    with(ParallelRunning(true)) {
      run {
        dataCollection.getRootNodes().forEach { rootNode ->
          asyncable {
            Rpt.reportBookAsContinuation(dataCollection.getFileProtocol().getBookAbbreviation(rootNode))
            rootNode.findNodesByAttributeName(dataCollection.getFileProtocol().tagName_verse(), dataCollection.getFileProtocol().attrName_verseSid())
                    .filter { RefBase.C_BackstopVerseNumber == Ref.rd(dataCollection.getFileProtocol().getSidAsRefKey(it)).getV() }
                    .forEach { Dom.deleteNode(it) }
          } // asyncable
        } // forEach
      } // run
    } // parallel
  } // fun
}