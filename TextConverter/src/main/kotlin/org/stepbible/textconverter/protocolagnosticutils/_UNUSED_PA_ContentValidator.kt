package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefKey
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.protocolagnosticutils.reversification.PA_ReversificationDataLoader
import org.stepbible.textconverter.protocolagnosticutils.reversification.ReversificationDataRow
import org.w3c.dom.Node
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/******************************************************************************/
/**
* Compares the canonical content of two texts.
*
* I *don't* check anything other than canonical content -- I don't check
* headings, footnotes, cross-references etc.  In fact this is probably the
* major shortcoming of what I do here, because we have very specific
* requirements regarding annotation when reversification is applied, and I
* should probably check that they have been satisfied.  At present I've
* bottled out, because this looks difficult.
*
* @author ARA 'Jamie' Jamieson
*/

object _UNUSED_PA_ContentValidator: ObjectInterface
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
   * Carries out content checks.
   *
   * @param dataCollectionNew
   * @param dataCollectionOld
   */

  fun process (dataCollectionNew: X_DataCollection, dataCollectionOld: X_DataCollection)
  {
    val reversificationRowsForAllBooks = PA_ReversificationDataLoader.getSelectedRows()
    loadContent(m_BookAnatomiesOld, dataCollectionOld, "original")
    loadContent(m_BookAnatomiesNew, dataCollectionNew, "revised")
    doChecksPerBook(dataCollectionNew, dataCollectionOld, reversificationRowsForAllBooks)
    confirmThatEverythingHasBeenChecked(dataCollectionNew)
  } // fun





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Checks to see if there are any verses we haven't checked.*/

  private fun confirmThatEverythingHasBeenChecked (dataCollectionNew: X_DataCollection)
  {
    dataCollectionNew.getBookNumbers().filter { null != dataCollectionNew.getRootNode(it) }.forEach { bookNo ->
      m_BookAnatomiesOld[bookNo]!!.m_VerseRefKeysToNodeIndex.keys.forEach { refKey -> error(refKey, "Verse in original text not picked up by checking.") }
      m_BookAnatomiesNew[bookNo]!!.m_VerseRefKeysToNodeIndex.keys.forEach { refKey -> error(refKey, "Verse in revised text not picked up by checking.") }
    }
  }


  /*****************************************************************************/
  private fun doChecksPerBook (dataCollectionNew: X_DataCollection, dataCollectionOld: X_DataCollection, reversificationRowsForAllBooks: List<ReversificationDataRow>)
  {
    Rpt.reportWithContinuation(level = 1, "Checking content ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollectionNew.getBookNumbers().forEach { bookNo ->
            asyncable {
               if (null != dataCollectionNew.getRootNode(bookNo))
               {
                 Rpt.reportBookAsContinuation(BibleBookNamesUsx.numberToAbbreviatedName(bookNo))
                 PA_ContentValidatorPerBook(bookAnatomiesNew = m_BookAnatomiesNew,
                                            bookAnatomiesOld = m_BookAnatomiesOld,
                                            m_FileProtocolNew = dataCollectionNew.getFileProtocol(),
                                            m_FileProtocolOld = dataCollectionOld.getFileProtocol(),
                                            reversificationRowsForAllBooks).processBookNo(bookNo)
               } // if
            } // asyncable
          } // forEach
        } // run
      } // parallel
    } // reportWithContinuation
  } // fun


  /*****************************************************************************/
  /* This function is called twice, and between those two calls arranges for the
     entire content of both the original and the revised text to be analysed and
     recorded.

     Strictly for anything other than conversion-time reversification, it is not
     necessary to operate this way, because we only ever compare book n with book
     n, and therefore would need to analyse only a single book at a time.

     Even with conversion-time reversification, there would be no need to analyse
     the whole of the _revised_ text at one go.  We work on only one revised book
     at a time, so would need the analysis only of the current book.  (We _would_
     need the complete analysis of the original text, however, because in some
     places a revised book takes it content from more than one book in the
     original.)

     However, I have opted to go for a full analysis of both texts, rather than
     a full analysis of one and an analysis of the current book in the other,
     because it means I can use the same code for both. */

  private fun loadContent (anatomies: ConcurrentHashMap<Int, BookAnatomy>, dataCollection: X_DataCollection, prompt: String)
  {
    Rpt.reportWithContinuation(level = 1, "Loading $prompt text prior to validating content ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getBookNumbers().forEach { bookNo ->
            asyncable {
               if (null != dataCollection.getRootNode(bookNo))
               {
                 Rpt.reportBookAsContinuation(BibleBookNamesUsx.numberToAbbreviatedName(bookNo))
                 PA_ContentValidator_ContentLoaderPerBook().process(anatomies, dataCollection, bookNo)
               } // if
            } // asyncable
          } // forEach
        } // run
      } // parallel
    } // reportWithContinuation
  } // fun


  /****************************************************************************/
  private val m_BookAnatomiesNew = ConcurrentHashMap<Int, BookAnatomy>()
  private val m_BookAnatomiesOld = ConcurrentHashMap<Int, BookAnatomy>()
} // object





