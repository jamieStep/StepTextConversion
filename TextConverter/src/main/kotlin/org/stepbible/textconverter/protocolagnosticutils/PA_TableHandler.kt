package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.TranslatableFixedText
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefRange
import org.stepbible.textconverter.nonapplicationspecificutils.shared.Language
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.w3c.dom.Node

/****************************************************************************/
/**
 * Table processing.  Tables are problematical because they almost always
 * run across verse boundaries, something which osis2mod isn't too keen on
 * (with good reason).
 *
 * <span class='important>THIS MUST BE RUN *PRIOR* TO ELISION EXPANSION AND
 * EID PLACEMENT, BUT *AFTER* DELETION OF ANY EXISTING EIDS.</span>
 *
 * Originally I was offering three approaches to handling tables:
 *
 * 1. Tables which didn't cross verse-boundaries could be left as-is.
 *
 * 2. Tables could be converted to flat form (plain text).  This would respect
 *    verse boundaries, but you'd lose the table layout.
 *
 * 3. The table layout could be retained effectively by taking the first verse
 *    associated with the table and moving the entire table within that
 *    verse, dropping the earlier ones and treating the first one as one
 *    massive elision.
 *
 *
 * Of these, I've dropped option 2, because if the translators have asked for
 * tables, it is presumably because that's what they wanted.  My expectation,
 * therefore, is that we will always want option 3.  Option 1 remains a
 * possibility, but I don't think we're likely to find many tables which fit
 * within a single verse.
 *
 * (Having said this, option 2 may actually have a fair bit going for it.
 * I've seen the results of sticking to table format, and in some cases you
 * end up with a table whose left column is very wide, in order to accommodate
 * a single very long entry, and in consequence, the right hand column in most
 * cases is so far from the text in the left column that you can't work out
 * what goes with what.)
 *
 * Of course, there is a problem with option 3, in that it does mean that
 * although all of the verses making up the table continue to exist, their
 * content is all wrong -- most are empty, and one is huge.  But that's tough.
 *
 * At the end of the processing, the table is marked as an elision.  I rely
 * upon later processing to turn it into a single verse.  The elision verse
 * carries an _inTableElision attribute.
 *
 * @author ARA "Jamie" Jamieson
 */

object PA_TableHandler: PA(), ObjectInterface
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
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Reformats tables.
  * 
  * @param dataCollection The data to be processed.
  */
  
  fun process (dataCollection: X_DataCollection)
  {
    extractCommonInformation(dataCollection)
    Rpt.reportWithContinuation(level = 1, "Handling tables ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable { PA_TableHandlerPerBook(m_FileProtocol).processRootNode(rootNode) }
          } // forEach
        } // run
      } // parallel
    } // reportWithContinuation
  }
}
  
  
  
  
/******************************************************************************/
private class PA_TableHandlerPerBook (val m_FileProtocol: X_FileProtocol)
{
  /****************************************************************************/
  /**
  * Processes all tables.
  *
  * @param rootNode Root node of document, or of that portion we are presently
  *   processing.
  */

