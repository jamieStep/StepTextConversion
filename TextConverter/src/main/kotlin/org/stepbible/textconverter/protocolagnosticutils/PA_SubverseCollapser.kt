package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.TranslatableFixedText
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.shared.Language
import org.w3c.dom.Document
import org.w3c.dom.Node

/******************************************************************************/
/**
 * Collapses subverses into the owning verse.
 *
 * JSword cannot (I believe) cope with subverses.  This object collapses
 * subverses into the owning verse.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_SubverseCollapser: PA()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * We may have subverses at present - either because they were present in the
   * raw USX or because reversification has created them.  At the time of
   * writing, we have made a decision that they should be collapsed into the
   * owning verse, which is handled by this present method
   * 
   * @param dataCollection Data to be processed.
   */

  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection)
    Rpt.report(level = 1, "Handling subverses ...")

    with(ParallelRunning(true)) {
      run {
        dataCollection.getRootNodes().forEach { rootNode ->
          asyncable {
            Rpt.reportBookAsContinuation(m_FileProtocol.getBookAbbreviation(rootNode))
            PA_SubverseCollapserPerBook(m_FileProtocol).processRootNode(rootNode)
          } // asyncable
        } // forEach
      } // run
    } // parallel
  } // fun
}



/******************************************************************************/
/* It is important to avoid making assumptions about what might be 'reasonable'
   when it comes to subverses -- I have seen a lot of arrangements, and a number
   of them seem entirely _un_reasonable.  If you think something can't arise,
   it's probably only because we haven't hit it _yet_.  Give it time.

   First of all, an important note: do recall that subverses are essentially
   verses with a particular form of reference.  They aren't nested inside their
   owning verse, but instead, follow it.

   In general, arrangements fall into two camps -- we may _have_ the owning
   verse -- 12, 12a, 12b, ... -- or we may not -- 12a, 12b, ...

   This much is fairly easy because we can reduce both to the same arrangement
   by inserting an empty owning verse in the second case, which then makes it
   identical to the first case.  (There is no need to be concerned about it
   being an empty verse, because by the time we've collapsed the subverses into
   it, presumably it will no longer be empty.)

   There are some assumptions which is it natural to make, and which are to be
   avoided, though.  You should not assume that there will be more than one
   subverse (you may even encounter something like 12a on its own -- a single
   subverse with no owning verse).  You should not assume that the subverses
   start from 'a'.  You should not assume they run contiguously (although
   presumably they will).  You should not assume even that they run in order
   (although again presumably they will).  And if you have an owning verse
   you should not assume there will be any text in the owning verse -- it
   may be empty and be immediately followed by the first subverse.

   FWIW, the worst case scenario I've encountered so far occurred in gerHFA,
   where the translators had put v10a at the location where you would normally
   expect v10 to occur (with no v10).  This much is not a real problem -- it is,
   indeed, one of the cases above.  But they'd put v10b somewhere else
   entirely.  There is absolutely no way of handling this: I have to remove
   the subverse tags because osis2mod / JSword can't cope with subverses.  But
   if I _do_ remove them in the way described above, we end up with two pieces
   of text both marked as being verse 10.  At the time of writing, the only way
   I can see of dealing with this is to make manual changes to the source in
   order to change the way things are marked up -- except that this is
   precluded by licence conditions.


   We have the option of adding markers to indicate where the boundaries of the
   subverses fell.  We have blown rather hot and cold on this -- do we or don't
   we.  At present we do.

   I am anticipating that we may want to be able to handle markers in one of
   two forms.  We may want superscripted letters -- a, b, ... aa, ab, ... --
   or we may just want a fixed (configurable) character, the latter for use
   in non-alphabetic scripts such as Chinese.

   There is a slight issue here where the first subverse is not preceded by
   any text.  If we are using superscripted letters, this situation is
   probably ok -- a superscripted 'a' immediately following the verse number
   doesn't look desperately odd, and is actually meaningful.  If we are using
   fixed symbols, this may not be so great because we end up with, say, a
   vertical bar immediately following the verse number, and this I think
   that _would_ look odd.  Not that I'm doing anything about it.
 */