/******************************************************************************/
private fun error (refKey: RefKey, message: String) = Logger.error(refKey, message)
private fun warning (refKey: RefKey, message: String) = Logger.warning(refKey, message)





/******************************************************************************/
/* Used to hold details of verses etc ...

   - m_AllNodes holds all nodes from a given document.  Or at least all bar
     notes and their descendants.  Strictly there's no need to remove notes,
     because the processing would simply ignore them if they were present.
     However, they can be quite chunky, and ignoring them would entail a fair
     bit of processing (albeit probably no more than excising them in the first
     place).  In fact, excising them is probably unnecessary anyway, because
     recent changes mean we remove most notes at the start of processing and
     reinstate them later.

   - m_VerseRefKeysToNodeIndex relates the refKeys for the verses of interest
     to the location within m_AllNodes where those verse tags (sid and eid)
     appear.  I don't include, in the sid index, pointers to any verses which
     were created ex nihilo as a result of the processing, on the grounds that
     there is nothing in the original text to compare them against, and
     therefore they mustn't be included in comparisons.

   - m_chapterSidToPsalmTitle relates the refkey sids of chapters which contain
     canonical headers to the actual header node.  Whether this will prove to
     be useful remains to be seen, because as I write this, I'm not sure how
     to validate texts where the original lacks canonical headers is converted
     to one which has them. */

private class BookAnatomy
{
  lateinit var m_AllNodes: List<Node>
  var m_VerseRefKeysToNodeIndex: MutableMap<RefKey, MutableList<SidEidPair>> = mutableMapOf()
  var m_ChapterSidToPsalmTitle: MutableMap<RefKey, Node> = mutableMapOf()
  val m_VersesRemovedFromTables = mutableSetOf<RefKey>()
}

private data class SidEidPair (val sidIx: Int, var eidIx: Int)
{
  var refKeysInTable: List<RefKey>? = null // This will be set only in the data for the _revised_ text, and gives the list of refKeys in the _original_ text which make up the table.
}





/******************************************************************************/
/**
* Obtains and records details of the content of a single book.
*/

private class PA_ContentValidator_ContentLoaderPerBook ()
{
  /****************************************************************************/
  fun process (anatomies: ConcurrentHashMap<Int, BookAnatomy>, dataCollection: X_DataCollection, bookNo: Int)
  {
    anatomies[bookNo] = makeBookAnatomy(dataCollection.getRootNode(bookNo)!!, dataCollection.getFileProtocol())
  }


  /****************************************************************************/
  /* Creates a single BookAnatomy data structure for a single book. */

