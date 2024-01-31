package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.*
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.shared.Language
import org.stepbible.textconverter.support.stepexception.StepExceptionShouldHaveBeenOverridden
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node

/****************************************************************************/
/**
 * Table processing.  Tables are problematical because they almost always
 * run across verse boundaries, something which osis2mod isn't too keen on
 * (with good reason).
 *
 * Originally I was offering three approaches to handling tables:
 *
 * 1. Tables which didn't cross verse-boundaries could be left as-is
 *
 * 2. Tables could be converted to flat form (plain text).  This would respect
 *    verse boundaries, but you'd lose the table layout.
 *
 * 3. The table layout could be retained effectively by taking the last verse
 *    associated with the table and moving the entire table within that
 *    verse, dropping the earlier ones and treating the last one as one
 *    massive elision.
 *
 *
 * Of these, I've dropped option 2, because if the translators have asked for
 * tables, it is presumably because that's what they wanted.  My expectation,
 * therefore, is that we will always want option 3.  Option 1 remains a
 * possibility, and if we have that I retain things as-is, but the processing
 * is there mainly because a table which fitted option 1 might be an
 * embarrassment if I attempted to apply option 3 processing.
 *
 * Of course, there is a problem with option 3, in that it does mean that
 * although all of the verses making up the table continue to exist, their
 * content is all wrong -- most are empty, and one is huge.  But that's tough.
 *
 * @author ARA "Jamie" Jamieson
 */

