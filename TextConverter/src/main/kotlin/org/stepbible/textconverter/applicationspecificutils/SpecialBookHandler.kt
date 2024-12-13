package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.contains
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.findNodeByName
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.findNodesByName
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceAbandonRun
import org.stepbible.textconverter.protocolagnosticutils.PA_ElementArchiver
import org.w3c.dom.Node

/******************************************************************************/
/**
 * Handles 'special' books.  At present this is limited to Greek Esther text,
 * for which the complications are as follows:
 *
 * Greek Esther comes in two forms.  In one, we receive the full text.  In the
 * other, we receive just the chapters and verses which the Greek text has over
 * and above those in the Hebrew version.
 *
 * Unfortunately the USX spec gives only a single name for Greek Esther, so that
 * officially there is no way to use the book name to indicate which version of
 * the text has been supplied.  The name -- Esg -- seems to suggest USX
 * anticipates that only the full text will ever be supplied, but the fact
 * remains that we need to be able to cater for both, and therefore have to make
 * the assumption that Esg may contain either the full text or just the
 * additions.
 *
 * There is a similar issue with OSIS, where the reference manual gives only
 * AddEsth, apparently making the opposite assumption from USX -- ie assuming
 * that the only text which will be supplied is the additions text.
 *
 * And then, just to confuse things thoroughly, Crosswire claim that there is,
 * in fact, an additional USX abbreviation (Ade) and an additional OSIS
 * abbreviation (EsthGr), so that in fact both protocols *do* 'officially'
 * support both the full text and the additions text under appropriate names.
 *
 * I am somewhat dubious of this assertion, since I can find no reference to
 * either of these names other than on a Crosswire web page; but whether they
 * are correct or not, anyone working with either the USX or the OSIS reference
 * manual is going to have only Esg or AddEsth available to them, and is
 * therefore going to have to press those into use for both versions.
 *
 * I therefore adopt a lenient attitude: if processing USX, I accept either Esg
 * or Ade, and determine whether we have been given the full text or the
 * additions not by reference to the name, but by examining the content.
 * Ditto if I am processing OSIS, mutatis mutandis.
 *
 * In determining which version we actually have, I look for chapter 1 verse 2.
 * If present, I assume we have the full text.  If absent, I assume we have
 * the additions.  This may require a little explanation.  Typically in the
 * additions text, chapters 1-9 are missing entirely, along with vv 1-3 of
 * chapter 10.  In this case, 1:2 will be absent because chapter 1 is absent.
 * Just occasionally, though, I have come across texts in which chapters 1-9
 * are present, with each containing a single verse to act as a placeholder.
 * By looking for verse *2* rather than verse 1, I can recognise this, too, as
 * being the additions text.
 *
 * The task of this present object is to recognise what we have, and to force
 * an appropriate name for it, as follows:
 *
 * - If we have the additions text I force its name to be Ade (AddEsth in OSIS).
 *
 * - If we have the full text I force the name to Est if we do not have Est
 *   already.  Otherwise I force the name to be Esg.  (Or, in OSIS, Esth and
 *   EsthGr.)
 *
 *
 * This also has a potential knock-on effect, because renaming may invalidate
 * both cross-references which point into the book being renamed and also
 * cross-references within it -- the former because chances are they will name
 * the wrong target, and the latter because typically cross-references contain
 * a reference to the verse which contains them.  And actually this last
 * fact is also an issue for general footnotes.
 *
 * If I am renaming stuff, therefore, I need to make sure these issues are
 * made good.
 *
 * <span class='important'>Almost all of this class has been written so as
 * to cater either for USX or OSIS.  There is just one place -- marked
 * 'OSIS only' below -- where I expect to be dealing purely with OSIS.  I
 * guess it wouldn't require huge effort to sort this out, but it will be
 * more effort than I'm prepared to expend at present.</span>
 *
 * Bear in mind when positioning the call to the present object that it
 * assumes that cross-reference nodes have already been archived because
 * it updates cross-references if necessary, and looks in the archive to
 * find them.
 *
 * Beware of creating more cross-references after moving the original
 * ones to the archive, because this class won't update them.  However,
 * we don't create cross-references presently and I can't immediately
 * think of any reason why we should do so during the conversion
 * processing.
 *
 * @author ARA "Jamie" Jamieson
 */