  private fun makeBookAnatomy (rootNode: Node, fileProtocol: X_FileProtocol): BookAnatomy
  {
    /**************************************************************************/
    //Dbg.outputDom(rootNode.ownerDocument)



    /**************************************************************************/
    /* At the end of this block, res.m_AllNodes contains an ordered list of
       all nodes in the document, except for notes nodes.  Strictly there's no
       harm in retaining the notes nodes, I guess, but they tend to have a lot
       of content, and it is of no interest when checking. */

    val res = BookAnatomy()
    val ignoreNodes = IdentityHashMap<Node, Byte>()
    val allNodes = rootNode.getAllNodesBelow()
    allNodes.filter { "note" == Dom.getNodeName(it) }.forEach { node -> ignoreNodes[node] = 0; node.getAllNodesBelow().forEach { ignoreNodes[it] = 0 } }
    res.m_AllNodes = allNodes.filter { it !in ignoreNodes }



    /**************************************************************************/
    fun processCanonicalTitle (node: Node)
    {
      Dbg.d(node.textContent)
      Dbg.d(node.ownerDocument)
      val sidRefKey = fileProtocol.readRef(Dom.getAttribute(Dom.getAncestorNamed(node, fileProtocol.tagName_chapter())!!, fileProtocol.attrName_chapterSid())!!).toRefKey()
      res.m_ChapterSidToPsalmTitle[sidRefKey] = node
    }



    /**************************************************************************/
    fun processVerse (index: Int, node: Node)
    {
      /************************************************************************/
      /* We do a lot of tests on verse sids, and exclude some of them.  I don't
         bother with this for eids, because things are driven from the sids.
         This may mean the eid collection contains more elements than the sid
         collection, and I may curse myself for that simply because it may turn
         out to confuse the human reader, but from a processing point of view
         it saves a little programming effort. */

      if (fileProtocol.attrName_verseEid() in node)
      {
        if (null != m_ActiveSidEidPair)
          m_ActiveSidEidPair!!.eidIx = index
        return
      }



      /************************************************************************/
      /* Must be a sid, then.  We can ignore it if it's an empty node created
         by the processing here, because there will be no corresponding empty
         verse in the original against which to compare it.

         We also need some fiddly processing if the verse forms part of an
         elision, because we will have expanded the elision out, and need to
         ignore the empty verses we've created, while selecting the elided
         verse in the original for comparison purposes.

         Note that not all of these tests make sense when we are processing the
         original text, but there's no _harm_ in applying them, and it means we
         can avoid special-case processing. */

      if (NodeMarker.hasEmptyVerseType(node)) // Empty verses have been created in the course of processing, and therefore don't exist in the original for us to test against.
      {
        m_ActiveSidEidPair = null
        return
      }

      var refKey = fileProtocol.readRefCollection(node[fileProtocol.attrName_verseSid()]!!).getLastAsRefKey()
      refKey = Ref.clearS(refKey)

      m_ActiveSidEidPair = SidEidPair(index, -1)

      if (refKey in res.m_VerseRefKeysToNodeIndex)
        res.m_VerseRefKeysToNodeIndex[refKey]!!.add(m_ActiveSidEidPair!!)
      else
        res.m_VerseRefKeysToNodeIndex[refKey] = mutableListOf(m_ActiveSidEidPair!!)

      if (NodeMarker.hasTableOwnerType(node))
        m_ActiveSidEidPair!!.refKeysInTable = NodeMarker.getTableRefKeys(node)!!.split(",").map { it.toLong() }



      /************************************************************************/
      /* In the revised text, table owners have a list of contained verse
         refKeys.  We need to record these so that the processing knows they
         are implicitly present. */

      val containedTableVerses = NodeMarker.getTableRefKeys(node) ?: return
      res.m_VersesRemovedFromTables.addAll(containedTableVerses.split(",").map { it.toLong() })
    }



    /**************************************************************************/
    /* This block does two things.  For verses, it creates a map from the
       refKey to the index into the m_AllNodes array (and allows for elisions,
       taking the end reference, although I'm not now 100% sure whether we
       should expect elisions here.  In fact, the map relates the bcv refKey to
       a list of the indices for all nodes having that bcv reference key.  This
       means the subverses (and their owning verse, if it exists) are grouped
       together.

       And for canonical titles, it creates a map from the refKey of the owning
       chapter to the canonical title node. */

    for (i in res.m_AllNodes.indices)
    {
      val node = res.m_AllNodes[i]
      if (fileProtocol.tagName_verse() == Dom.getNodeName(node))
        processVerse(i, node)
      else if (fileProtocol.isCanonicalTitleNode(node))
        processCanonicalTitle(node)
    } // for



    /**************************************************************************/
    return res
  }


