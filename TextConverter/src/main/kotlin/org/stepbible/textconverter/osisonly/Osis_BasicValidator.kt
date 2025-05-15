package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.applicationspecificutils.Osis_FileProtocol
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ParallelRunning
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.contains
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.get
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefKey
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefRange
import org.w3c.dom.Node

/*******************************************************************************/
/**
 * Checks the basic structure of the text -- things like are all verses within
 * chapters.  Also records information about any 'interesting' aspects of the
 * text, such as whether it contains elided verses.
 *
 * @author ARA "Jamie" Jamieson
 */

object Osis_BasicValidator
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   *  Checks the basis structure -- are all chapters within books, all verses
   *  within chapters, etc.
   *
   *  @param dataCollection: Data to be processed.
   */

  fun process (dataCollection: X_DataCollection)
  {
    val issuesThunk = IssuesThunk()
    Rpt.reportWithContinuation(level = 1, "Performing structural validation for ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable {
              val issues = Osis_BasicValidator_PerBook().processRootNode(rootNode)
              issuesThunk.merge(issues)
            }
          } // forEach
        } // run
      } // Parallel
    } // reportWithContinuation

    if (issuesThunk.m_ChaptersWithBadIds.isNotEmpty())
      Logger.error("Chapters with bad ids: " + issuesThunk.m_ChaptersWithBadIds.joinToString(", "))

    if (issuesThunk.m_VersesWithBadIds.isNotEmpty())
      Logger.error("Chapters with bad ids: " + issuesThunk.m_VersesWithBadIds.joinToString(", "))

    if (issuesThunk.m_ChaptersWithBadBookAncestor.isNotEmpty())
      Logger.error("Chapters which are not under a book, or are under the wrong book: " + issuesThunk.m_ChaptersWithBadBookAncestor.joinToString(", "){ Ref.rd(it).toString() })

    if (issuesThunk.m_VersesWithBadChapterAncestor.isNotEmpty())
      Logger.error("Verses which are under the wrong chapter: " + issuesThunk.m_VersesWithBadChapterAncestor.toSet().joinToString(", "){ Ref.rd(it).toString() })

    if (issuesThunk.m_VersesWithNoChapterAncestor.isNotEmpty())
      Logger.error("Verses which are not under a chapter: " + issuesThunk.m_VersesWithNoChapterAncestor.joinToString(", "){ Ref.rd(it).toString() })

    if (issuesThunk.m_VersesWhereSidAndEidDoNotAlternate.isNotEmpty())
      Logger.error("Locations where verse sids and eids do not alternate: " + issuesThunk.m_VersesWhereSidAndEidDoNotAlternate.joinToString(", "){ Ref.rd(it).toString() })

    if (issuesThunk.m_VersesWhereSidAndEidDoNotMatch.isNotEmpty())
      Logger.error("Locations where verse sids and eids do not match: " + issuesThunk.m_VersesWhereSidAndEidDoNotMatch.joinToString(", "){ Ref.rd(it).toString() })

    if (issuesThunk.m_ElidedSubverses.isNotEmpty())
      Logger.warning("Elided subverses: " + issuesThunk.m_ElidedSubverses.joinToString(", "){ RefRange.rdUsx(it).toString() })
  }
}




/*******************************************************************************/
private class Osis_BasicValidator_PerBook
{
  /****************************************************************************/
  fun processRootNode (bookNode: Node): IssuesThunk
  {
    Rpt.reportBookAsContinuation(Osis_FileProtocol.getBookAbbreviation(bookNode))
    structuralValidationForBook1(bookNode)
    return m_IssuesThunk
  }


  /****************************************************************************/
  private fun structuralValidationForBook1 (bookNode: Node)
  {
    val bookNo = Osis_FileProtocol.readRef(Osis_FileProtocol.getBookAbbreviation(bookNode)).getB()
    Dom.findNodesByName(bookNode, Osis_FileProtocol.tagName_chapter(), false).forEach { chapterNode ->
      if (!Dom.hasAsAncestor(chapterNode, bookNode))
        m_IssuesThunk.m_ChaptersWithBadBookAncestor.add(Osis_FileProtocol.readRef(chapterNode[Osis_FileProtocol.attrName_chapterSid()]!!).toRefKey())
      else
      {
        val chapterRefKey = getRefKey(chapterNode[Osis_FileProtocol.attrName_chapterSid()]!!)
        if (0L == chapterRefKey)
          m_IssuesThunk.m_ChaptersWithBadIds.add(chapterNode[Osis_FileProtocol.attrName_chapterSid()]!!)
        else
        {
          if (Ref.getB(chapterRefKey) != bookNo)
            m_IssuesThunk.m_ChaptersWithBadBookAncestor.add(chapterRefKey)
          structuralValidationForChapter(chapterNode)
        }
      }
    }
  }