private class PA_SubverseCollapserPerBook (val m_FileProtocol: X_FileProtocol)
{
  /****************************************************************************/
  fun processRootNode (rootNode: Node)
  {
    /**************************************************************************/
//    val dbg = Dbg.dCont(Dom.toString(rootNode), "Zec")
//    if (dbg) Dbg.dDomTree(rootNode)



    /**************************************************************************/
    /* allSids is grouped by _verse_ number, so if we have a v10 and a v10a,
       they will end up in the same group.  This initial grouping will also
       give us verses which have no associated subverses, and these we can
       ignore in further processing.  The filter looks at the _last_ entry in
       each group.  If this isn't for a subverse, then we know the group as a
       whole is of no interest. */

    val sidAttr = m_FileProtocol.attrName_verseSid()
    val allVerses = rootNode.findNodesByName("verse", false) // All the verse tags (which includes subverses, because they, too, use verse tags).
    val (allSids, allEids) = allVerses.partition { m_FileProtocol.attrName_verseSid() in it } // Split into two, sids and eids, both following the same ordering.
    val sidToEidMapping = allSids.zip(allEids).toMap().toMutableMap() // Creates a mapping table from sid to corresponding eid.
    val sidGroups = // See note at start of para.
      allSids.groupBy { m_FileProtocol.readRef(it, sidAttr).toRefKey_bcv() }
             .filter { Ref.hasS(m_FileProtocol.readRef(it.value.last(), sidAttr).toRefKey()) }



    /**************************************************************************/
    /* If the first element in the list of sids has no subverse, then it must
       be the owning verse -- so we must _have_ an owning verse, and there is
       therefore no need to create one.

       Otherwise, we create the owning verse and update the data structures to
       reflect its existence.  Doing that means that the caller can always
       rely upon an owning verse existing and being in the correct place. */

    fun createOwningVerseIfNecessary (group: List<Node>): List<Node>
    {
      var owningVerseRefKey = m_FileProtocol.readRef(group.first(), sidAttr).toRefKey()
      if (!Ref.hasS(owningVerseRefKey)) return group

      owningVerseRefKey = Ref.clearS(owningVerseRefKey)

      val owningVerseSid = m_FileProtocol.makeVerseSidNode(rootNode.ownerDocument, Pair(owningVerseRefKey, null))
      val owningVerseEid = m_FileProtocol.makeVerseEidNode(rootNode.ownerDocument, Pair(owningVerseRefKey, null))

      Dom.insertNodeBefore(group.first(), owningVerseSid)
      Dom.insertNodeBefore(group.first(), owningVerseEid)

      sidToEidMapping[owningVerseSid] = owningVerseEid
      val res = mutableListOf(owningVerseSid)
      res.addAll(group)
      return res
    }


    /**************************************************************************/
    fun processGroup (group: List<Node>)
    {
      val owningSid = group.first()[sidAttr]!! // Copy reference from owner sid to last eid.
      sidToEidMapping[group.last()]!![m_FileProtocol.attrName_verseEid()] = owningSid

      group.subList(0, group.size - 1).map { sidToEidMapping[it]!! }.forEach(Dom::deleteNode) // Ditch all eids bar the last.

      group.subList(1, group.size).forEach { sidNode -> // Insert markers before all sids but the first, and then delete the sids.
        val n = Ref.getS(m_FileProtocol.getSidAsRefKey(sidNode))
        val separator = makeSeparator(rootNode.ownerDocument, n)
        Dom.insertNodeBefore(sidNode, separator)
        Dom.deleteNode(sidNode)
      }
    }



    /**************************************************************************/
     sidGroups.forEach {
      val group = createOwningVerseIfNecessary(it.value)
      processGroup(group)
    }

//    if (dbg) Dbg.dDomTree(rootNode, "b.xml")
  }


  companion object
  {
    /**************************************************************************/
    /* The boundary marker which we make is defined by Special:V_subverse_boundaryMarker
       as follows:

       - If it is null, then the boundary marker is a space.

       - If it does not contain a dash, then that is taken as the marker.

       - If it does contain a dash (ie is of the form x-y), then the elements
         before and after the dash are assumed to be the vernacular equivalents
         of 'a' and 'z', and the generated markers are superscripted and are
         vernacular equivalents of the form 'a', 'b', ... 'aa', 'ab' ...
     */

    /**************************************************************************/
    private fun makeSeparator (doc: Document, n: Int): Node
    {
      return if ('\u0001' == m_LowChar) makeFixedSeparator(doc, n) else makeVariableSeparator(doc, n)
    }


    /**************************************************************************/
    private fun makeFixedSeparator (doc: Document, n: Int): Node
    {
      val res = Dom.createNode(doc, "<_X_subverseSeparatorFixed/>")
      res.appendChild(Dom.createTextNode(doc, m_MarkerTemplate))
      return res
    }


    /**************************************************************************/
    private fun makeVariableSeparator (doc: Document, n: Int): Node
    {
      val marker = MiscellaneousUtils.convertNumberToRepeatingString(n, m_LowChar, m_HighChar)
      val res = Dom.createNode(doc, "<_X_subverseSeparatorVariable/>")
      res.appendChild(Dom.createTextNode(doc, marker))
      return res
    }


    /**************************************************************************/
    private val m_LowChar: Char
    private val m_HighChar: Char
    private val m_MarkerTemplate: String

    init
    {
      m_MarkerTemplate = TranslatableFixedText.lookupText(Language.Vernacular, "V_subverse_boundaryMarker")
      val (x, y) = if ("-" in m_MarkerTemplate) m_MarkerTemplate.split("-") else listOf("\u0001", "\u0001")
      m_LowChar = x[0]; m_HighChar = y[0]
    }
  } // companion
}