open class SE_Tables protected constructor (fileProtocol: Z_FileProtocol, emptyVerseHandler: Z_EmptyVerseHandler): SE
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
  * Processes all tables.
  *
  * @param rootNode Root node of document, or of that portion we are presently
  *   processing.
  */

  override fun process (rootNode: Node)
  {
    Dbg.reportProgress("Handling tables.")
    Dom.findNodesByName(rootNode, tagName_table(), false).forEach {
      if (Dom.findNodesByName(it, "verse", false).any())
        restructureTablesConvertToElidedForm(it)
      else
        reformatTableWhichDidNotRequireElision(it)
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Protected                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  protected open fun attributeName_tableHeaderCellStyle (): String { throw StepExceptionShouldHaveBeenOverridden() }
  protected open fun insertBlankLine (tableNode: Node) { throw StepExceptionShouldHaveBeenOverridden() }
  protected open fun tagName_cell (): String { throw StepExceptionShouldHaveBeenOverridden()}
  protected open fun tagName_row (): String { throw StepExceptionShouldHaveBeenOverridden()}
  protected open fun tagName_table (): String { throw StepExceptionShouldHaveBeenOverridden() }
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
      val cells = Dom.findNodesByName(rowNode, tagName_cell(), false)
      cells.subList(0, cells.size - 1). forEach { appendSpaces (it) }
    }


    /**************************************************************************/
    val rows  = Dom.findNodesByName(tableNode, tagName_row(), false)
    val cells  = Dom.findNodesByName(rows[0], tagName_cell(), false) // Header.
    cells.forEach { restructureTablesEmboldenTableHeaderCell(it) }
    rows.forEach { processRowContent(it) }
    Dom.setNodeName(tableNode, tagName_table())
    insertBlankLine(tableNode)
  }


  /****************************************************************************/
  /* The aim here time is to encapsulate the entire table within an elision,
     and then to insert some kind of marker wherever the verse boundaries
     originally came, just to show where they are.  If the table 'more or less'
     starts with a sid, then I move that sid out of the table and use that as
     the container for the elided verses.  Otherwise I locate the last verse
     prior to the table and use that.  Trouble is that there are still probably
     plenty of special cases for which I don't cater -- for example if the table
     contains headers before the first verse.  In this case, we'd want the
     headers to come outside the table, but we'd have no preceding verse to own
     the table. */

  private fun restructureTablesConvertToElidedForm (table: Node)
  {
    /**************************************************************************/
    val verseTags = Dom.findNodesByName(table, m_FileProtocol.tagName_verse(), false) .toMutableList()
    var verseStarts = verseTags.filter { m_FileProtocol.attrName_verseSid() in it } .toMutableList()
    val verseEnds   = verseTags.filter { m_FileProtocol.attrName_verseEid() in it } .toMutableList()
    val owningVerseSid: Node?



    /**************************************************************************/
    /* For error messages. */

    val location = if (verseStarts.isNotEmpty()) verseStarts[0][m_FileProtocol.attrName_verseSid()]!! else verseEnds[0][m_FileProtocol.attrName_verseEid()]!!
    val locationAsRefKey = m_FileProtocol.readRef(location).toRefKey()



    /**************************************************************************/
    /* Until we know otherwise, we'll close the elision after the table. */

    var closeElisionAfterNode = table



    /**************************************************************************/
    /* If the last verse tag within the table is a sid, it's fairly easy to
       cope if the eid more or less follows the end of the table -- we just
       move the eid back inside the table. */

    if (m_FileProtocol.attrName_verseSid() in verseTags.last())
    {
     val eid = Dom.findNodeByAttributeValue(m_RootNode, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid(), Dom.getAttribute(verseTags.last(), "sid")!!)!!
     if (Dom.isNextSiblingOf(table, eid, true))
      {
        Dom.deleteNode(eid)
        table.appendChild(eid)
        verseTags.add(eid)
        verseEnds.add(eid)
      }
      else // The eid isn't conveniently placed just after the table.
        closeElisionAfterNode = eid
    }



    /**************************************************************************/
    /* If the very first non-blank node in the table is a sid, move the sid
       immediately before the table so that the verse can contain the entire
       table. */

    if (Dom.isFirstNonBlankChildOf(table, verseStarts[0]))
    {
      owningVerseSid = verseStarts[0]
      Dom.deleteNode(owningVerseSid)
      Dom.insertNodeBefore(table, owningVerseSid)
      verseStarts = verseStarts.subList(1, verseStarts.size)
    }



    /**************************************************************************/
    /* If we haven't identified the owning verse, the owning verse must be the
       one which comes before the first verse start in the table, and we can
       find this based upon the sid. */

    else
    {
      val ref = m_FileProtocol.readRef(verseStarts[0], m_FileProtocol.attrName_verseSid())
      ref.setV(ref.getV() - 1)
      owningVerseSid = Dom.findNodeByAttributeValue(m_RootNode, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseSid(), ref.toString())
      if (null == owningVerseSid)
      {
        Logger.error(locationAsRefKey, "Table: Failed to find owning verse at or about $location")
        return
      }
    }



    /**************************************************************************/
    /* By this point, owningVerse points to the verse within which the table
       starts.  verseTags contains all of the verse tags _within_ the table,
       verseStarts all of the sids, and verseEnds all of the eids.  The last
       entry in verseEnds is an eid at the end of the table -- ie with no
       following canonical text. */



    /**************************************************************************/
    /* We're going to create an eid for the owning verse and position it at the
       other end of the table, so we no longer want its existing eid. */

    val existingEid = Dom.findNodeByAttributeValue(m_RootNode, m_FileProtocol.tagName_verse(), m_FileProtocol.attrName_verseEid(), owningVerseSid[m_FileProtocol.attrName_verseSid()]!!)
    Dom.deleteNode(existingEid!!)



    /**************************************************************************/
    /* Change the sid of the owning verse to reflect the elision, and add an
       explanatory footnote. */

    val startOfElisionRef = m_FileProtocol.readRef(owningVerseSid, m_FileProtocol.attrName_verseSid())
    val endOfElisionRef   = m_FileProtocol.readRef(verseStarts.last(), m_FileProtocol.attrName_verseSid())
    val elisionRef = "$startOfElisionRef-$endOfElisionRef"

    owningVerseSid[m_FileProtocol.attrName_verseSid()] = startOfElisionRef.toString()
    Utils.addTemporaryAttribute(owningVerseSid, "_temp_generatedElisionToCoverTable", "y")
    val owningVerseFootnote = m_FileProtocol.makeFootnoteNode(m_RootNode.ownerDocument, startOfElisionRef.toRefKey(), Translations.stringFormat(Language.Vernacular, "V_tableElision_owningVerse"))
    Dom.insertNodeAfter(owningVerseSid, owningVerseFootnote)



    /**************************************************************************/
    /* Insert an eid after the table to correspond to the owning verse. */

    val owningVerseEid = Dom.createNode(m_RootNode.ownerDocument, "<${m_FileProtocol.tagName_verse()} ${m_FileProtocol.attrName_verseEid()}='$startOfElisionRef'/>")
    Dom.insertNodeAfter(closeElisionAfterNode, owningVerseEid)
    if (closeElisionAfterNode !== table) Dom.deleteNode(closeElisionAfterNode) // We're closing after an eid which is no longer required.



    /**************************************************************************/
    /* Delete all eids within the table. */

    verseEnds.forEach { Dom.deleteNode(it) }



    /**************************************************************************/
    /* Replace all sids by visible verse-boundary markers. */

    fun insertVerseBoundaryMarker (sid: Node)
    {
      val sidText = sid[m_FileProtocol.attrName_verseSid()]!!
      val markerText = Translations.stringFormat(Language.Vernacular, "V_tableElision_verseBoundary", m_FileProtocol.readRef(sidText))
      val markerNode = Dom.createNode(m_RootNode.ownerDocument, "<_X_verseBoundaryWithinElidedTable/>")
      markerNode.appendChild(Dom.createTextNode(m_RootNode.ownerDocument, markerText))
      Dom.insertNodeAfter(sid, markerNode)
      Dom.deleteNode(sid)
    }

    verseStarts.forEach { insertVerseBoundaryMarker(it) }



    /**************************************************************************/
    /* At this juncture, we have the table entirely contained within a single
       verse, and the verse is marked up appropriately as an elision.  All that
       remains is to expand out the elision.  As a reminder, we encountered the
       table tag in the _first_ verse, so we want to have that recorded as the
       master, and we want the empty verses to follow it.  We almost have code
       to do this via expandElision, but that was set up on the assumption
       that empty verses would precede the master, and it's a bit fiddly to
       change that. */

    fun generateEmptyVerse (ref: Ref)
    {
      val (start, end) = m_EmptyVerseHandler.createEmptyVerseForElision(owningVerseSid.ownerDocument, ref.toRefKey())
      Dom.insertNodeAfter(owningVerseEid, end)
      Dom.insertNodeAfter(owningVerseEid, start)
    }

    Utils.addTemporaryAttribute(owningVerseSid, "_temp_originalId", elisionRef)
    Utils.addTemporaryAttribute(owningVerseSid, "_temp_elided", "y")
    Utils.addTemporaryAttribute(owningVerseSid, "_temp_masterForElisionWithVerseCountOf", "" + verseStarts.size + 1)

    val elisionRange = m_FileProtocol.readRefCollection(elisionRef).getAllAsRefs()
    elisionRange.subList(1, elisionRange.size).reversed().forEach { // We want to generate empty verses for everything bar the first verse, because the first verse is the master and contains all the text.
      generateEmptyVerse(it)
    }

    //Dbg.outputDom((owningVerseEid.ownerDocument))
  }


  /****************************************************************************/
  /* Wraps the content of a table header cell in boldface markup.  This works
     only with USX -- OSIS has no suitable markup.  This is handled here by
     giving a non-existent value for attributeName_tableHeaderCellStyle in
     the OSIS derivative of this class. */

  private fun restructureTablesEmboldenTableHeaderCell (headerCell: Node)
  {
    if (attributeName_tableHeaderCellStyle() !in headerCell) return
    var style = headerCell[attributeName_tableHeaderCellStyle()]!!
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




/******************************************************************************/
object Osis_SE_Tables : SE_Tables (Osis_FileProtocol, Osis_EmptyVerseHandler)
{
  /****************************************************************************/
  override fun tagName_cell () = "cell"
  override fun tagName_row () = "row"
  override fun tagName_table () = "table"
  override fun attributeName_tableHeaderCellStyle () = "%garbage%"


  /****************************************************************************/
  override fun insertBlankLine (tableNode: Node)
  {
    val blankLineA = Dom.createNode(tableNode.ownerDocument, "<p />") // Need two of these to end up with one blank line when rendered.
    val blankLineB= Dom.createNode(tableNode.ownerDocument, "<p />")
    Dom.insertNodeBefore(tableNode, blankLineA)
    Dom.insertNodeBefore(tableNode, blankLineB)
  }
}




/******************************************************************************/
object Usx_SE_Tables : SE_Tables(Usx_FileProtocol, Usx_EmptyVerseHandler)
{
  /****************************************************************************/
  override fun tagName_cell () = "cell"
  override fun tagName_row () = "row"
  override fun tagName_table () = "table"
  override fun attributeName_tableHeaderCellStyle () = "style"


  /****************************************************************************/
  override fun insertBlankLine (tableNode: Node)
  {
    val blankLineA = Dom.createNode(tableNode.ownerDocument, "<para style='b'/>") // Need two of these to end up with one blank line when rendered.
    val blankLineB= Dom.createNode(tableNode.ownerDocument, "<para style='b'/>")
    Dom.insertNodeBefore(tableNode, blankLineA)
    Dom.insertNodeBefore(tableNode, blankLineB)
  }
}