  /****************************************************************************/
  private var m_ActiveSidEidPair: SidEidPair? = null
}





/******************************************************************************/
/**
* A validator which checks the content of a single book.  It is convenient to
* have a separate instance of this for each book so that
*/

private class PA_ContentValidatorPerBook (val bookAnatomiesNew: ConcurrentHashMap<Int, BookAnatomy>,
                                          val bookAnatomiesOld: ConcurrentHashMap<Int, BookAnatomy>,
                                          val m_FileProtocolNew: X_FileProtocol,
                                          val m_FileProtocolOld: X_FileProtocol,
                                          val m_ReversificationRowsForAllBooks: List<ReversificationDataRow>)
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
  * Carries out the checks on a single book in the revised text.
  *
  * @param bookNo What it says on the tin.
*/
  fun processBookNo (bookNo: Int)
  {
    m_BookAnatomiesOld = bookAnatomiesOld
    m_BookAnatomiesNew = bookAnatomiesNew
    checkNonReversified(bookNo)
  }


  /****************************************************************************/
  private lateinit var m_BookAnatomiesNew: ConcurrentHashMap<Int, BookAnatomy>
  private lateinit var m_BookAnatomiesOld: ConcurrentHashMap<Int, BookAnatomy>




  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                Verses not affected by reversification                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Checks verses carried through from the source without being affected by
     reversification.  This needs to be called at the end of processing, at
     which point the only verses remaining in the data structure will be the
     ones not taken care of by reversification.

     This method is probably more or less ok even when doing conversion-time
     reversification, so long as you check the verses whose references /
     locations have changed _first_, and delete their details from the book
     anatomy structures. */

  private fun checkNonReversified (bookNo: Int)
  {
    val bookAnatomyOld = m_BookAnatomiesOld[bookNo]!!
    val bookAnatomyNew = m_BookAnatomiesNew[bookNo]!!
    checkNonReversifiedMismatchedVerses(bookAnatomyNew = bookAnatomyNew, bookAnatomyOld = bookAnatomyOld)
    checkNonReversifiedContent(bookAnatomyNew = bookAnatomyNew, bookAnatomyOld = bookAnatomyOld)
  }


  /****************************************************************************/
  /* Compares the actual content of verses. */

  private fun checkNonReversifiedContent (bookAnatomyNew: BookAnatomy, bookAnatomyOld: BookAnatomy)
  {
    /**************************************************************************/
    val oldDelenda = mutableListOf<RefKey>()
    val newDelenda = mutableListOf<RefKey>()



    /**************************************************************************/
    /* Called where we're dealing with a table.  In this case, tableRefKeys,
       which has been taken from the data for the _revised_ text, gives the
       refKeys of the verses in the _original_ text which have been incorporated
       into the table.  The function returns the combined content of these
       verses. */

    fun getContentForTable (bookAnatomyOld: BookAnatomy, tableRefKeys: List<RefKey>, fileProtocol: X_FileProtocol): String
    {
      val sidEidPairList = tableRefKeys.map { bookAnatomyOld.m_VerseRefKeysToNodeIndex[it]!! }.flatten()
      return getContent(bookAnatomyOld.m_AllNodes, sidEidPairList, fileProtocol)
    }



    /**************************************************************************/
    /* We should now have matched entries in the two anatomies, so to a
       reasonable approximation it's purely a case of comparing the one
       directly with the other.  The complication comes with elided tables
       and perhaps with canonical titles. */

    bookAnatomyNew.m_VerseRefKeysToNodeIndex.keys.forEach { refKeyNew ->
      val tableRefKeys = bookAnatomyNew.m_VerseRefKeysToNodeIndex[refKeyNew]!!.first().refKeysInTable
      val newContent = getContent(bookAnatomyNew, m_FileProtocolNew, refKeyNew)

      var refKeyOld = -1L

      val oldContent = if (null == tableRefKeys) // Not a table owner.
      {
        refKeyOld = getOldEquivalentOfRefKey(refKeyNew)
        getContent(bookAnatomyOld, m_FileProtocolOld, refKeyOld)
      }
      else // A table owner.
        getContentForTable(bookAnatomyOld, tableRefKeys, m_FileProtocolOld)

      if (canonicalise(newContent) != canonicalise(oldContent))
      {
        val inTable = if (null == tableRefKeys) "" else " (in Table)"
        val message = "Verse mismatch:$inTable<nl>  Original = '$oldContent'<nl>     Final = '$newContent'<nl>"
        error(refKeyNew, message)
      }



      // Remove the entries for the things we've processed, so that at
      // the end of processing, we can see if anything has been missed.

      newDelenda.add(refKeyNew)
      if (null == tableRefKeys)
        oldDelenda.add(refKeyOld)
      else
        oldDelenda.addAll(tableRefKeys)
    } // forEach



    /**************************************************************************/
    newDelenda.forEach { bookAnatomyNew.m_VerseRefKeysToNodeIndex.remove(it) }
    oldDelenda.forEach { bookAnatomyOld.m_VerseRefKeysToNodeIndex.remove(it) }
  } // fun


  /****************************************************************************/
  /* Checks for verses in only one of the two texts. */

  private fun checkNonReversifiedMismatchedVerses (bookAnatomyNew: BookAnatomy, bookAnatomyOld: BookAnatomy)
  {
    val inOldOnly = bookAnatomyOld.m_VerseRefKeysToNodeIndex.keys - bookAnatomyNew.m_VerseRefKeysToNodeIndex.keys - bookAnatomyNew.m_VersesRemovedFromTables
    val inNewOnly = bookAnatomyNew.m_VerseRefKeysToNodeIndex.keys - bookAnatomyOld.m_VerseRefKeysToNodeIndex.keys

    inOldOnly.forEach { error (it, "Verse in original text (as verse or as subverses), but not in revised text." )}
    inNewOnly.forEach { error (it, "Verse in revised text, but not in original text (neither as verse nor as subverses)." )}

    inOldOnly.forEach { bookAnatomyNew.m_VerseRefKeysToNodeIndex.remove(it) }
    inNewOnly.forEach { bookAnatomyOld.m_VerseRefKeysToNodeIndex.remove(it) }
  }


  /****************************************************************************/
  /* Converts verse content to canonicalised form, for example replacing
     with spaces the characters we typically use to flag empty verses. */

  private fun canonicalise (s: String): String
  {
     return s
      .trim()
      .replace("&#160;".toRegex(), " ") // XML non-breaking space.
      .replace("&#x2013;".toRegex(), " ")  // En-dash.
      .replace("&nbsp;", " ")
      .replace("\u00a0", " ") // Unicode non-breaking space.
      .replace("\\s+".toRegex(), " ")
  }


  /****************************************************************************/
  /* Gets canonical content. */

  private fun getContent (bookAnatomy: BookAnatomy, fileProtocol: X_FileProtocol, refKey: RefKey): String
  {
    return getContent(bookAnatomy.m_AllNodes, bookAnatomy.m_VerseRefKeysToNodeIndex[refKey]!!, fileProtocol)
  }


  /****************************************************************************/
  private fun getContent (allNodes: List<Node>, verseIndexes: List<SidEidPair>, fileProtocol: X_FileProtocol): String
  {
    val res = StringBuilder(1000)
      verseIndexes.forEach {
        for (i in it.sidIx .. it.eidIx) // Normally there's no point in including sid and eid, but I want to press this into service also for canonical titles, where sid and eid are actually just the first and last child of para:d.
        {
          val node = allNodes[i]
          if ("#text" == Dom.getNodeName(node) &&                                       // The content is limited to text nodes.
            !fileProtocol.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(node))    // And only those which are canonical.
          {
            res.append(" ")
            res.append(node.textContent)
          } // if
        } // for
      } // forEach

    return res.toString()
  }


  /****************************************************************************/
  /* At present, this merely returns its argument, reflecting the fact that so
     long as we are not dealing with conversion-time reversification, we always
     compare verse n against verse n.  We will need something more sophisticated
     if we ever need to support conversion-time reversification. */

  fun getOldEquivalentOfRefKey (refKey: RefKey): RefKey
  {
    return refKey
  }
}