  /****************************************************************************/
  private fun structuralValidationForChapter (chapterNode: Node)
  {
    val chapterRefKey = Osis_FileProtocol.readRef(chapterNode[Osis_FileProtocol.attrName_chapterSid()]!!).toRefKey()
    val verseNodes = Dom.findNodesByName(chapterNode, Osis_FileProtocol.tagName_verse(), false)
    verseNodes.forEach { verseNode ->
      if (!Dom.hasAsAncestor(verseNode, chapterNode))
        m_IssuesThunk.m_VersesWithNoChapterAncestor.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseSid()]!!).toRefKey())
      else
      {
        val id = if (Osis_FileProtocol.attrName_verseSid() in verseNode) Osis_FileProtocol.attrName_verseSid() else Osis_FileProtocol.attrName_verseEid()
        val verseRefKey = getRefKey(verseNode[id]!!)
        if (0L == verseRefKey)
          m_IssuesThunk.m_VersesWithBadIds.add(chapterNode[Osis_FileProtocol.attrName_verseSid()]!!)
        else
        {
          checkForElidedSubverses(verseNode)

          if (Ref.rd(verseRefKey).toRefKey_bc() != chapterRefKey)
            m_IssuesThunk.m_VersesWithBadChapterAncestor.add(verseRefKey)
        }
      }
    }

    checkVerseSidsAndEidsAlternate(verseNodes)
  }


  /****************************************************************************/
  private fun checkForElidedSubverses (node: Node)
  {
    val sid = node[Osis_FileProtocol.attrName_verseSid()] ?: return
    if ('-' !in sid) return
    val rc = Osis_FileProtocol.readRefCollection(sid)
    if (rc.getLowAsRef().hasS() || rc.getHighAsRef().hasS())
      m_IssuesThunk.m_ElidedSubverses.add(rc.toString())
  }


  /****************************************************************************/
  private fun checkVerseSidsAndEidsAlternate (verseNodes: List<Node>)
  {
    var expectedId: String? = null
    verseNodes.forEach { verseNode ->
      if (Osis_FileProtocol.attrName_verseSid() in verseNode)
      {
        if (null != expectedId)
        {
          m_IssuesThunk.m_VersesWhereSidAndEidDoNotAlternate.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseSid()]!!).toRefKey())
          return@forEach // Equivalent of continue.
        }

        expectedId = Osis_FileProtocol.readRefCollection(verseNode[Osis_FileProtocol.attrName_verseSid()]!!).toString()
      }

      else // eid
      {
        if (null == expectedId)
        {
          m_IssuesThunk.m_VersesWhereSidAndEidDoNotAlternate.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseEid()]!!).toRefKey())
          return@forEach // Equivalent of continue.
        }

        if (expectedId != Osis_FileProtocol.readRefCollection(verseNode[Osis_FileProtocol.attrName_verseEid()]!!).toString())
          m_IssuesThunk.m_VersesWhereSidAndEidDoNotMatch.add(Osis_FileProtocol.readRef(verseNode[Osis_FileProtocol.attrName_verseEid()]!!).toRefKey())

        expectedId = null
      } // eid
    }
  }


  /****************************************************************************/
  /* Attempts to read an id (sid / eid) and returns either the resulting refkey
     (or the first refkey if this is a range), or 0 as an error indicator. */

  private fun getRefKey (id: String): RefKey
  {
    return try
    {
      Osis_FileProtocol.readRefCollection(id).getFirstAsRefKey()
    }
    catch (_: Exception)
    {
      0
    }
  }


  private val m_IssuesThunk = IssuesThunk()
}


/******************************************************************************/
private class IssuesThunk
{
  val m_ChaptersWithBadBookAncestor: MutableList<RefKey> = mutableListOf()
  val m_ChaptersWithBadIds: MutableList<String> = mutableListOf()
  val m_ElidedSubverses: MutableList<String> = mutableListOf()
  val m_VersesWhereSidAndEidDoNotAlternate: MutableList<RefKey> = mutableListOf()
  val m_VersesWhereSidAndEidDoNotMatch: MutableList<RefKey> = mutableListOf()
  val m_VersesWithBadChapterAncestor: MutableList<RefKey> = mutableListOf()
  val m_VersesWithBadIds: MutableList<String> = mutableListOf()
  val m_VersesWithNoChapterAncestor: MutableList<RefKey> = mutableListOf()

  @Synchronized fun merge (otherIssues: IssuesThunk)
  {
    m_ChaptersWithBadBookAncestor.addAll(otherIssues.m_ChaptersWithBadBookAncestor)
    m_ChaptersWithBadIds.addAll(otherIssues.m_ChaptersWithBadIds)
    m_ElidedSubverses.addAll(otherIssues.m_ElidedSubverses)
    m_VersesWhereSidAndEidDoNotAlternate.addAll(otherIssues.m_VersesWhereSidAndEidDoNotAlternate)
    m_VersesWhereSidAndEidDoNotMatch.addAll(otherIssues.m_VersesWhereSidAndEidDoNotMatch)
    m_VersesWithBadChapterAncestor.addAll(otherIssues.m_VersesWithBadChapterAncestor)
    m_VersesWithBadIds.addAll(otherIssues.m_VersesWithBadIds)
    m_VersesWithNoChapterAncestor.addAll(otherIssues.m_VersesWithNoChapterAncestor)
  }
}