  fun processRootNode (rootNode: Node)
  {
    Rpt.reportBookAsContinuation(m_FileProtocol.getBookAbbreviation(rootNode))
    m_RootNode = rootNode
    val tableNodes = Dom.findNodesByName(rootNode, m_FileProtocol.tagName_table(), false)
    tableNodes.forEach {
      if (Dom.findNodesByName(it, "verse", false).any())
        restructureTablesConvertToElidedForm(it)
      else
        reformatTableWhichDidNotRequireElision(it)
    }

    //Dbg.outputDom(rootNode.ownerDocument)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                          Non-elided tables                             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* If we temporarily changed tag names above, we have some 'proper' tables
     which we are retaining.  The main thing we need to do with these is to
     rename them back to 'table' again so later processing can pick them up.
     In addition, it seems like a good idea to boldface any heading cells.  And
     experience shows that Sword does not leave any spaces between columns,
     so I add some here.  I also insert a blank before the table, although
     I'm less clear as to whether this will _always_ be a good thing. */

  private fun reformatTableWhichDidNotRequireElision (tableNode: Node)
  {
    /**************************************************************************/
    fun appendSpaces (cell: Node)
    {
      val textNode = Dom.createTextNode(m_RootNode.ownerDocument, "&nbsp;&nbsp;&nbsp;")
      cell.appendChild(textNode)
    }



    /**************************************************************************/
    fun processRowContent (rowNode: Node)
    {
      val cells = Dom.findNodesByName(rowNode, m_FileProtocol.tagName_cell(), false)
      cells.subList(0, cells.size - 1). forEach { appendSpaces (it) }
    }


    /**************************************************************************/
    val rows = tableNode.findNodesByName(m_FileProtocol.tagName_row(), false)
    val cells  = rows[0].findNodesByName(m_FileProtocol.tagName_cell(), false) // Header.
    cells.forEach { restructureTablesEmboldenTableHeaderCell(it) }
    rows.forEach { processRowContent(it) }
    Dom.setNodeName(tableNode, m_FileProtocol.tagName_table())
    m_FileProtocol.insertBlankLineIntoTable(tableNode)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                            Elided tables                               **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* In most cases, we're going to have a table running across multiple verses,
     and probably doing so in a fairly unpalatable.  (This isn't _always_ the
     case, though -- I have seen a few cases where the translators have elided
     the content of a table, so that the entire table appears within a single
     verse.)

     Our aim here is to do what I have just said that translators very
     occasionally do -- to take all of the verses in the table and to place
     the entire table within one elided verse.

     There is some slight complication as to what that verse should be --
     should it be the very first verse in the table, or should it be the
     verse which starts immediately before the table.  This is handled by
     identifyAndPositionTableOwnerNode.

     The present method then replaces each sid within the table by a
     boundary marker -- a <_X_verseBoundaryWithinElidedTable> tag (which
     I process into valid OSIS elsewhere) containing a stylised piece of
     text (which presently is the verse number in parentheses).  This makes
     it possible to identify where the verse boundaries fell originally.

     There is a slight issue here, because there is no guarantee that a
     parenthesised verse number will make sense in all text; in particular,
     parentheses may carry a different semantics, or may simply be susceptible
     to being confused with other characters.  However, it is the best I can
     think of presently.

     We also remove contained eids here, so as to ensure there is no verse
     markup within the table. */

  private fun restructureTablesConvertToElidedForm (table: Node)
  {
    /**************************************************************************/
    /* Work out which verse should own the table, and position it if
       necessary.  If identifyAndPositionTableOwnerNode returns null, it
       implies that the table already does not cross a verse boundary, and
       there is therefore nothing we need to do with it. */

    val owningVerseSid = identifyAndPositionTableOwnerNode(table) ?: return
    val verseRefKeysContainedInTable = mutableListOf(m_FileProtocol.getSidAsRefKey(owningVerseSid))



    /**************************************************************************/
    /* Replace all sids by visible verse-boundary markers, and then remove all
       internal verse ends.  I'm not really sure what to do about the
       verse-boundary markup.  I suspect the actual numerals need to be in
       English so they look like verse numbers.  But the enclosing markup (eg
       parens) may need to be converted to vernacular.  Can't achieve that
       combination at present. */

    var lastSidWithinTable: String? = null
    fun replaceVerseWithBoundaryMarker (sid: Node)
    {
      verseRefKeysContainedInTable.add(m_FileProtocol.getSidAsRefKey(sid))
      val sidText = sid[m_FileProtocol.attrName_verseSid()]!!
      lastSidWithinTable = sidText
      val markerText = TranslatableFixedText.stringFormat(Language.Vernacular, "V_tableElision_verseBoundary", m_FileProtocol.readRef(sidText))
      val markerNode = Dom.createNode(m_RootNode.ownerDocument, "<_X_verseBoundaryWithinElidedTable/>")
      markerNode.appendChild(Dom.createTextNode(m_RootNode.ownerDocument, markerText))
      Dom.insertNodeAfter(sid, markerNode)
      Dom.deleteNode(sid)
    }

    table.findNodesByAttributeName(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid()).forEach { replaceVerseWithBoundaryMarker(it) }
    val eids = table.findNodesByAttributeName(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid())
    val lastEidWithinTable = if (eids.isEmpty()) null else eids.last()[m_FileProtocol.attrName_verseEid()]
    eids.forEach { Dom.deleteNode(it) }



    /**************************************************************************/
    /* There is one special case we need to deal with.  The table may have
       contained a number of sids, and we will have got rid of all of the
       contained sids and eids.  However, it's possible that the eid
       corresponding to the last sid may actually fall outside the table, in
       which case we still need to get rid of it. */

    if (null != lastSidWithinTable && lastEidWithinTable != lastSidWithinTable)
      Dom.deleteNode(Dom.findNodeByAttributeValue(Dom.getAncestorNamed(table, m_FileProtocol.tagName_chapter())!!, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid(), lastSidWithinTable!!)!!)



    /**************************************************************************/
    /* Add the verse refKeys as an attribute to the containing verse sid.
       Seems like a reasonable idea, but I can't recall whether this is just
       for debugging, or whether I actually _need_ it. */

    NodeMarker.setTableRefKeys(owningVerseSid, verseRefKeysContainedInTable.joinToString(","))



    /**************************************************************************/
    /* Add some useful addition attributes ...

       - Make up a unique id and add it to the table and the owning verse, so
         we can readily tie the two together.

       - Mark the owning verse as being a table elision.  (Note that if the
         table was already set up as an elision by the translators -- or more
         accurately, if it contained no verse boundaries, and therefore we
         adjudged that it required no work -- we never get here, and therefore
         such tables are not marked.
     */

    val uniqueId = Globals.getUniqueInternalId() // Link the table with its associated sid to help later processing (SE_EnhancedVerseEndInserter).
    NodeMarker.setUniqueId(owningVerseSid, uniqueId)
    NodeMarker.setUniqueId(table, uniqueId)
    NodeMarker.setElisionType(owningVerseSid, "tableElision")



    /**************************************************************************/
    /* I imagine all of the tables which we have had cause to process here will
       have had verse markup within the table to begin with, and therefore in
       particular will have had a last contained verse.  I check that this is
       the case anyway.*/

    if (null == lastSidWithinTable)
      return



    /**************************************************************************/
    /* Change the sid for the containing verse to reflect the elision. */

    val startOfElisionRef = m_FileProtocol.readRef(owningVerseSid, m_FileProtocol.attrName_verseSid())
    m_FileProtocol.updateVerseSid(owningVerseSid, startOfElisionRef.toRefKey(), m_FileProtocol.readRef(lastSidWithinTable!!).toRefKey()) // Change sid.



    /**************************************************************************/
    /* If we're permitted to create footnotes, add one explaining what we've
       done.  We need to refer to the range covered by the footnote, which can
       be deduced from the sid of the containing verse and the last contained
       verse -- except that we need to increment the verse number for the former
       to fit in with the wording of the footnote. */

    val range = RefRange(startOfElisionRef, m_FileProtocol.readRef(lastSidWithinTable!!))
    range.getLowAsRef().setV(range.getLowAsRef().getV() + 1)
    val owningVerseFootnote = m_FileProtocol.makeFootnoteNode(Permissions.FootnoteAction.AddFootnoteToMasterVerseInElidedTable, m_RootNode.ownerDocument, startOfElisionRef.toRefKey(), TranslatableFixedText.stringFormatWithLookup("V_tableElision_owningVerse", range))
    if (null != owningVerseFootnote) // Will be null if, in fact, we're not permitted to create footnotes.
      Dom.insertNodeAfter(owningVerseSid, owningVerseFootnote)



    /**************************************************************************/
    /* Finally, earlier processing will have deleted the eid for the
       containing verse, so we need to insert it, just after the table. */

    val eidNode = m_FileProtocol.makeVerseEidNode(table.ownerDocument, owningVerseSid[m_FileProtocol.attrName_verseSid()]!!)
    Dom.insertNodeAfter(table, eidNode)



    /**************************************************************************/
    //Dbg.d(table.ownerDocument)
  }


  /****************************************************************************/
  /* Works out which verse should own the table, and therefore acts as master
     for the elision.  If the table starts off with a verse node, then I move
     that outside of the table and it becomes the owner.  If it doesn't start
     with a verse node, then the verse which immediately precedes the table is
     the owner. */

  private fun identifyAndPositionTableOwnerNode (table: Node): Node?
  {
    /**************************************************************************/
    /* Very occasionally the translators supply us with something where no work
       is required.  Given that tables are _never_ small enough to fit within
       a single verse, this will presumably mean that they have already placed
       it within an elided verse.  However the test below is rather more simple-
       minded: I simply assume that if the table contains no verse markers, it
       must already be ok. */

    if (null == table.findNodeByName(m_FileProtocol.tagName_verse(), false))
      return null



    /**************************************************************************/
    val owningVerseSid: Node?
    var ix = -1
    val allNodesInTable = table.getAllNodesBelow()



    /**************************************************************************/
    /* Sets ix to the first verse in the table if that's at the start; otherwise
       leaves it unchanged. */

    for (i in allNodesInTable.indices)
    {
      val node = allNodesInTable[i]
      if (Dom.isWhitespace(node)) continue
      if (Dom.isTextNode(node) && m_FileProtocol.isCanonicalNode(node)) break
      if (m_FileProtocol.tagName_verse() == Dom.getNodeName(node) && m_FileProtocol.attrName_verseSid() in node)
      {
        ix = i
        break
      }
    }



    /**************************************************************************/
    /* If the first verse _was_ at the front, reposition it to just before the
       table. */

    if (ix >= 0)
    {
      owningVerseSid = allNodesInTable[ix]
      Dom.deleteNode(owningVerseSid)
      Dom.insertNodeBefore(table, owningVerseSid)
      NodeMarker.setOriginalId(owningVerseSid, owningVerseSid[m_FileProtocol.attrName_verseSid()]!!)
      NodeMarker.setTableOwnerType(owningVerseSid, "wasFirstVerseInsideTable")
      return owningVerseSid
    }



    /**************************************************************************/
    /* Otherwise, the first verse _wasn't_ at the start of the table, so we
       make the verse prior to the table into the owner. */

    val ref = m_FileProtocol.readRef(table.findNodeByAttributeName(m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid())!!, m_FileProtocol.attrName_verseSid())
    ref.setV(ref.getV() - 1)
    val res = Dom.findNodeByAttributeValue(m_RootNode, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid(), m_FileProtocol.refToString(ref.toRefKey()))
    if (null == res)
    {
      Logger.error(ref.toRefKey(), "Table: Failed to find owning verse at or about $ref")
      throw StepExceptionWithStackTraceAbandonRun("Table: Failed to find owning verse at or about $ref")
    }

    NodeMarker.setTableOwnerType(res, "wasLastVerseBeforeTable")
    return res
  }



  /****************************************************************************/
  /* Wraps the content of a table header cell in boldface markup.  This works
     only with USX -- OSIS has no suitable markup.  This is handled here by
     giving a non-existent value for attributeName_tableHeaderCellStyle in
     the OSIS derivative of this class. */

  private fun restructureTablesEmboldenTableHeaderCell (headerCell: Node)
  {
    if (m_FileProtocol.attrName_tableHeaderCellStyle() !in headerCell) return
    var style = headerCell[m_FileProtocol.attrName_tableHeaderCellStyle()]!!
    if (!style.startsWith("th")) return
    style = "tc" + style.substring(2)
    Dom.setAttribute(headerCell, "style", style)
    val wrapper = Dom.createNode(m_RootNode.ownerDocument, "<char style='bd'/>")
    Dom.getChildren(headerCell).forEach { wrapper.appendChild(it) }
    headerCell.appendChild(wrapper)
  }


  /****************************************************************************/
  private lateinit var m_RootNode: Node
}