object SpecialBookHandler
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
  * Handles renaming etc of special books.
  *
  * @param dataCollection
  */

  fun process (dataCollection: X_DataCollection, notesArchiver: PA_ElementArchiver)
  {
    m_DataCollection = dataCollection
    m_FileProtocol = dataCollection.getFileProtocol()
    m_NotesArchiver = notesArchiver
    processGreekEsther()
  }




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun processGreekEsther ()
  {
    /**************************************************************************/
    /* See if we have Greek Esther under either of the two names available to
       it -- Esg / Ade if we're processing USX, EsthGr / AddEsth if we're
       processing OSIS.  It's ok to use BibleBookNamesUsx here, because I
       simply need the book number. */

    val greekEstherBookNode = m_DataCollection.getRootNode(BibleBookNamesUsx.nameToNumber("Esg")) ?:
                              m_DataCollection.getRootNode(BibleBookNamesUsx.nameToNumber("Ade")) ?:
                              return

    val originalBookNumber = m_FileProtocol.getBookNumber(greekEstherBookNode)


    /**************************************************************************/
    /* Work out which version we have -- the additions if the first chapter is
       10, or if the first chapter does not have at least two verses; otherwise
       the full version. */

    val firstChapter = greekEstherBookNode.findNodeByName("chapter", false)!!
    var version = 'F'
    if (10 == Ref.getC(m_FileProtocol.getSidAsRefKey(firstChapter)))
      version = '+'
    else
    {
      val verses = firstChapter.findNodesByName(m_FileProtocol.tagName_verse())
      if (verses.size < 4) // I want to look for two verses, but have to cater for each being a sid / eid pair.  Probably wouldn't hurt if I set this rather higher, actually.
        version = '+'
    }



    /**************************************************************************/
    /* Work out what the name _should_ be. */

    val newBookNumber = when (version)
    {

      '+' -> BibleBookNamesUsx.abbreviatedNameToNumber("Ade")

      else -> {
        val hebrewEstherBookNode = m_DataCollection.getRootNode(BibleBookNamesUsx.nameToNumber("Est"))
        BibleBookNamesUsx.abbreviatedNameToNumber(if (null == hebrewEstherBookNode) "Est" else "Esg")
      }
    }



    /**************************************************************************/
    if (originalBookNumber == newBookNumber)
      return



    /**************************************************************************/
    /* If we want the book to turn into Est (Esth), move it to before Job
       (which had better exist, or else I can't really move it). */

    if (newBookNumber == BibleBookNamesUsx.abbreviatedNameToNumber("Est"))
    {
      val jobRootNode = m_DataCollection.getRootNode(BibleBookNamesUsx.nameToNumber("Job")) ?: throw StepExceptionWithoutStackTraceAbandonRun("Needed to change Greek Esther into Est, but Job does not exist, so I have nowhere to insert it.)")
      Dom.deleteNode(greekEstherBookNode)
      Dom.insertNodeBefore(jobRootNode, greekEstherBookNode)
    }



    /**************************************************************************/
    renameBook(greekEstherBookNode, newBookNumber) // Deals with all sids and eids.
    val tempDoc = m_DataCollection.convertToDoc()
    m_DataCollection.loadFromDocs(listOf(tempDoc)) // Possibly over the top, reloading absolutely everything, but it's probably safer.
    BookOrdering.initialiseFromOsis(tempDoc) // OSIS only -- this is the one bit which would need to change if you want to cater for USX.
    m_NotesArchiver.modifyReferences(m_DataCollection, mapOf(Original(Ref.rd(originalBookNumber, 0, 0, 0).toRefKey()) to Revised(Ref.rd(newBookNumber, 0, 0, 0).toRefKey())), "b") // Change details in cross-references.
  }


  /****************************************************************************/
  /* Note that this assumes book nodes are containing nodes by this time in the
     overall processing. */

  private fun renameBook (node: Node, newBookNumber: Int)
  {
    val newName = if (m_FileProtocol.Type == X_FileProtocol.ProtocolType.USX) BibleBookNamesUsx.numberToAbbreviatedName(newBookNumber) else BibleBookNamesOsis.numberToAbbreviatedName(newBookNumber)
    m_FileProtocol.setBookAbbreviation(node, newName)
    node.findNodesByName(m_FileProtocol.tagName_chapter()).forEach { renameChapter(it, newBookNumber) }
  }


  /****************************************************************************/
  /* Note that this assumes chapter nodes are containing nodes by this time in
     the overall processing. */

  private fun renameChapter (node: Node, newBookNumber: Int)
  {
    val revisedRefKey = Ref.setB(m_FileProtocol.getSidAsRefKey(node), newBookNumber)
    m_FileProtocol.setSid(node, revisedRefKey)
    node.findNodesByName(m_FileProtocol.tagName_verse()).forEach { renameVerse(it, newBookNumber)}
  }


  /****************************************************************************/
  /* Note that this assumes chapter nodes are containing nodes by this time in
     the overall processing. */

  private fun renameVerse (node: Node, newBookNumber: Int)
  {
    if (m_FileProtocol.attrName_verseSid() in node)
    {
      val revisedRefKey = Ref.setB(m_FileProtocol.getSidAsRefKey(node), newBookNumber)
      m_FileProtocol.setSid(node, revisedRefKey)
    }

    else
    {
      val revisedRefKey = Ref.setB(m_FileProtocol.getEidAsRefKey(node), newBookNumber)
      m_FileProtocol.setEid(node, revisedRefKey)
    }
  }


  /****************************************************************************/
  private lateinit var m_DataCollection: X_DataCollection
  private lateinit var m_FileProtocol: X_FileProtocol
  private lateinit var m_NotesArchiver: PA_ElementArchiver
}